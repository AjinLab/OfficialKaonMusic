package com.kaon.music.core.data.sync

import com.kaon.music.core.data.db.dao.FavoriteDao
import com.kaon.music.core.data.db.dao.PlayEventDao
import com.kaon.music.core.data.db.dao.TrackDao
import com.kaon.music.core.data.db.entity.FavoriteTrackEntity
import com.kaon.music.core.data.db.entity.PlayEventEntity
import com.kaon.music.core.data.db.entity.TrackEntity
import com.kaon.music.core.data.model.PlayEvent
import com.kaon.music.core.data.repository.TrackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncEngineTest {

    private lateinit var fakeTrackDao: FakeTrackDao
    private lateinit var fakeFavoriteDao: FakeFavoriteDao
    private lateinit var fakePlayEventDao: FakePlayEventDao

    @Before
    fun setup() {
        fakeTrackDao = FakeTrackDao()
        fakeFavoriteDao = FakeFavoriteDao(fakeTrackDao)
        fakePlayEventDao = FakePlayEventDao(fakeTrackDao)
    }

    /**
     * Test 1: Two tracks with identical duration and file size cannot be silently merged.
     */
    @Test
    fun `two tracks with identical duration and file size cannot be silently merged`() = runTest {
        val scanner = object : MediaStoreScannerFake(
            listOf(
                createScanItem(mediaStoreId = 101, title = "Track One", artist = "Artist A", durationMs = 180000, sizeBytes = 5000000),
                createScanItem(mediaStoreId = 102, title = "Track Two", artist = "Artist B", durationMs = 180000, sizeBytes = 5000000)
            )
        ) {}

        val engine = SyncEngine(scanner, fakeTrackDao)
        val result = engine.synchronize()

        assertEquals(2, result.totalDiscovered)
        assertEquals(2, result.added)
        assertEquals(0, result.reLinked)
        assertEquals(2, fakeTrackDao.storedTracks.size)

        val track1 = fakeTrackDao.storedTracks.find { it.mediaStoreId == 101L }
        val track2 = fakeTrackDao.storedTracks.find { it.mediaStoreId == 102L }

        assertNotNull(track1)
        assertNotNull(track2)
        assertTrue(track1!!.trackId != track2!!.trackId)
        assertEquals("Track One", track1.title)
        assertEquals("Track Two", track2.title)
    }

    /**
     * Test 2: File returning after being missing re-links to original track.
     */
    @Test
    fun `file returning after being missing re-links to original track`() = runTest {
        // Initial scan
        val scannerPass1 = object : MediaStoreScannerFake(
            listOf(createScanItem(mediaStoreId = 101, title = "Song A", artist = "Artist A", durationMs = 210000, sizeBytes = 6000000))
        ) {}
        val engine1 = SyncEngine(scannerPass1, fakeTrackDao)
        engine1.synchronize()

        val originalTrackId = fakeTrackDao.storedTracks.first().trackId

        // Pass 2: File vanishes
        val scannerPass2 = object : MediaStoreScannerFake(emptyList()) {}
        val engine2 = SyncEngine(scannerPass2, fakeTrackDao)
        val result2 = engine2.synchronize()

        assertEquals(1, result2.markedMissing)
        assertTrue(fakeTrackDao.storedTracks.first().isMissing)

        // Pass 3: File reappears with new MediaStore ID (e.g. MediaStore rebuilt)
        val scannerPass3 = object : MediaStoreScannerFake(
            listOf(createScanItem(mediaStoreId = 555, title = "Song A", artist = "Artist A", durationMs = 210000, sizeBytes = 6000000))
        ) {}
        val engine3 = SyncEngine(scannerPass3, fakeTrackDao)
        val result3 = engine3.synchronize()

        assertEquals(1, result3.reLinked)
        assertEquals(0, result3.added)
        assertEquals(1, fakeTrackDao.storedTracks.size)

        val reLinkedTrack = fakeTrackDao.storedTracks.first()
        assertEquals(originalTrackId, reLinkedTrack.trackId)
        assertEquals(555L, reLinkedTrack.mediaStoreId)
        assertFalse(reLinkedTrack.isMissing)
    }

    /**
     * Test 3: Genuinely new file does not accidentally attach to an old missing track.
     */
    @Test
    fun `genuinely new file does not accidentally attach to an old missing track`() = runTest {
        // Track 1 went missing
        fakeTrackDao.storedTracks.add(
            TrackEntity(
                trackId = 1L,
                mediaStoreId = 101L,
                title = "Old Song",
                artist = "Old Artist",
                artistId = 1L,
                album = "Old Album",
                albumId = 10L,
                trackNumber = 1,
                discNumber = 1,
                year = 2020,
                durationMs = 180000L,
                sizeBytes = 4000000L,
                dateModified = 1000L,
                relativePath = "Music/Old/",
                titleNormalized = "old song",
                artistNormalized = "old artist",
                albumNormalized = "old album",
                isMissing = true,
                lastSeenTimestamp = System.currentTimeMillis()
            )
        )

        // New file added with different title
        val scanner = object : MediaStoreScannerFake(
            listOf(
                createScanItem(
                    mediaStoreId = 202,
                    title = "Completely Different Song",
                    artist = "Different Artist",
                    durationMs = 180000,
                    sizeBytes = 4000000,
                    relativePath = "Music/New/"
                )
            )
        ) {}

        val engine = SyncEngine(scanner, fakeTrackDao)
        val result = engine.synchronize()

        assertEquals(1, result.added)
        assertEquals(0, result.reLinked)
        assertEquals(2, fakeTrackDao.storedTracks.size)

        val missing = fakeTrackDao.storedTracks.find { it.trackId == 1L }
        val newTrack = fakeTrackDao.storedTracks.find { it.trackId != 1L }

        assertNotNull(missing)
        assertNotNull(newTrack)
        assertTrue(missing!!.isMissing)
        assertFalse(newTrack!!.isMissing)
    }

    /**
     * Test 4: Repeated syncs are idempotent.
     */
    @Test
    fun `repeated syncs are idempotent`() = runTest {
        val items = listOf(
            createScanItem(mediaStoreId = 1, title = "Song 1", artist = "Artist 1", durationMs = 100000, sizeBytes = 2000000),
            createScanItem(mediaStoreId = 2, title = "Song 2", artist = "Artist 2", durationMs = 150000, sizeBytes = 3000000)
        )

        val scanner = object : MediaStoreScannerFake(items) {}
        val engine = SyncEngine(scanner, fakeTrackDao)

        val result1 = engine.synchronize()
        assertEquals(2, result1.added)

        val result2 = engine.synchronize()
        assertEquals(0, result2.added)
        assertEquals(0, result2.reLinked)
        assertEquals(0, result2.markedMissing)
        assertEquals(2, fakeTrackDao.storedTracks.size)
    }

    /**
     * Test 5: MediaStore ID changes while actual track remains the same.
     */
    @Test
    fun `mediaStore ID changes while actual track remains the same`() = runTest {
        val scanner1 = object : MediaStoreScannerFake(
            listOf(createScanItem(mediaStoreId = 10, title = "Song", artist = "Artist", durationMs = 120000, sizeBytes = 2500000))
        ) {}
        val engine1 = SyncEngine(scanner1, fakeTrackDao)
        engine1.synchronize()

        val originalTrackId = fakeTrackDao.storedTracks.first().trackId

        // ID changes to 20
        val scanner2 = object : MediaStoreScannerFake(
            listOf(createScanItem(mediaStoreId = 20, title = "Song", artist = "Artist", durationMs = 120000, sizeBytes = 2500000))
        ) {}
        val engine2 = SyncEngine(scanner2, fakeTrackDao)
        val result2 = engine2.synchronize()

        assertEquals(1, result2.reLinked)
        assertEquals(1, fakeTrackDao.storedTracks.size)
        assertEquals(originalTrackId, fakeTrackDao.storedTracks.first().trackId)
        assertEquals(20L, fakeTrackDao.storedTracks.first().mediaStoreId)
    }

    /**
     * Test 6 (Milestone 2 Failure-Mode Matrix #4):
     * Permission revoked while running: No reconcile, no deletions, no tracks marked missing.
     */
    @Test
    fun `sync safety guard aborts and preserves database when permission is revoked`() = runTest {
        fakeTrackDao.storedTracks.add(
            TrackEntity(
                trackId = 1L,
                mediaStoreId = 1001L,
                title = "Existing Track",
                artist = "Artist",
                artistId = 1L,
                album = "Album",
                albumId = 1L,
                trackNumber = 1,
                discNumber = 1,
                year = 2024,
                durationMs = 200000L,
                sizeBytes = 4000000L,
                dateModified = 1000L,
                relativePath = "Music/",
                titleNormalized = "existing track",
                artistNormalized = "artist",
                albumNormalized = "album",
                isMissing = false,
                lastSeenTimestamp = 1000L
            )
        )

        val scanner = object : MediaStoreScannerFake(items = emptyList(), permissionGranted = false) {}
        val engine = SyncEngine(scanner, fakeTrackDao)
        val result = engine.synchronize()

        assertEquals(0, result.totalDiscovered)
        assertEquals(0, result.added)
        assertEquals(0, result.markedMissing)
        assertEquals(1, fakeTrackDao.storedTracks.size)
        assertFalse(fakeTrackDao.storedTracks[0].isMissing)
    }

    /**
     * Test 7 (Milestone 3 Matrix #19 & M3-D4):
     * File renamed/moved on device + sync: Re-links to existing track row without creating duplicate.
     */
    @Test
    fun `file renamed or moved on device re-links via Tier 2 metadata match`() = runTest {
        // Initial track state
        fakeTrackDao.storedTracks.add(
            TrackEntity(
                trackId = 42L,
                mediaStoreId = 1001L,
                title = "Bohemian Rhapsody",
                artist = "Queen",
                artistId = 5L,
                album = "A Night at the Opera",
                albumId = 10L,
                trackNumber = 4,
                discNumber = 1,
                year = 1975,
                durationMs = 354000L,
                sizeBytes = 8500000L,
                dateModified = 1000L,
                relativePath = "Music/OldFolder/Track4.mp3",
                titleNormalized = "bohemian rhapsody",
                artistNormalized = "queen",
                albumNormalized = "a night at the opera",
                isMissing = true,
                lastSeenTimestamp = 1000L
            )
        )

        // File moved to new folder with new MediaStoreId
        val movedItem = MediaStoreAudioItem(
            mediaStoreId = 2002L,
            title = "Bohemian Rhapsody",
            artist = "Queen",
            artistId = 5L,
            album = "A Night at the Opera",
            albumId = 10L,
            trackNumber = 4,
            discNumber = 1,
            year = 1975,
            durationMs = 354000L,
            sizeBytes = 8500000L,
            dateModified = 2000L,
            relativePath = "Music/Queen/Bohemian Rhapsody.mp3"
        )

        val scanner = object : MediaStoreScannerFake(listOf(movedItem)) {}
        val engine = SyncEngine(scanner, fakeTrackDao)
        val result = engine.synchronize()

        assertEquals(1, result.reLinked)
        assertEquals(0, result.added)
        assertEquals(1, fakeTrackDao.storedTracks.size)

        val reLinked = fakeTrackDao.storedTracks.first()
        assertEquals(42L, reLinked.trackId)
        assertEquals(2002L, reLinked.mediaStoreId)
        assertFalse(reLinked.isMissing)
    }

    /**
     * Test 8 (Milestone 3 Retention Window Purge):
     * Orphaned tracks missing past 30 days are cleanly purged from DB.
     */
    @Test
    fun `orphaned tracks older than 30 days are purged`() = runTest {
        val now = System.currentTimeMillis()
        val fortyDaysAgo = now - (40L * 24 * 60 * 60 * 1000L)
        val fiveDaysAgo = now - (5L * 24 * 60 * 60 * 1000L)

        // Old missing track (> 30 days)
        fakeTrackDao.storedTracks.add(
            TrackEntity(
                trackId = 1L,
                mediaStoreId = 101L,
                title = "Ancient Missing Song",
                artist = "Artist",
                album = "Album",
                albumId = 1L,
                durationMs = 180000L,
                sizeBytes = 3000000L,
                dateModified = 1000L,
                relativePath = "Music/",
                titleNormalized = "ancient missing song",
                artistNormalized = "artist",
                albumNormalized = "album",
                isMissing = true,
                lastSeenTimestamp = fortyDaysAgo
            )
        )

        // Recently missing track (< 30 days)
        fakeTrackDao.storedTracks.add(
            TrackEntity(
                trackId = 2L,
                mediaStoreId = 102L,
                title = "Recent Missing Song",
                artist = "Artist",
                album = "Album",
                albumId = 1L,
                durationMs = 180000L,
                sizeBytes = 3000000L,
                dateModified = 1000L,
                relativePath = "Music/",
                titleNormalized = "recent missing song",
                artistNormalized = "artist",
                albumNormalized = "album",
                isMissing = true,
                lastSeenTimestamp = fiveDaysAgo
            )
        )

        val scanner = object : MediaStoreScannerFake(emptyList()) {}
        val engine = SyncEngine(scanner, fakeTrackDao)
        val result = engine.synchronize(orphanRetentionDays = 30)

        assertEquals(1, result.purgedOrphans)
        assertEquals(1, fakeTrackDao.storedTracks.size)
        assertEquals(2L, fakeTrackDao.storedTracks.first().trackId)
    }

    /**
     * Test 9 (Ambiguity Abort Rule):
     * If multiple missing candidates match the tier criteria, re-linking is aborted to prevent cross-linking.
     */
    @Test
    fun `ambiguity in candidate matching aborts re-link and inserts clean new track`() = runTest {
        // Two missing tracks with identical metadata (e.g. duplicate files in different folders)
        fakeTrackDao.storedTracks.add(
            TrackEntity(
                trackId = 1L,
                mediaStoreId = 101L,
                title = "Duplicate Song",
                artist = "Artist",
                album = "Album",
                albumId = 1L,
                durationMs = 180000L,
                sizeBytes = 3000000L,
                dateModified = 1000L,
                relativePath = "Music/FolderA/Song.mp3",
                titleNormalized = "duplicate song",
                artistNormalized = "artist",
                albumNormalized = "album",
                isMissing = true,
                lastSeenTimestamp = System.currentTimeMillis()
            )
        )
        fakeTrackDao.storedTracks.add(
            TrackEntity(
                trackId = 2L,
                mediaStoreId = 102L,
                title = "Duplicate Song",
                artist = "Artist",
                album = "Album",
                albumId = 1L,
                durationMs = 180000L,
                sizeBytes = 3000000L,
                dateModified = 1000L,
                relativePath = "Music/FolderB/Song.mp3",
                titleNormalized = "duplicate song",
                artistNormalized = "artist",
                albumNormalized = "album",
                isMissing = true,
                lastSeenTimestamp = System.currentTimeMillis()
            )
        )

        // New scanned item matches Tier 2 for BOTH missing candidates
        val scannedItem = MediaStoreAudioItem(
            mediaStoreId = 555L,
            title = "Duplicate Song",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            durationMs = 180000L,
            sizeBytes = 3000000L,
            dateModified = 2000L,
            relativePath = "Music/FolderC/Song.mp3"
        )

        val scanner = object : MediaStoreScannerFake(listOf(scannedItem)) {}
        val engine = SyncEngine(scanner, fakeTrackDao)
        val result = engine.synchronize()

        // Re-link should be ABORTED due to ambiguity; new track inserted
        assertEquals(0, result.reLinked)
        assertEquals(1, result.added)
        assertEquals(3, fakeTrackDao.storedTracks.size)

        val newTrack = fakeTrackDao.storedTracks.find { it.mediaStoreId == 555L }
        assertNotNull(newTrack)
        assertTrue(newTrack!!.trackId != 1L && newTrack.trackId != 2L)
    }

    /**
     * Test 10 (Tier 3 Removal Verification):
     * Tracks with identical title and size but different artist/album are NOT re-linked.
     */
    @Test
    fun `same title and size but different artist is never re-linked without tier 3`() = runTest {
        // Missing track with generic title
        fakeTrackDao.storedTracks.add(
            TrackEntity(
                trackId = 10L,
                mediaStoreId = 101L,
                title = "Intro",
                artist = "Artist Alpha",
                album = "Album Alpha",
                albumId = 1L,
                durationMs = 60000L,
                sizeBytes = 1000000L,
                dateModified = 1000L,
                relativePath = "Music/Alpha/Intro.mp3",
                titleNormalized = "intro",
                artistNormalized = "artist alpha",
                albumNormalized = "album alpha",
                isMissing = true,
                lastSeenTimestamp = System.currentTimeMillis()
            )
        )

        // New scanned track from different artist with same title, duration, size
        val scannedItem = MediaStoreAudioItem(
            mediaStoreId = 202L,
            title = "Intro",
            artist = "Artist Beta",
            album = "Album Beta",
            albumId = 2L,
            durationMs = 60000L,
            sizeBytes = 1000000L,
            dateModified = 2000L,
            relativePath = "Music/Beta/Intro.mp3"
        )

        val scanner = object : MediaStoreScannerFake(listOf(scannedItem)) {}
        val engine = SyncEngine(scanner, fakeTrackDao)
        val result = engine.synchronize()

        // Re-link must NOT happen across artists; must be inserted as a new track
        assertEquals(0, result.reLinked)
        assertEquals(1, result.added)
        assertEquals(2, fakeTrackDao.storedTracks.size)

        val alphaTrack = fakeTrackDao.storedTracks.find { it.trackId == 10L }
        val betaTrack = fakeTrackDao.storedTracks.find { it.trackId != 10L }

        assertNotNull(alphaTrack)
        assertNotNull(betaTrack)
        assertTrue(alphaTrack!!.isMissing)
        assertEquals("Artist Beta", betaTrack!!.artist)
        assertEquals(202L, betaTrack.mediaStoreId)
    }

    /**
     * Test 11 (Milestone 4 Failure-Mode Matrix #25 - Anchor Requirement):
     * Favorite a track -> rename/move file -> sync -> favorite survives re-link with same trackId.
     */
    @Test
    fun `favorite survives file move or rename and re-link with stable trackId`() = runTest {
        val originalTrackId = 77L
        val originalMediaStoreId = 1001L

        // Initial track state
        fakeTrackDao.storedTracks.add(
            TrackEntity(
                trackId = originalTrackId,
                mediaStoreId = originalMediaStoreId,
                title = "Starman",
                artist = "David Bowie",
                artistId = 10L,
                album = "Ziggy Stardust",
                albumId = 20L,
                trackNumber = 4,
                discNumber = 1,
                year = 1972,
                durationMs = 256000L,
                sizeBytes = 6500000L,
                dateModified = 1000L,
                relativePath = "Music/Bowie/04 - Starman.mp3",
                titleNormalized = "starman",
                artistNormalized = "david bowie",
                albumNormalized = "ziggy stardust",
                isMissing = false,
                lastSeenTimestamp = System.currentTimeMillis()
            )
        )

        // User favorites the track
        val repo = TrackRepository(fakeTrackDao, fakeFavoriteDao, SyncEngine(object : MediaStoreScannerFake(emptyList()) {}, fakeTrackDao))
        repo.toggleFavorite(originalTrackId)

        assertTrue(fakeFavoriteDao.isFavorite(originalTrackId))
        assertEquals(1, repo.observeFavoriteTracks().first().size)
        assertEquals("Starman", repo.observeFavoriteTracks().first().first().title)

        // File is moved/renamed on disk and scanned with a new MediaStore ID
        val movedItem = MediaStoreAudioItem(
            mediaStoreId = 9999L,
            title = "Starman",
            artist = "David Bowie",
            artistId = 10L,
            album = "Ziggy Stardust",
            albumId = 20L,
            trackNumber = 4,
            discNumber = 1,
            year = 1972,
            durationMs = 256000L,
            sizeBytes = 6500000L,
            dateModified = 2000L,
            relativePath = "Music/David Bowie/Ziggy Stardust/Starman.mp3"
        )

        val scanner = object : MediaStoreScannerFake(listOf(movedItem)) {}
        val engine = SyncEngine(scanner, fakeTrackDao)
        val result = engine.synchronize()

        assertEquals(1, result.reLinked)
        assertEquals(0, result.added)

        // Verify: Track re-linked to same Kaon trackId
        val storedTrack = fakeTrackDao.storedTracks.first()
        assertEquals(originalTrackId, storedTrack.trackId)
        assertEquals(9999L, storedTrack.mediaStoreId)
        assertFalse(storedTrack.isMissing)

        // Verify M4 Anchor: Favorite survived re-link with identical trackId
        assertTrue(fakeFavoriteDao.isFavorite(originalTrackId))
        val favoritesList = repo.observeFavoriteTracks().first()
        assertEquals(1, favoritesList.size)
        assertEquals(originalTrackId, favoritesList.first().id)
        assertEquals(9999L, favoritesList.first().mediaStoreId)
        assertTrue(favoritesList.first().isFavorite)
    }

    /**
     * Test 12 (Milestone 4 Failure-Mode Matrix #26):
     * Favorite a track -> file deleted/unplugged -> hidden from Favorites tab query;
     * favorite row retained in DB; file returns -> favorite resurfaces automatically.
     */
    @Test
    fun `favorite survives temporary deletion and resurfaces upon file return`() = runTest {
        val trackId = 88L
        val originalMediaStoreId = 101L

        fakeTrackDao.storedTracks.add(
            TrackEntity(
                trackId = trackId,
                mediaStoreId = originalMediaStoreId,
                title = "Heroes",
                artist = "David Bowie",
                artistId = 10L,
                album = "Heroes",
                albumId = 21L,
                durationMs = 360000L,
                sizeBytes = 8000000L,
                dateModified = 1000L,
                relativePath = "Music/Bowie/Heroes.mp3",
                titleNormalized = "heroes",
                artistNormalized = "david bowie",
                albumNormalized = "heroes",
                isMissing = false,
                lastSeenTimestamp = System.currentTimeMillis()
            )
        )

        val repo = TrackRepository(fakeTrackDao, fakeFavoriteDao, SyncEngine(object : MediaStoreScannerFake(emptyList()) {}, fakeTrackDao))
        repo.toggleFavorite(trackId)
        assertTrue(fakeFavoriteDao.isFavorite(trackId))
        assertEquals(1, repo.observeFavoriteTracks().first().size)

        // Pass 1: SD card unplugged / file vanishes -> sync marks track missing
        val emptyScanner = object : MediaStoreScannerFake(emptyList()) {}
        val engine1 = SyncEngine(emptyScanner, fakeTrackDao)
        val result1 = engine1.synchronize()

        assertEquals(1, result1.markedMissing)
        assertTrue(fakeTrackDao.storedTracks.first().isMissing)

        // Query-level orphan hiding: Track is hidden from active favorites view
        assertEquals(0, repo.observeFavoriteTracks().first().size)
        // User data retention: Favorite record in DB is NOT deleted
        assertTrue(fakeFavoriteDao.isFavorite(trackId))

        // Pass 2: SD card reconnected / file returns -> sync re-links / marks present
        val returningItem = MediaStoreAudioItem(
            mediaStoreId = 5555L,
            title = "Heroes",
            artist = "David Bowie",
            artistId = 10L,
            album = "Heroes",
            albumId = 21L,
            durationMs = 360000L,
            sizeBytes = 8000000L,
            dateModified = 1000L,
            relativePath = "Music/Bowie/Heroes.mp3"
        )
        val returningScanner = object : MediaStoreScannerFake(listOf(returningItem)) {}
        val engine2 = SyncEngine(returningScanner, fakeTrackDao)
        val result2 = engine2.synchronize()

        assertEquals(1, result2.reLinked)
        assertFalse(fakeTrackDao.storedTracks.first().isMissing)

        // Favorite resurfaces automatically with stable trackId
        val restoredFavorites = repo.observeFavoriteTracks().first()
        assertEquals(1, restoredFavorites.size)
        assertEquals(trackId, restoredFavorites.first().id)
        assertTrue(restoredFavorites.first().isFavorite)
    }

    /**
     * Test 13 (M4-D1 Invariant 9 - Single Write Path):
     * Toggle favorite adds and removes cleanly, and observes active favorites in reverse-chronological order.
     */
    @Test
    fun `toggle favorite via repository manages favorites cleanly`() = runTest {
        val track1 = TrackEntity(
            trackId = 1L,
            mediaStoreId = 101L,
            title = "Track 1",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            durationMs = 180000L,
            sizeBytes = 4000000L,
            dateModified = 1000L,
            relativePath = "Music/1.mp3",
            titleNormalized = "track 1",
            artistNormalized = "artist",
            albumNormalized = "album",
            isMissing = false,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        val track2 = TrackEntity(
            trackId = 2L,
            mediaStoreId = 102L,
            title = "Track 2",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            durationMs = 200000L,
            sizeBytes = 4500000L,
            dateModified = 1000L,
            relativePath = "Music/2.mp3",
            titleNormalized = "track 2",
            artistNormalized = "artist",
            albumNormalized = "album",
            isMissing = false,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        fakeTrackDao.storedTracks.addAll(listOf(track1, track2))

        val repo = TrackRepository(fakeTrackDao, fakeFavoriteDao, SyncEngine(object : MediaStoreScannerFake(emptyList()) {}, fakeTrackDao))

        // Initial state: 0 favorites
        assertEquals(0, repo.observeFavoriteTracks().first().size)
        assertFalse(repo.observeIsFavorite(1L).first())

        // Favorite track 1
        repo.toggleFavorite(1L)
        assertTrue(repo.observeIsFavorite(1L).first())
        assertEquals(1, repo.observeFavoriteTracks().first().size)

        // Favorite track 2
        repo.toggleFavorite(2L)
        assertTrue(repo.observeIsFavorite(2L).first())
        val favs = repo.observeFavoriteTracks().first()
        assertEquals(2, favs.size)

        // Unfavorite track 1
        repo.toggleFavorite(1L)
        assertFalse(repo.observeIsFavorite(1L).first())
        val updatedFavs = repo.observeFavoriteTracks().first()
        assertEquals(1, updatedFavs.size)
        assertEquals(2L, updatedFavs.first().id)
    }

    /**
     * Test 14 (Milestone 4 M4-D3 - Recently Played De-duplication):
     * Multiple plays of the same track produce exactly one row with the latest timestamp in reverse-chronological order.
     */
    @Test
    fun `recently played query de-duplicates multiple plays of same track and orders by latest play`() = runTest {
        val trackA = TrackEntity(
            trackId = 1L,
            mediaStoreId = 101L,
            title = "Track A",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            durationMs = 180000L,
            sizeBytes = 4000000L,
            dateModified = 1000L,
            relativePath = "Music/A.mp3",
            titleNormalized = "track a",
            artistNormalized = "artist",
            albumNormalized = "album",
            isMissing = false,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        val trackB = TrackEntity(
            trackId = 2L,
            mediaStoreId = 102L,
            title = "Track B",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            durationMs = 200000L,
            sizeBytes = 4500000L,
            dateModified = 1000L,
            relativePath = "Music/B.mp3",
            titleNormalized = "track b",
            artistNormalized = "artist",
            albumNormalized = "album",
            isMissing = false,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        fakeTrackDao.storedTracks.addAll(listOf(trackA, trackB))

        // Track A played at t = 1000
        fakePlayEventDao.insertEvent(PlayEventEntity(trackId = 1L, eventType = "PLAY", playedAt = 1000L))
        // Track B played at t = 2000
        fakePlayEventDao.insertEvent(PlayEventEntity(trackId = 2L, eventType = "PLAY", playedAt = 2000L))
        // Track A played again at t = 3000
        fakePlayEventDao.insertEvent(PlayEventEntity(trackId = 1L, eventType = "PLAY", playedAt = 3000L))

        val repo = TrackRepository(fakeTrackDao, fakeFavoriteDao, SyncEngine(object : MediaStoreScannerFake(emptyList()) {}, fakeTrackDao), fakePlayEventDao)

        val recentTracks = repo.observeRecentlyPlayedTracks().first()

        // Must contain 2 distinct tracks (Track A is not listed twice)
        assertEquals(2, recentTracks.size)
        // Track A had latest play at 3000L > 2000L, so Track A is first
        assertEquals(1L, recentTracks[0].id)
        assertEquals("Track A", recentTracks[0].title)
        // Track B is second
        assertEquals(2L, recentTracks[1].id)
        assertEquals("Track B", recentTracks[1].title)
    }

    /**
     * Test 15 (Milestone 4 M4-D4 - Most Played Query):
     * Most played tracks query ranks tracks correctly by total PLAY event count.
     */
    @Test
    fun `most played query ranks tracks correctly by total play count`() = runTest {
        val trackA = TrackEntity(
            trackId = 1L,
            mediaStoreId = 101L,
            title = "Track A",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            durationMs = 180000L,
            sizeBytes = 4000000L,
            dateModified = 1000L,
            relativePath = "Music/A.mp3",
            titleNormalized = "track a",
            artistNormalized = "artist",
            albumNormalized = "album",
            isMissing = false,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        val trackB = TrackEntity(
            trackId = 2L,
            mediaStoreId = 102L,
            title = "Track B",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            durationMs = 200000L,
            sizeBytes = 4500000L,
            dateModified = 1000L,
            relativePath = "Music/B.mp3",
            titleNormalized = "track b",
            artistNormalized = "artist",
            albumNormalized = "album",
            isMissing = false,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        fakeTrackDao.storedTracks.addAll(listOf(trackA, trackB))

        // Track A played 5 times
        repeat(5) { i ->
            fakePlayEventDao.insertEvent(PlayEventEntity(trackId = 1L, eventType = "PLAY", playedAt = 1000L + i))
        }
        // Track B played 2 times
        repeat(2) { i ->
            fakePlayEventDao.insertEvent(PlayEventEntity(trackId = 2L, eventType = "PLAY", playedAt = 2000L + i))
        }

        val repo = TrackRepository(fakeTrackDao, fakeFavoriteDao, SyncEngine(object : MediaStoreScannerFake(emptyList()) {}, fakeTrackDao), fakePlayEventDao)

        val mostPlayed = repo.observeMostPlayedTracks().first()
        assertEquals(2, mostPlayed.size)
        assertEquals(1L, mostPlayed[0].id)
        assertEquals(2L, mostPlayed[1].id)
    }

    /**
     * Test 16 (Milestone 4 Failure-Mode Matrix #29):
     * A track with zero play events is absent from both Recently Played and Most Played queries.
     */
    @Test
    fun `zero-play track is absent from recently played and most played queries`() = runTest {
        val playedTrack = TrackEntity(
            trackId = 1L,
            mediaStoreId = 101L,
            title = "Played Track",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            durationMs = 180000L,
            sizeBytes = 4000000L,
            dateModified = 1000L,
            relativePath = "Music/1.mp3",
            titleNormalized = "played track",
            artistNormalized = "artist",
            albumNormalized = "album",
            isMissing = false,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        val unplayedTrack = TrackEntity(
            trackId = 2L,
            mediaStoreId = 102L,
            title = "Unplayed Track",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            durationMs = 200000L,
            sizeBytes = 4500000L,
            dateModified = 1000L,
            relativePath = "Music/2.mp3",
            titleNormalized = "unplayed track",
            artistNormalized = "artist",
            albumNormalized = "album",
            isMissing = false,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        fakeTrackDao.storedTracks.addAll(listOf(playedTrack, unplayedTrack))

        fakePlayEventDao.insertEvent(PlayEventEntity(trackId = 1L, eventType = "PLAY", playedAt = 5000L))

        val repo = TrackRepository(fakeTrackDao, fakeFavoriteDao, SyncEngine(object : MediaStoreScannerFake(emptyList()) {}, fakeTrackDao), fakePlayEventDao)

        // All active tracks returns both
        val all = repo.observeAllTracks().first()
        assertEquals(2, all.size)

        // Recently Played returns ONLY played track
        val recent = repo.observeRecentlyPlayedTracks().first()
        assertEquals(1, recent.size)
        assertEquals(1L, recent.first().id)

        // Most Played returns ONLY played track
        val most = repo.observeMostPlayedTracks().first()
        assertEquals(1, most.size)
        assertEquals(1L, most.first().id)
    }

    /**
     * Test 17 (Milestone 4 M4-D2 - Orphan Hiding and Resurfacing for Play History):
     * Orphaned track is hidden from Recently Played and Most Played queries, and resurfaces with intact history upon re-link.
     */
    @Test
    fun `orphaned track is hidden from recent queries and resurfaces upon return with history intact`() = runTest {
        val track = TrackEntity(
            trackId = 42L,
            mediaStoreId = 2001L,
            title = "Life On Mars?",
            artist = "David Bowie",
            artistId = 10L,
            album = "Hunky Dory",
            albumId = 30L,
            durationMs = 230000L,
            sizeBytes = 5500000L,
            dateModified = 1000L,
            relativePath = "Music/Bowie/LifeOnMars.mp3",
            titleNormalized = "life on mars?",
            artistNormalized = "david bowie",
            albumNormalized = "hunky dory",
            isMissing = false,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        fakeTrackDao.storedTracks.add(track)

        // Insert 3 play events
        fakePlayEventDao.insertEvent(PlayEventEntity(trackId = 42L, eventType = "PLAY", playedAt = 1000L))
        fakePlayEventDao.insertEvent(PlayEventEntity(trackId = 42L, eventType = "PLAY", playedAt = 2000L))
        fakePlayEventDao.insertEvent(PlayEventEntity(trackId = 42L, eventType = "PLAY", playedAt = 3000L))

        val repo = TrackRepository(fakeTrackDao, fakeFavoriteDao, SyncEngine(object : MediaStoreScannerFake(emptyList()) {}, fakeTrackDao), fakePlayEventDao)

        assertEquals(1, repo.observeRecentlyPlayedTracks().first().size)
        assertEquals(1, repo.observeMostPlayedTracks().first().size)

        // File is removed/unplugged -> sync marks track missing
        val emptyScanner = object : MediaStoreScannerFake(emptyList()) {}
        val engine = SyncEngine(emptyScanner, fakeTrackDao)
        engine.synchronize()

        assertTrue(fakeTrackDao.storedTracks.first().isMissing)

        // Query-level orphan hiding: Track is omitted from Recently Played and Most Played
        assertEquals(0, repo.observeRecentlyPlayedTracks().first().size)
        assertEquals(0, repo.observeMostPlayedTracks().first().size)
        // User data retention: play events are still preserved in play_events table
        assertEquals(3, fakePlayEventDao.events.size)

        // File returns -> sync re-links track
        val returningItem = MediaStoreAudioItem(
            mediaStoreId = 8888L,
            title = "Life On Mars?",
            artist = "David Bowie",
            artistId = 10L,
            album = "Hunky Dory",
            albumId = 30L,
            durationMs = 230000L,
            sizeBytes = 5500000L,
            dateModified = 1000L,
            relativePath = "Music/Bowie/LifeOnMars.mp3"
        )
        val returningEngine = SyncEngine(object : MediaStoreScannerFake(listOf(returningItem)) {}, fakeTrackDao)
        returningEngine.synchronize()

        assertFalse(fakeTrackDao.storedTracks.first().isMissing)

        // Resurfaces automatically in Recent and Most Played queries
        val resurfacedRecent = repo.observeRecentlyPlayedTracks().first()
        assertEquals(1, resurfacedRecent.size)
        assertEquals(42L, resurfacedRecent.first().id)

        val resurfacedMost = repo.observeMostPlayedTracks().first()
        assertEquals(1, resurfacedMost.size)
        assertEquals(42L, resurfacedMost.first().id)
    }

    /**
     * Test 18 (Milestone 4 Step 3 - Recently Added date_added Isolation Property):
     * Track A: date_added = 1000, date_modified = 9000 (old add, fresh tag edit)
     * Track B: date_added = 5000, date_modified = 5000 (recently added, untouched)
     * Expected Recently Added order: [Track B, Track A]
     */
    @Test
    fun `recently added query isolates date_added from file date_modified changes`() = runTest {
        val trackA = TrackEntity(
            trackId = 1L,
            mediaStoreId = 101L,
            title = "Track A",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            durationMs = 180000L,
            sizeBytes = 4000000L,
            dateModified = 9000L, // Fresh tag edit!
            dateAdded = 1000L,    // Old addition
            relativePath = "Music/A.mp3",
            titleNormalized = "track a",
            artistNormalized = "artist",
            albumNormalized = "album",
            isMissing = false,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        val trackB = TrackEntity(
            trackId = 2L,
            mediaStoreId = 102L,
            title = "Track B",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            durationMs = 200000L,
            sizeBytes = 4500000L,
            dateModified = 5000L,
            dateAdded = 5000L,    // Newer addition
            relativePath = "Music/B.mp3",
            titleNormalized = "track b",
            artistNormalized = "artist",
            albumNormalized = "album",
            isMissing = false,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        fakeTrackDao.storedTracks.addAll(listOf(trackA, trackB))

        val repo = TrackRepository(fakeTrackDao, fakeFavoriteDao, SyncEngine(object : MediaStoreScannerFake(emptyList()) {}, fakeTrackDao), fakePlayEventDao)

        val recentlyAdded = repo.observeRecentlyAddedTracks().first()

        assertEquals(2, recentlyAdded.size)
        // Must sort strictly by dateAdded DESC: Track B (5000) > Track A (1000)
        assertEquals(2L, recentlyAdded[0].id)
        assertEquals("Track B", recentlyAdded[0].title)
        assertEquals(1L, recentlyAdded[1].id)
        assertEquals("Track A", recentlyAdded[1].title)
    }

    /**
     * Test 19 (Milestone 4 Step 3 - Post-Migration Backfill of date_added):
     * A pre-migration row with date_added = 0 is backfilled with MediaStore DATE_ADDED on sync.
     */
    @Test
    fun `sync engine backfills date_added for pre-migration stored rows`() = runTest {
        // Pre-migration stored track (version 2 schema row had default date_added = 0)
        val preMigrationTrack = TrackEntity(
            trackId = 10L,
            mediaStoreId = 505L,
            title = "Legacy Track",
            artist = "Legacy Artist",
            album = "Legacy Album",
            albumId = 1L,
            durationMs = 210000L,
            sizeBytes = 5000000L,
            dateModified = 1000L,
            dateAdded = 0L, // Unset / migrated default
            relativePath = "Music/Legacy.mp3",
            titleNormalized = "legacy track",
            artistNormalized = "legacy artist",
            albumNormalized = "legacy album",
            isMissing = false,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        fakeTrackDao.storedTracks.add(preMigrationTrack)

        // MediaStore returns the item with valid dateAdded = 1700000000L
        val scannedItem = MediaStoreAudioItem(
            mediaStoreId = 505L,
            title = "Legacy Track",
            artist = "Legacy Artist",
            album = "Legacy Album",
            albumId = 1L,
            durationMs = 210000L,
            sizeBytes = 5000000L,
            dateModified = 1000L,
            dateAdded = 1700000000L,
            relativePath = "Music/Legacy.mp3"
        )

        val scanner = object : MediaStoreScannerFake(listOf(scannedItem)) {}
        val engine = SyncEngine(scanner, fakeTrackDao)
        engine.synchronize()

        val updated = fakeTrackDao.storedTracks.first { it.trackId == 10L }
        assertEquals(1700000000L, updated.dateAdded)
    }

    /**
     * Test 20 (Milestone 4 Step 3 - Tracks Sort by Most Played Logic):
     * Most played sort order ranks tracks with play counts first (by count), followed by unplayed tracks alphabetically.
     */
    @Test
    fun `most played sort order ranks played tracks by play count then unplayed alphabetically`() = runTest {
        val trackA = TrackEntity(
            trackId = 1L,
            mediaStoreId = 101L,
            title = "Alpha",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            durationMs = 180000L,
            sizeBytes = 4000000L,
            dateModified = 1000L,
            dateAdded = 1000L,
            relativePath = "Music/A.mp3",
            titleNormalized = "alpha",
            artistNormalized = "artist",
            albumNormalized = "album",
            isMissing = false,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        val trackB = TrackEntity(
            trackId = 2L,
            mediaStoreId = 102L,
            title = "Bravo",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            durationMs = 200000L,
            sizeBytes = 4500000L,
            dateModified = 1000L,
            dateAdded = 1000L,
            relativePath = "Music/B.mp3",
            titleNormalized = "bravo",
            artistNormalized = "artist",
            albumNormalized = "album",
            isMissing = false,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        val trackC = TrackEntity(
            trackId = 3L,
            mediaStoreId = 103L,
            title = "Charlie",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            durationMs = 190000L,
            sizeBytes = 4200000L,
            dateModified = 1000L,
            dateAdded = 1000L,
            relativePath = "Music/C.mp3",
            titleNormalized = "charlie",
            artistNormalized = "artist",
            albumNormalized = "album",
            isMissing = false,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        val trackD = TrackEntity(
            trackId = 4L,
            mediaStoreId = 104L,
            title = "Delta",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            durationMs = 210000L,
            sizeBytes = 4600000L,
            dateModified = 1000L,
            dateAdded = 1000L,
            relativePath = "Music/D.mp3",
            titleNormalized = "delta",
            artistNormalized = "artist",
            albumNormalized = "album",
            isMissing = false,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        fakeTrackDao.storedTracks.addAll(listOf(trackA, trackB, trackC, trackD))

        // Track B played 10 times
        repeat(10) { i ->
            fakePlayEventDao.insertEvent(PlayEventEntity(trackId = 2L, eventType = "PLAY", playedAt = 1000L + i))
        }
        // Track D played 3 times
        repeat(3) { i ->
            fakePlayEventDao.insertEvent(PlayEventEntity(trackId = 4L, eventType = "PLAY", playedAt = 2000L + i))
        }
        // Track A and Track C have 0 plays

        val repo = TrackRepository(fakeTrackDao, fakeFavoriteDao, SyncEngine(object : MediaStoreScannerFake(emptyList()) {}, fakeTrackDao), fakePlayEventDao)

        val allTracks = repo.observeAllTracks().first()
        val mostPlayed = repo.observeMostPlayedTracks().first()

        // Apply ViewModel MOST_PLAYED sorting algorithm
        val mostPlayedIds = mostPlayed.map { it.id }
        val rankMap = mostPlayedIds.mapIndexed { index, id -> id to index }.toMap()
        val (played, unplayed) = allTracks.partition { rankMap.containsKey(it.id) }
        val sortedPlayed = played.sortedBy { rankMap[it.id] }
        val sortedUnplayed = unplayed.sortedBy { it.title.lowercase() }
        val finalSorted = sortedPlayed + sortedUnplayed

        assertEquals(4, finalSorted.size)
        // Played ranked first by count: Bravo (10 plays), Delta (3 plays)
        assertEquals("Bravo", finalSorted[0].title)
        assertEquals("Delta", finalSorted[1].title)
        // Unplayed appended alphabetically: Alpha, Charlie
        assertEquals("Alpha", finalSorted[2].title)
        assertEquals("Charlie", finalSorted[3].title)
    }

    private fun createScanItem(
        mediaStoreId: Long,
        title: String,
        artist: String,
        durationMs: Long,
        sizeBytes: Long,
        relativePath: String = "Music/",
        dateAdded: Long = 0L
    ): MediaStoreAudioItem {
        return MediaStoreAudioItem(
            mediaStoreId = mediaStoreId,
            title = title,
            artist = artist,
            album = "Test Album",
            albumId = 10,
            durationMs = durationMs,
            sizeBytes = sizeBytes,
            dateModified = 1000,
            dateAdded = dateAdded,
            relativePath = relativePath
        )
    }
}

open class MediaStoreScannerFake(
    private val items: List<MediaStoreAudioItem>,
    private val permissionGranted: Boolean = true
) : MediaStoreScanner(android.content.ContextWrapper(null)) {
    override fun hasStoragePermission(): Boolean = permissionGranted
    override fun scanAudioFiles(): List<MediaStoreAudioItem> = items
}

class FakeTrackDao : TrackDao {
    val storedTracks = mutableListOf<TrackEntity>()
    private var nextId = 100L

    override fun observeAllActiveTracks(): Flow<List<TrackEntity>> = flowOf(storedTracks.filter { !it.isMissing })
    override suspend fun getAllActiveTracks(): List<TrackEntity> = storedTracks.filter { !it.isMissing }
    override suspend fun getAllStoredTracks(): List<TrackEntity> = storedTracks.toList()
    override suspend fun getTrackById(trackId: Long): TrackEntity? = storedTracks.find { it.trackId == trackId }
    override suspend fun getTracksByIds(trackIds: List<Long>): List<TrackEntity> = storedTracks.filter { trackIds.contains(it.trackId) }
    override suspend fun getTrackByMediaStoreId(mediaStoreId: Long): TrackEntity? = storedTracks.find { it.mediaStoreId == mediaStoreId }

    override fun searchTracks(query: String): Flow<List<TrackEntity>> = flowOf(emptyList())

    override fun observeRecentlyAddedTracks(limit: Int): Flow<List<TrackEntity>> {
        return flowOf(storedTracks.filter { !it.isMissing }.sortedByDescending { it.dateAdded }.take(limit))
    }

    override suspend fun getRecentlyAddedTracks(limit: Int): List<TrackEntity> {
        return storedTracks.filter { !it.isMissing }.sortedByDescending { it.dateAdded }.take(limit)
    }

    override fun observeAllAlbums(): Flow<List<TrackDao.AlbumSummary>> {
        val summaries = storedTracks.filter { !it.isMissing }
            .groupBy { it.albumId }
            .map { (albumId, tracks) ->
                TrackDao.AlbumSummary(
                    album_id = albumId,
                    album = tracks.first().album,
                    artist = tracks.first().artist,
                    artist_id = tracks.first().artistId,
                    year = tracks.maxOfOrNull { it.year } ?: 0,
                    track_count = tracks.size,
                    total_duration_ms = tracks.sumOf { it.durationMs }
                )
            }
        return flowOf(summaries)
    }

    override suspend fun getAlbumById(albumId: Long): TrackDao.AlbumSummary? {
        val tracks = storedTracks.filter { it.albumId == albumId && !it.isMissing }
        if (tracks.isEmpty()) return null
        return TrackDao.AlbumSummary(
            album_id = albumId,
            album = tracks.first().album,
            artist = tracks.first().artist,
            artist_id = tracks.first().artistId,
            year = tracks.maxOfOrNull { it.year } ?: 0,
            track_count = tracks.size,
            total_duration_ms = tracks.sumOf { it.durationMs }
        )
    }

    override fun observeTracksForAlbum(albumId: Long): Flow<List<TrackEntity>> {
        return flowOf(storedTracks.filter { it.albumId == albumId && !it.isMissing }
            .sortedWith(compareBy({ it.discNumber }, { it.trackNumber }, { it.titleNormalized })))
    }

    override suspend fun getTracksForAlbum(albumId: Long): List<TrackEntity> {
        return storedTracks.filter { it.albumId == albumId && !it.isMissing }
            .sortedWith(compareBy({ it.discNumber }, { it.trackNumber }, { it.titleNormalized }))
    }

    override fun observeAllArtists(): Flow<List<TrackDao.ArtistSummary>> {
        val summaries = storedTracks.filter { !it.isMissing }
            .groupBy { it.artistNormalized }
            .map { (_, tracks) ->
                TrackDao.ArtistSummary(
                    artist_id = tracks.first().artistId,
                    artist = tracks.first().artist,
                    album_count = tracks.map { it.albumId }.distinct().size,
                    track_count = tracks.size
                )
            }
        return flowOf(summaries)
    }

    override fun observeAlbumsForArtist(artistNormalized: String): Flow<List<TrackDao.AlbumSummary>> {
        val summaries = storedTracks.filter { it.artistNormalized == artistNormalized && !it.isMissing }
            .groupBy { it.albumId }
            .map { (albumId, tracks) ->
                TrackDao.AlbumSummary(
                    album_id = albumId,
                    album = tracks.first().album,
                    artist = tracks.first().artist,
                    artist_id = tracks.first().artistId,
                    year = tracks.maxOfOrNull { it.year } ?: 0,
                    track_count = tracks.size,
                    total_duration_ms = tracks.sumOf { it.durationMs }
                )
            }
        return flowOf(summaries)
    }

    override fun observeTracksForArtist(artistNormalized: String): Flow<List<TrackEntity>> {
        return flowOf(storedTracks.filter { it.artistNormalized == artistNormalized && !it.isMissing }
            .sortedWith(compareBy({ it.albumNormalized }, { it.discNumber }, { it.trackNumber }, { it.titleNormalized })))
    }

    override suspend fun getTracksForArtist(artistNormalized: String): List<TrackEntity> {
        return storedTracks.filter { it.artistNormalized == artistNormalized && !it.isMissing }
            .sortedWith(compareBy({ it.year }, { it.albumNormalized }, { it.discNumber }, { it.trackNumber }, { it.titleNormalized }))
    }

    override suspend fun findReLinkCandidates(minDurationMs: Long, maxDurationMs: Long, sizeBytes: Long): List<TrackEntity> {
        return storedTracks.filter {
            it.isMissing && it.durationMs in minDurationMs..maxDurationMs && it.sizeBytes == sizeBytes
        }
    }

    override suspend fun insertTrack(track: TrackEntity): Long {
        val id = if (track.trackId == 0L) nextId++ else track.trackId
        val entity = track.copy(trackId = id)
        storedTracks.add(entity)
        return id
    }

    override suspend fun insertTracks(tracks: List<TrackEntity>): List<Long> {
        return tracks.map { insertTrack(it) }
    }

    override suspend fun updateTrack(track: TrackEntity) {
        val index = storedTracks.indexOfFirst { it.trackId == track.trackId }
        if (index >= 0) storedTracks[index] = track
    }

    override suspend fun updateTracks(tracks: List<TrackEntity>) {
        tracks.forEach { updateTrack(it) }
    }

    override suspend fun reLinkTrack(trackId: Long, newMediaStoreId: Long, timestamp: Long) {
        val index = storedTracks.indexOfFirst { it.trackId == trackId }
        if (index >= 0) {
            val old = storedTracks[index]
            storedTracks[index] = old.copy(
                mediaStoreId = newMediaStoreId,
                isMissing = false,
                lastSeenTimestamp = timestamp
            )
        }
    }

    override suspend fun markTracksMissing(trackIds: List<Long>) {
        trackIds.forEach { id ->
            val index = storedTracks.indexOfFirst { it.trackId == id }
            if (index >= 0) storedTracks[index] = storedTracks[index].copy(isMissing = true)
        }
    }

    override suspend fun markTracksPresent(trackIds: List<Long>, timestamp: Long) {
        trackIds.forEach { id ->
            val index = storedTracks.indexOfFirst { it.trackId == id }
            if (index >= 0) storedTracks[index] = storedTracks[index].copy(isMissing = false, lastSeenTimestamp = timestamp)
        }
    }

    override suspend fun purgeOrphanedTracks(purgeCutoffTimestamp: Long): Int {
        val toRemove = storedTracks.filter { it.isMissing && it.lastSeenTimestamp < purgeCutoffTimestamp }
        storedTracks.removeAll(toRemove)
        return toRemove.size
    }
}

class FakeFavoriteDao(private val fakeTrackDao: FakeTrackDao? = null) : FavoriteDao {
    val favorites = mutableMapOf<Long, Long>() // trackId -> addedAt

    override fun observeFavoriteTrackIds(): Flow<List<Long>> = flowOf(favorites.keys.toList())
    override suspend fun getFavoriteTrackIds(): List<Long> = favorites.keys.toList()

    override fun observeFavoriteTrackEntities(): Flow<List<TrackEntity>> {
        val activeFavs = fakeTrackDao?.storedTracks
            ?.filter { !it.isMissing && favorites.containsKey(it.trackId) }
            ?.sortedByDescending { favorites[it.trackId] ?: 0L }
            ?: emptyList()
        return flowOf(activeFavs)
    }

    override suspend fun getFavoriteTrackEntities(): List<TrackEntity> {
        return fakeTrackDao?.storedTracks
            ?.filter { !it.isMissing && favorites.containsKey(it.trackId) }
            ?.sortedByDescending { favorites[it.trackId] ?: 0L }
            ?: emptyList()
    }

    override fun observeIsFavorite(trackId: Long): Flow<Boolean> = flowOf(favorites.containsKey(trackId))
    override suspend fun isFavorite(trackId: Long): Boolean = favorites.containsKey(trackId)
    override suspend fun addFavorite(favorite: FavoriteTrackEntity) {
        favorites[favorite.trackId] = favorite.addedAt
    }
    override suspend fun removeFavorite(trackId: Long) {
        favorites.remove(trackId)
    }
}

class FakePlayEventDao(private val fakeTrackDao: FakeTrackDao? = null) : PlayEventDao {
    val events = mutableListOf<PlayEventEntity>()

    override suspend fun insertEvent(event: PlayEventEntity): Long {
        events.add(event)
        return events.size.toLong()
    }

    override fun observeRecentlyPlayedTrackEntities(limit: Int): Flow<List<TrackEntity>> {
        return flowOf(getRecentlyPlayedTrackEntitiesSync(limit))
    }

    override suspend fun getRecentlyPlayedTrackEntities(limit: Int): List<TrackEntity> {
        return getRecentlyPlayedTrackEntitiesSync(limit)
    }

    private fun getRecentlyPlayedTrackEntitiesSync(limit: Int): List<TrackEntity> {
        val latestPlayByTrackId = events.filter { it.eventType == "PLAY" }
            .groupBy { it.trackId }
            .mapValues { (_, evts) -> evts.maxOf { it.playedAt } }

        return fakeTrackDao?.storedTracks
            ?.filter { !it.isMissing && latestPlayByTrackId.containsKey(it.trackId) }
            ?.sortedByDescending { latestPlayByTrackId[it.trackId] ?: 0L }
            ?.take(limit)
            ?: emptyList()
    }

    override fun observeMostPlayedTrackEntities(limit: Int): Flow<List<TrackEntity>> {
        return flowOf(getMostPlayedTrackEntitiesSync(limit))
    }

    override suspend fun getMostPlayedTrackEntities(limit: Int): List<TrackEntity> {
        return getMostPlayedTrackEntitiesSync(limit)
    }

    private fun getMostPlayedTrackEntitiesSync(limit: Int): List<TrackEntity> {
        val playCountByTrackId = events.filter { it.eventType == "PLAY" }
            .groupBy { it.trackId }
            .mapValues { (_, evts) -> evts.size.toLong() }

        return fakeTrackDao?.storedTracks
            ?.filter { !it.isMissing && playCountByTrackId.containsKey(it.trackId) }
            ?.sortedByDescending { playCountByTrackId[it.trackId] ?: 0L }
            ?.take(limit)
            ?: emptyList()
    }
}
