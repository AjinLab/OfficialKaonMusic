package com.kaon.music.core.data.sync

import com.kaon.music.core.data.db.dao.FavoriteDao
import com.kaon.music.core.data.db.dao.PlayEventDao
import com.kaon.music.core.data.db.dao.TrackDao
import com.kaon.music.core.data.db.entity.FavoriteTrackEntity
import com.kaon.music.core.data.db.entity.PlayEventEntity
import com.kaon.music.core.data.db.entity.TrackEntity
import com.kaon.music.core.data.model.PlayEvent
import kotlinx.coroutines.flow.Flow
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
        fakeFavoriteDao = FakeFavoriteDao()
        fakePlayEventDao = FakePlayEventDao()
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
     * Test 2: A temporarily missing track keeps its trackId, favorite state, and play history.
     */
    @Test
    fun `temporarily missing track keeps its trackId, favorite state, and play history`() = runTest {
        val originalTrackId = 55L
        val track = TrackEntity(
            trackId = originalTrackId,
            mediaStoreId = 301,
            title = "Stairway to Heaven",
            artist = "Led Zeppelin",
            album = "Led Zeppelin IV",
            albumId = 4,
            durationMs = 482000,
            sizeBytes = 12000000,
            dateModified = 1000,
            relativePath = "Music/Rock/",
            titleNormalized = "stairway to heaven",
            artistNormalized = "led zeppelin",
            albumNormalized = "led zeppelin iv",
            isMissing = false
        )
        fakeTrackDao.storedTracks.add(track)
        fakeFavoriteDao.addFavorite(FavoriteTrackEntity(trackId = originalTrackId))
        fakePlayEventDao.insertEvent(
            PlayEventEntity(
                id = 1,
                trackId = originalTrackId,
                eventType = PlayEvent.EventType.PLAY.name,
                playedMs = 482000
            )
        )

        // Simulate file temporarily missing (e.g. unmounted SD card or permission toggle)
        val emptyScanner = object : MediaStoreScannerFake(emptyList()) {}
        val engine = SyncEngine(emptyScanner, fakeTrackDao)
        val syncResult = engine.synchronize()

        assertEquals(1, syncResult.markedMissing)

        // Verify track is flagged as missing, but ID, favorite, and history are preserved
        val missingTrack = fakeTrackDao.getTrackById(originalTrackId)
        assertNotNull(missingTrack)
        assertTrue(missingTrack!!.isMissing)
        assertEquals(originalTrackId, missingTrack.trackId)
        assertTrue(fakeFavoriteDao.isFavorite(originalTrackId))
        assertEquals(1, fakePlayEventDao.events.size)
        assertEquals(originalTrackId, fakePlayEventDao.events.first().trackId)
    }

    /**
     * Test 3: A file returning after being missing re-links to the original track.
     */
    @Test
    fun `file returning after being missing re-links to original track`() = runTest {
        val originalTrackId = 77L
        val missingTrack = TrackEntity(
            trackId = originalTrackId,
            mediaStoreId = 404, // Old MediaStore ID
            title = "Hotel California",
            artist = "Eagles",
            album = "Hotel California",
            albumId = 7,
            durationMs = 390000,
            sizeBytes = 9500000,
            dateModified = 1000,
            relativePath = "Music/Eagles/",
            titleNormalized = "hotel california",
            artistNormalized = "eagles",
            albumNormalized = "hotel california",
            isMissing = true
        )
        fakeTrackDao.storedTracks.add(missingTrack)

        // File returns (possibly with new MediaStore ID after system rescanned it)
        val scanner = object : MediaStoreScannerFake(
            listOf(
                createScanItem(
                    mediaStoreId = 888, // New MediaStore ID assigned by system
                    title = "Hotel California",
                    artist = "Eagles",
                    durationMs = 390100,
                    sizeBytes = 9500000,
                    relativePath = "Music/Eagles/"
                )
            )
        ) {}

        val engine = SyncEngine(scanner, fakeTrackDao)
        val result = engine.synchronize()

        assertEquals(1, result.reLinked)
        assertEquals(0, result.added)

        val restored = fakeTrackDao.getTrackById(originalTrackId)
        assertNotNull(restored)
        assertEquals(originalTrackId, restored!!.trackId)
        assertEquals(888L, restored.mediaStoreId)
        assertFalse(restored.isMissing)
    }

    /**
     * Test 4: A genuinely new file does not accidentally attach to an old track.
     */
    @Test
    fun `genuinely new file does not accidentally attach to an old missing track`() = runTest {
        val oldMissingTrack = TrackEntity(
            trackId = 10,
            mediaStoreId = 500,
            title = "Yesterday",
            artist = "The Beatles",
            album = "Help!",
            albumId = 2,
            durationMs = 125000,
            sizeBytes = 3200000,
            dateModified = 100,
            relativePath = "Music/Beatles/",
            titleNormalized = "yesterday",
            artistNormalized = "the beatles",
            albumNormalized = "help!",
            isMissing = true
        )
        fakeTrackDao.storedTracks.add(oldMissingTrack)

        // Genuinely new file with completely different metadata but matching duration
        val scanner = object : MediaStoreScannerFake(
            listOf(
                createScanItem(
                    mediaStoreId = 700,
                    title = "Song With Same Length",
                    artist = "Different Artist",
                    durationMs = 125000,
                    sizeBytes = 3200000,
                    relativePath = "Music/Other/"
                )
            )
        ) {}

        val engine = SyncEngine(scanner, fakeTrackDao)
        val result = engine.synchronize()

        assertEquals(0, result.reLinked)
        assertEquals(1, result.added)
        assertEquals(2, fakeTrackDao.storedTracks.size)

        // Old missing track remains untouched
        val old = fakeTrackDao.getTrackById(10)
        assertTrue(old!!.isMissing)
    }

    /**
     * Test 5: MediaStore _ID changes while the actual track remains the same.
     */
    @Test
    fun `mediaStore ID changes while actual track remains the same`() = runTest {
        val originalTrackId = 100L
        val track = TrackEntity(
            trackId = originalTrackId,
            mediaStoreId = 1000,
            title = "Comfortably Numb",
            artist = "Pink Floyd",
            album = "The Wall",
            albumId = 9,
            durationMs = 382000,
            sizeBytes = 11000000,
            dateModified = 500,
            relativePath = "Music/PinkFloyd/",
            titleNormalized = "comfortably numb",
            artistNormalized = "pink floyd",
            albumNormalized = "the wall",
            isMissing = true // Vanished under old ID 1000
        )
        fakeTrackDao.storedTracks.add(track)

        // System rescanned volume and assigned mediaStoreId = 2000
        val scanner = object : MediaStoreScannerFake(
            listOf(
                createScanItem(
                    mediaStoreId = 2000,
                    title = "Comfortably Numb",
                    artist = "Pink Floyd",
                    durationMs = 382000,
                    sizeBytes = 11000000,
                    relativePath = "Music/PinkFloyd/"
                )
            )
        ) {}

        val engine = SyncEngine(scanner, fakeTrackDao)
        val result = engine.synchronize()

        assertEquals(1, result.reLinked)
        val reLinked = fakeTrackDao.getTrackById(originalTrackId)
        assertEquals(2000L, reLinked!!.mediaStoreId)
        assertEquals(originalTrackId, reLinked.trackId)
    }

    /**
     * Test 9: Repeated syncs are idempotent.
     */
    @Test
    fun `repeated syncs are idempotent`() = runTest {
        val scanItems = listOf(
            createScanItem(mediaStoreId = 1, title = "Song 1", artist = "Artist 1", durationMs = 180000, sizeBytes = 4000000),
            createScanItem(mediaStoreId = 2, title = "Song 2", artist = "Artist 2", durationMs = 210000, sizeBytes = 5000000),
            createScanItem(mediaStoreId = 3, title = "Song 3", artist = "Artist 3", durationMs = 240000, sizeBytes = 6000000)
        )
        val scanner = object : MediaStoreScannerFake(scanItems) {}
        val engine = SyncEngine(scanner, fakeTrackDao)

        // Run 1: Initial sync
        val res1 = engine.synchronize()
        assertEquals(3, res1.added)
        assertEquals(3, fakeTrackDao.storedTracks.size)

        // Run 2: Immediate resync (no changes)
        val res2 = engine.synchronize()
        assertEquals(0, res2.added)
        assertEquals(0, res2.updated)
        assertEquals(0, res2.reLinked)
        assertEquals(0, res2.markedMissing)
        assertEquals(3, fakeTrackDao.storedTracks.size)

        // Run 3: Another resync
        val res3 = engine.synchronize()
        assertEquals(0, res3.added)
        assertEquals(0, res3.updated)
        assertEquals(3, fakeTrackDao.storedTracks.size)
    }

    /**
     * Test 10: Permission revocation does not corrupt database.
     */
    @Test
    fun `permission revocation or empty scan preserves user database safely`() = runTest {
        // Pre-populate tracks with favorites
        fakeTrackDao.storedTracks.add(
            TrackEntity(
                trackId = 1,
                mediaStoreId = 10,
                title = "Important Song",
                artist = "Favorite Artist",
                album = "Album",
                albumId = 1,
                durationMs = 200000,
                sizeBytes = 5000000,
                dateModified = 10,
                relativePath = "Music/",
                titleNormalized = "important song",
                artistNormalized = "favorite artist",
                albumNormalized = "album",
                isMissing = false
            )
        )
        fakeFavoriteDao.addFavorite(FavoriteTrackEntity(trackId = 1))

        // Scanner returns empty list (permission revoked)
        val scanner = object : MediaStoreScannerFake(emptyList()) {}
        val engine = SyncEngine(scanner, fakeTrackDao)
        engine.synchronize(orphanRetentionDays = 30)

        // Track is marked missing, but record is NOT deleted, and favorite is intact
        assertEquals(1, fakeTrackDao.storedTracks.size)
        assertTrue(fakeTrackDao.storedTracks.first().isMissing)
        assertTrue(fakeFavoriteDao.isFavorite(1))
    }

    private fun createScanItem(
        mediaStoreId: Long,
        title: String,
        artist: String,
        durationMs: Long,
        sizeBytes: Long,
        relativePath: String = "Music/"
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
            relativePath = relativePath,
            contentUri = android.net.Uri.EMPTY
        )
    }
}

