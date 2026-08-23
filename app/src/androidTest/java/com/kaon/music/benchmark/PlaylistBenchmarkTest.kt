package com.kaon.music.benchmark

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaon.music.core.data.db.KaonDatabase
import com.kaon.music.core.data.db.entity.PlaylistEntity
import com.kaon.music.core.data.db.entity.PlaylistTrackEntity
import com.kaon.music.core.data.db.entity.TrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import kotlin.system.measureNanoTime

@RunWith(AndroidJUnit4::class)
class PlaylistBenchmarkTest {

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

    @Test
    fun benchmark1000TrackPlaylistLoadAndReorder() {
        runBlocking {
            val trackDao = db.trackDao()
        val playlistDao = db.playlistDao()

        val trackCount = 1000

        // 1. Insert 1,000 synthetic tracks
        val tracks = (1..trackCount).map { i ->
            TrackEntity(
                trackId = i.toLong(),
                mediaStoreId = 10000L + i,
                title = "Synthetic Track #$i",
                artist = "Artist ${i % 50}",
                album = "Album ${i % 20}",
                albumId = (i % 20).toLong() + 1L,
                durationMs = 180000L + (i * 100),
                sizeBytes = 4000000L,
                dateModified = 1000L,
                dateAdded = 1000L,
                relativePath = "Music/track_$i.mp3",
                titleNormalized = "synthetic track #$i",
                artistNormalized = "artist ${i % 50}",
                albumNormalized = "album ${i % 20}",
                isMissing = false,
                lastSeenTimestamp = System.currentTimeMillis()
            )
        }
        trackDao.insertTracks(tracks)

        // 2. Create playlist and populate 1,000 tracks
        val playlistId = playlistDao.insertPlaylist(
            PlaylistEntity(name = "Mega 1000 Tracks Playlist")
        )

        val entries = (0 until trackCount).map { pos ->
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = (pos + 1).toLong(),
                position = pos,
                addedAt = 1000L + pos
            )
        }
        playlistDao.addTracksToPlaylist(entries)

        // 3. Measure Cold Load Query Latency (1,000 tracks JOIN with tracks table)
        val coldQueryNs = measureNanoTime {
            val loaded = playlistDao.getTracksForPlaylist(playlistId)
            assertEquals(1000, loaded.size)
        }
        val coldQueryMs = coldQueryNs / 1_000_000.0

        // 4. Measure Warm Load Query Latency
        val warmQueryNs = measureNanoTime {
            val loaded = playlistDao.getTracksForPlaylist(playlistId)
            assertEquals(1000, loaded.size)
        }
        val warmQueryMs = warmQueryNs / 1_000_000.0

        // 5. Measure Reordering 1,000-track playlist in @Transaction (reversed order)
        val reversedEntries = (0 until trackCount).map { newPos ->
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = (trackCount - newPos).toLong(),
                position = newPos,
                addedAt = 1000L
            )
        }

        val reorderNs = measureNanoTime {
            playlistDao.updateTrackPositions(reversedEntries)
        }
        val reorderMs = reorderNs / 1_000_000.0

        // 6. Verify correct reordered positions
        val reorderedTracks = playlistDao.getTracksForPlaylist(playlistId)
        assertEquals(1000, reorderedTracks.size)
        assertEquals(1000L, reorderedTracks.first().trackId)
        assertEquals(1L, reorderedTracks.last().trackId)

        Timber.tag("PlaylistBenchmark").i("1,000 Tracks Benchmark: Cold Query=%.2f ms, Warm Query=%.2f ms, 1,000 Item Reorder=%.2f ms",
            coldQueryMs, warmQueryMs, reorderMs)
        println(">>> [1,000-Track Benchmark] Cold Query: ${coldQueryMs}ms | Warm Query: ${warmQueryMs}ms | 1000-Track Reorder Transaction: ${reorderMs}ms")

        // Assert performance thresholds:
        // Querying 1,000 items should be under 50ms
        assertTrue("Cold query ($coldQueryMs ms) must be < 100ms", coldQueryMs < 100.0)
        // Reordering 1,000 items via @Transaction should be under 100ms
        assertTrue("1,000 item reorder ($reorderMs ms) must be < 100ms", reorderMs < 100.0)
        }
    }
}
