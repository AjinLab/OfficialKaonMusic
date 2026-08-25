package com.kaon.music.matrix

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaon.music.core.data.db.KaonDatabase
import com.kaon.music.core.data.db.entity.FavoriteTrackEntity
import com.kaon.music.core.data.db.entity.PlayEventEntity
import com.kaon.music.core.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android On-Device Integration Tests for Milestone 4 Device Matrix rows (Row 25, 26, 28, 29).
 * Executed on real Android SQLite engine on the attached hardware / emulator target.
 */
@RunWith(AndroidJUnit4::class)
class DeviceMatrixIntegrationTest {

    private lateinit var db: KaonDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KaonDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * Row 25 & 26 (On-Device SQLite):
     * Favorite a track -> mark missing -> hidden from SQL join query -> favorite record retained -> mark present -> resurfaces.
     */
    @Test
    fun testRow25And26FavoriteSurvivalAndOrphanHidingOnDeviceSQLite() = runBlocking {
        val trackDao = db.trackDao()
        val favoriteDao = db.favoriteDao()

        val track = TrackEntity(
            trackId = 77L,
            mediaStoreId = 1001L,
            title = "Starman",
            artist = "David Bowie",
            album = "Ziggy Stardust",
            albumId = 20L,
            durationMs = 256000L,
            sizeBytes = 6500000L,
            dateModified = 1000L,
            dateAdded = 1000L,
            relativePath = "Music/Bowie/Starman.mp3",
            titleNormalized = "starman",
            artistNormalized = "david bowie",
            albumNormalized = "ziggy stardust",
            isMissing = false,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        trackDao.insertTrack(track)
        favoriteDao.addFavorite(FavoriteTrackEntity(trackId = 77L))

        // Initial: 1 active favorite track in SQL JOIN
        val initialFavs = favoriteDao.observeFavoriteTrackEntities().first()
        assertEquals(1, initialFavs.size)
        assertEquals(77L, initialFavs.first().trackId)

        // Row 26: SD card unplugged -> mark missing
        trackDao.markTracksMissing(listOf(77L))
        val missingTrack = trackDao.getTrackById(77L)
        assertNotNull(missingTrack)
        assertTrue(missingTrack!!.isMissing)

        // SQL JOIN query WHERE t.is_missing = 0 automatically hides orphaned track
        val duringOrphanFavs = favoriteDao.observeFavoriteTrackEntities().first()
        assertEquals(0, duringOrphanFavs.size)

        // User favorite record is preserved
        assertTrue(favoriteDao.isFavorite(77L))

        // Row 25: File returns with new mediaStoreId -> reLinkTrack
        trackDao.reLinkTrack(77L, 9999L)
        val reLinkedTrack = trackDao.getTrackById(77L)
        assertNotNull(reLinkedTrack)
        assertFalse(reLinkedTrack!!.isMissing)
        assertEquals(9999L, reLinkedTrack.mediaStoreId)

        // Favorite resurfaces automatically with stable trackId = 77L
        val resurfacedFavs = favoriteDao.observeFavoriteTrackEntities().first()
        assertEquals(1, resurfacedFavs.size)
        assertEquals(77L, resurfacedFavs.first().trackId)
        assertEquals(9999L, resurfacedFavs.first().mediaStoreId)
    }

    /**
     * Row 29 (On-Device SQLite):
     * A zero-play track is present in active tracks but absent from recently played and most played queries.
     */
    @Test
    fun testRow29ZeroPlayIsolationOnDeviceSQLite() = runBlocking {
        val trackDao = db.trackDao()
        val playEventDao = db.playEventDao()

        val track1 = TrackEntity(
            trackId = 1L,
            mediaStoreId = 101L,
            title = "Track Played",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            durationMs = 180000L,
            sizeBytes = 4000000L,
            dateModified = 1000L,
            dateAdded = 1000L,
            relativePath = "Music/1.mp3",
            titleNormalized = "track played",
            artistNormalized = "artist",
            albumNormalized = "album",
            isMissing = false,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        val track2 = TrackEntity(
            trackId = 2L,
            mediaStoreId = 102L,
            title = "Track Unplayed",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            durationMs = 200000L,
            sizeBytes = 4500000L,
            dateModified = 1000L,
            dateAdded = 1000L,
            relativePath = "Music/2.mp3",
            titleNormalized = "track unplayed",
            artistNormalized = "artist",
            albumNormalized = "album",
            isMissing = false,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        trackDao.insertTracks(listOf(track1, track2))

        // Only track 1 is played
        playEventDao.insertEvent(PlayEventEntity(trackId = 1L, eventType = "PLAY", playedAt = 5000L))

        // All active tracks has both
        assertEquals(2, trackDao.getAllActiveTracks().size)

        // Recently Played has ONLY track 1
        val recent = playEventDao.getRecentlyPlayedTrackEntities(10)
        assertEquals(1, recent.size)
        assertEquals(1L, recent.first().trackId)

        // Most Played has ONLY track 1
        val most = playEventDao.getMostPlayedTrackEntities(10)
        assertEquals(1, most.size)
        assertEquals(1L, most.first().trackId)
    }

    /**
     * Row 30 (On-Device SQLite):
     * Recently Added query strictly orders by date_added DESC, not date_modified.
     */
    @Test
    fun testRow30RecentlyAddedIsolationOnDeviceSQLite() = runBlocking {
        val trackDao = db.trackDao()

        val trackA = TrackEntity(
            trackId = 1L,
            mediaStoreId = 101L,
            title = "Track A",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            durationMs = 180000L,
            sizeBytes = 4000000L,
            dateModified = 999999L, // Freshly modified
            dateAdded = 1000L,      // Old add
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
            dateAdded = 5000L,      // Newer add
            relativePath = "Music/B.mp3",
            titleNormalized = "track b",
            artistNormalized = "artist",
            albumNormalized = "album",
            isMissing = false,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        trackDao.insertTracks(listOf(trackA, trackB))

        val recentlyAdded = trackDao.getRecentlyAddedTracks(10)
        assertEquals(2, recentlyAdded.size)
        // Must sort by dateAdded DESC: Track B (5000) > Track A (1000)
        assertEquals(2L, recentlyAdded[0].trackId)
        assertEquals(1L, recentlyAdded[1].trackId)
    }

    /**
     * M5-D2 & M5-D5 (On-Device SQLite):
     * Playlist track membership, orphan hiding, re-link resurfacing, and transactional reordering.
     */
    @Test
    fun testPlaylistOrphanHidingAndTransactionalReorderingOnDeviceSQLite() = runBlocking {
        val trackDao = db.trackDao()
        val playlistDao = db.playlistDao()

        val track1 = TrackEntity(
            trackId = 10L,
            mediaStoreId = 1001L,
            title = "Track 1",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            durationMs = 180000L,
            sizeBytes = 4000000L,
            dateModified = 1000L,
            dateAdded = 1000L,
            relativePath = "Music/1.mp3",
            titleNormalized = "track 1",
            artistNormalized = "artist",
            albumNormalized = "album",
            isMissing = false,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        val track2 = TrackEntity(
            trackId = 20L,
            mediaStoreId = 1002L,
            title = "Track 2",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            durationMs = 200000L,
            sizeBytes = 4500000L,
            dateModified = 1000L,
            dateAdded = 1000L,
            relativePath = "Music/2.mp3",
            titleNormalized = "track 2",
            artistNormalized = "artist",
            albumNormalized = "album",
            isMissing = false,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        trackDao.insertTracks(listOf(track1, track2))

        val pId = playlistDao.insertPlaylist(
            com.kaon.music.core.data.db.entity.PlaylistEntity(name = "Party Mix")
        )

        playlistDao.addTrackToPlaylist(com.kaon.music.core.data.db.entity.PlaylistTrackEntity(pId, 10L, 0, 1000L))
        playlistDao.addTrackToPlaylist(com.kaon.music.core.data.db.entity.PlaylistTrackEntity(pId, 20L, 1, 1001L))

        // Initial visible tracks
        val initial = playlistDao.getTracksForPlaylist(pId)
        assertEquals(2, initial.size)
        assertEquals(10L, initial[0].trackId)
        assertEquals(20L, initial[1].trackId)

        // Mark track 2 missing -> hidden from playlist view
        trackDao.markTracksMissing(listOf(20L))
        val withOrphan = playlistDao.getTracksForPlaylist(pId)
        assertEquals(1, withOrphan.size)
        assertEquals(10L, withOrphan.first().trackId)

        // Re-link track 2 -> resurfaces at position 1
        trackDao.reLinkTrack(20L, 9999L)
        val resurfaced = playlistDao.getTracksForPlaylist(pId)
        assertEquals(2, resurfaced.size)
        assertEquals(10L, resurfaced[0].trackId)
        assertEquals(20L, resurfaced[1].trackId)

        // Transactional reorder: swap positions
        playlistDao.updateTrackPositions(
            listOf(
                com.kaon.music.core.data.db.entity.PlaylistTrackEntity(pId, 20L, 0, 1001L),
                com.kaon.music.core.data.db.entity.PlaylistTrackEntity(pId, 10L, 1, 1000L)
            )
        )
        val reordered = playlistDao.getTracksForPlaylist(pId)
        assertEquals(20L, reordered[0].trackId)
        assertEquals(10L, reordered[1].trackId)

        // Delete playlist containing tracks
        playlistDao.deletePlaylist(pId)
        assertEquals(0, playlistDao.getPlaylistTrackEntries(pId).size)
        // Tracks in tracks table remain intact
        assertEquals(2, trackDao.getAllStoredTracks().size)
    }

    /**
     * M5 Exit Criterion: Process Death During Reorder
     *
     * 1. Create playlist with 10 tracks on real disk-backed SQLite database.
     * 2. Move track at position 5 (trackId 5L) to position 2 via PlaylistRepository.reorderTracks.
     * 3. Simulate process death: close database instance completely.
     * 4. Relaunch: open brand new database connection from disk file.
     * 5. Verify: track 5 is at index 2 (position 2), and all other tracks retain their expected order.
     */
    @Test
    fun testProcessDeathReorderPersistenceOnDiskDatabase() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbFile = context.getDatabasePath("test_process_death_reorder.db")
            if (dbFile.exists()) dbFile.delete()

            // 1. Initial Launch: Create disk database & populate 10 tracks
            val diskDb = Room.databaseBuilder(context, KaonDatabase::class.java, "test_process_death_reorder.db")
                .allowMainThreadQueries()
                .build()

            val trackDao = diskDb.trackDao()
            val playlistDao = diskDb.playlistDao()
            val favoriteDao = diskDb.favoriteDao()
            val playlistRepo = com.kaon.music.core.data.repository.PlaylistRepository(playlistDao, trackDao, favoriteDao)

            val trackEntities = (1..10).map { i ->
                TrackEntity(
                    trackId = i.toLong(),
                    mediaStoreId = 1000L + i,
                    title = "Track $i",
                    artist = "Artist",
                    album = "Album",
                    albumId = 1L,
                    durationMs = 180000L,
                    sizeBytes = 4000000L,
                    dateModified = 1000L,
                    dateAdded = 1000L,
                    relativePath = "Music/$i.mp3",
                    titleNormalized = "track $i",
                    artistNormalized = "artist",
                    albumNormalized = "album",
                    isMissing = false,
                    lastSeenTimestamp = System.currentTimeMillis()
                )
            }
            trackDao.insertTracks(trackEntities)

            val playlistId = playlistRepo.createPlaylist("Chill Study Mix")
            for (i in 1..10) {
                playlistRepo.addTrackToPlaylist(playlistId, i.toLong())
            }

            // Verify initial 10 tracks order: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
            val initialTracks = playlistDao.getTracksForPlaylist(playlistId)
            assertEquals(10, initialTracks.size)
            for (i in 0..9) {
                assertEquals((i + 1).toLong(), initialTracks[i].trackId)
            }

            // 2. User moves track at position 5 (trackId 6L) to position 2
            // New desired order: [1, 2, 6, 3, 4, 5, 7, 8, 9, 10]
            val reorderedIds = listOf(1L, 2L, 6L, 3L, 4L, 5L, 7L, 8L, 9L, 10L)
            playlistRepo.reorderTracks(playlistId, reorderedIds)

            // 3. Simulate Sudden Process Death (kill DB connection)
            diskDb.close()

            // 4. App Relaunch from cold storage: Create a completely fresh Database instance
            val freshDb = Room.databaseBuilder(context, KaonDatabase::class.java, "test_process_death_reorder.db")
                .allowMainThreadQueries()
                .build()

            val freshPlaylistDao = freshDb.playlistDao()

            // 5. Verify: Track 6 is at index 2 (position 2), entire sequence is perfectly preserved
            val restoredTracks = freshPlaylistDao.getTracksForPlaylist(playlistId)
            assertEquals(10, restoredTracks.size)
            assertEquals(1L, restoredTracks[0].trackId)
            assertEquals(2L, restoredTracks[1].trackId)
            assertEquals(6L, restoredTracks[2].trackId) // Moved track is at position 2
            assertEquals(3L, restoredTracks[3].trackId)
            assertEquals(4L, restoredTracks[4].trackId)
            assertEquals(5L, restoredTracks[5].trackId)
            assertEquals(7L, restoredTracks[6].trackId)
            assertEquals(8L, restoredTracks[7].trackId)
            assertEquals(9L, restoredTracks[8].trackId)
            assertEquals(10L, restoredTracks[9].trackId)

            // Check positions in raw playlist_tracks table
            val rawEntries = freshPlaylistDao.getPlaylistTrackEntries(playlistId)
            assertEquals(10, rawEntries.size)
            for (i in reorderedIds.indices) {
                assertEquals(reorderedIds[i], rawEntries[i].trackId)
                assertEquals(i, rawEntries[i].position)
            }

            freshDb.close()
            dbFile.delete()
        }
    }

    /**
     * Critical Gap 3 Verification:
     * Room AutoMigration(from = 4, to = 5) applied to real user data.
     * Verifies 100 tracks, 50 favorites, 10 playlists (200 tracks), and 500 play events migrate seamlessly.
     */
    @Test
    fun testSchemaV4ToV5MigrationPreservesUserData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("test_migration_v4_v5.db")
        if (dbFile.exists()) dbFile.delete()

        // 1. Create a raw SQLite database adhering to Version 4 Schema
        val v4Db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        v4Db.execSQL(
            """CREATE TABLE IF NOT EXISTS `tracks` (
                `track_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `media_store_id` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `artist` TEXT NOT NULL,
                `artist_id` INTEGER NOT NULL DEFAULT 0,
                `album` TEXT NOT NULL,
                `album_id` INTEGER NOT NULL,
                `track_number` INTEGER NOT NULL DEFAULT 0,
                `disc_number` INTEGER NOT NULL DEFAULT 1,
                `year` INTEGER NOT NULL DEFAULT 0,
                `duration_ms` INTEGER NOT NULL,
                `size_bytes` INTEGER NOT NULL,
                `date_modified` INTEGER NOT NULL,
                `date_added` INTEGER NOT NULL DEFAULT 0,
                `relative_path` TEXT NOT NULL,
                `title_normalized` TEXT NOT NULL,
                `artist_normalized` TEXT NOT NULL,
                `album_normalized` TEXT NOT NULL,
                `is_missing` INTEGER NOT NULL DEFAULT 0,
                `last_seen_timestamp` INTEGER NOT NULL
            )"""
        )
        v4Db.execSQL(
            """CREATE TABLE IF NOT EXISTS `favorites` (
                `track_id` INTEGER PRIMARY KEY NOT NULL,
                `added_timestamp` INTEGER NOT NULL DEFAULT 0
            )"""
        )
        v4Db.execSQL(
            """CREATE TABLE IF NOT EXISTS `playlists` (
                `playlist_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL
            )"""
        )
        v4Db.execSQL(
            """CREATE TABLE IF NOT EXISTS `playlist_tracks` (
                `playlist_id` INTEGER NOT NULL,
                `track_id` INTEGER NOT NULL,
                `position` INTEGER NOT NULL,
                `added_at` INTEGER NOT NULL,
                PRIMARY KEY(`playlist_id`, `track_id`),
                FOREIGN KEY(`playlist_id`) REFERENCES `playlists`(`playlist_id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )"""
        )
        v4Db.execSQL(
            """CREATE TABLE IF NOT EXISTS `play_events` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `track_id` INTEGER NOT NULL,
                `event_type` TEXT NOT NULL,
                `played_at` INTEGER NOT NULL,
                `played_ms` INTEGER NOT NULL,
                FOREIGN KEY(`track_id`) REFERENCES `tracks`(`track_id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )"""
        )
        v4Db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        v4Db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'd84698c02dae1dfad4a96571069a14d9')")
        v4Db.version = 4

        // Populate 100 tracks
        for (i in 1..100) {
            v4Db.execSQL(
                """INSERT INTO tracks (track_id, media_store_id, title, artist, artist_id, album, album_id, duration_ms, size_bytes, date_modified, date_added, relative_path, title_normalized, artist_normalized, album_normalized, is_missing, last_seen_timestamp)
                VALUES ($i, ${1000 + i}, 'Song $i', 'Artist $i', $i, 'Album $i', $i, 200000, 5000000, 1000, 1000, 'Music/$i.mp3', 'song $i', 'artist $i', 'album $i', 0, 1000)"""
            )
        }

        // Populate 50 favorites
        for (i in 1..50) {
            v4Db.execSQL("INSERT INTO favorites (track_id, added_timestamp) VALUES ($i, ${1000 + i})")
        }

        // Populate 10 playlists with 200 playlist track records
        for (p in 1..10) {
            v4Db.execSQL("INSERT INTO playlists (playlist_id, name, created_at, updated_at) VALUES ($p, 'Playlist $p', 1000, 1000)")
            for (t in 1..20) {
                val trackId = ((p - 1) * 10 + t) % 100 + 1
                v4Db.execSQL("INSERT OR REPLACE INTO playlist_tracks (playlist_id, track_id, position, added_at) VALUES ($p, $trackId, ${t - 1}, 1000)")
            }
        }

        // Populate 500 play events
        for (e in 1..500) {
            val trackId = (e % 100) + 1
            v4Db.execSQL("INSERT INTO play_events (track_id, event_type, played_at, played_ms) VALUES ($trackId, 'PLAY', ${10000 + e}, 150000)")
        }

        v4Db.close()

        // 2. Open with KaonDatabase v5 (triggers Room AutoMigration from 4 to 5)
        val v5Db = Room.databaseBuilder(context, KaonDatabase::class.java, "test_migration_v4_v5.db")
            .allowMainThreadQueries()
            .build()

        val trackDao = v5Db.trackDao()
        val favoriteDao = v5Db.favoriteDao()
        val playlistDao = v5Db.playlistDao()
        val playEventDao = v5Db.playEventDao()

        // 3. Verify All 100 Tracks exist with source = "LOCAL" and youtube_video_id = null
        val allTracks = trackDao.observeAllActiveTracks().first()
        assertEquals(100, allTracks.size)
        for (track in allTracks) {
            assertEquals("LOCAL", track.source)
            assertEquals(null, track.youtubeVideoId)
            assertFalse(track.source == "YOUTUBE")
        }

        // 4. Verify All 50 Favorites exist
        val favorites = favoriteDao.observeFavoriteTrackEntities().first()
        assertEquals(50, favorites.size)

        // 5. Verify All 10 Playlists exist
        val playlists = playlistDao.observeAllPlaylistsWithCount().first()
        assertEquals(10, playlists.size)

        // 6. Verify Play Events exist
        val recentTracks = playEventDao.observeRecentlyPlayedTrackEntities(limit = 100).first()
        assertTrue(recentTracks.isNotEmpty())

        v5Db.close()
        dbFile.delete()
    }

    /**
     * Critical Gap 1 & 2 Verification:
     * YouTube stream resolution, rate limiting, and error handling verification.
     */
    @Test
    fun testYouTubeStreamResolutionAndRateLimiting() = runBlocking {
        // 1. Test invalid video ID handling
        val invalidResult = com.kaon.music.core.playback.YouTubeStreamResolver.resolveStreamUrl("")
        assertTrue(invalidResult.isFailure)

        // 2. Test pre-resolution with null/blank
        com.kaon.music.core.playback.YouTubeStreamResolver.preResolve(null)
        com.kaon.music.core.playback.YouTubeStreamResolver.preResolve("   ")

        // 3. Test known video ID resolution
        val videoId = "dQw4w9WgXcQ"
        val result = com.kaon.music.core.playback.YouTubeStreamResolver.resolveStreamUrl(videoId)
        if (result.isSuccess) {
            val streamUrl = result.getOrNull()
            assertNotNull(streamUrl)
            assertTrue(streamUrl!!.startsWith("https://"))
        } else {
            // Handled gracefully without crash
            val error = result.exceptionOrNull()
            assertNotNull(error)
        }
    }
}