open class MediaStoreScannerFake(private val items: List<MediaStoreAudioItem>) :
    MediaStoreScanner(android.content.ContextWrapper(null)) {
    fun getItems(): List<MediaStoreAudioItem> = items
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
    override fun observeDistinctAlbums(): Flow<List<TrackDao.AlbumSummary>> = flowOf(emptyList())
    override fun observeDistinctArtists(): Flow<List<String>> = flowOf(emptyList())
    override fun observeTracksByAlbum(album: String): Flow<List<TrackEntity>> = flowOf(emptyList())
    override fun observeTracksByArtist(artist: String): Flow<List<TrackEntity>> = flowOf(emptyList())

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

class FakeFavoriteDao : FavoriteDao {
    val favorites = mutableSetOf<Long>()

    override fun observeFavoriteTrackIds(): Flow<List<Long>> = flowOf(favorites.toList())
    override fun observeIsFavorite(trackId: Long): Flow<Boolean> = flowOf(favorites.contains(trackId))
    override suspend fun isFavorite(trackId: Long): Boolean = favorites.contains(trackId)
    override suspend fun addFavorite(favorite: FavoriteTrackEntity) {
        favorites.add(favorite.trackId)
    }
    override suspend fun removeFavorite(trackId: Long) {
        favorites.remove(trackId)
    }
}

class FakePlayEventDao : PlayEventDao {
    val events = mutableListOf<PlayEventEntity>()

    override suspend fun insertEvent(event: PlayEventEntity): Long {
        events.add(event)
        return events.size.toLong()
    }

    override fun observeRecentlyPlayedTrackIds(limit: Int): Flow<List<Long>> {
        return flowOf(events.map { it.trackId }.distinct().take(limit))
    }

    override suspend fun getMostPlayedTrackIds(limit: Int): List<PlayEventDao.TrackPlayCount> {
        return events.groupBy { it.trackId }.map {
            PlayEventDao.TrackPlayCount(it.key, it.value.size.toLong())
        }.sortedByDescending { it.play_count }.take(limit)
    }
}
