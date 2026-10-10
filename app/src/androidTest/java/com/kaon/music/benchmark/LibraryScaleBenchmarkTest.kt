package com.kaon.music.benchmark

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaon.music.core.data.db.KaonDatabase
import com.kaon.music.core.data.db.entity.FavoriteTrackEntity
import com.kaon.music.core.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * Library scale benchmark: 1k, 10k, and 50k tracks.
 *
 * The success criterion for Phase 3 is not a millisecond target but a shape: first-page cost must be
 * roughly flat as the library grows, because the query is bounded and index-backed. A benchmark that
 * asserted absolute times would be a flake across devices, so the assertions here are structural —
 * the page is the requested size, and the 50k first page is not disproportionately slower than the
 * 1k one — with the measured numbers logged for the record.
 *
 * The ratio bound is deliberately loose (12x for a 50x data increase). It fails on a regression to
 * "materialise, then sort in Kotlin", which is linear in library size, and tolerates the real device
 * noise of an in-memory SQLite benchmark.
 */
@RunWith(AndroidJUnit4::class)
class LibraryScaleBenchmarkTest {

    private lateinit var db: KaonDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            KaonDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() = db.close()

    private fun tracks(count: Int): List<TrackEntity> = (1..count).map { i ->
        val title = "Track %06d".format(i)
        TrackEntity(
            trackId = i.toLong(),
            mediaStoreId = 100_000L + i,
            title = title,
            artist = "Artist ${i % 500}",
            artistId = (i % 500).toLong(),
            album = "Album ${i % 2_000}",
            albumId = (i % 2_000).toLong(),
            trackNumber = i % 20,
            discNumber = 1,
            year = 2000 + (i % 25),
            durationMs = 180_000L + i,
            sizeBytes = 4_000_000L,
            dateModified = i.toLong(),
            dateAdded = i.toLong(),
            relativePath = "Music/$title.mp3",
            titleNormalized = title.lowercase(),
            artistNormalized = "artist ${i % 500}",
            albumNormalized = "album ${i % 2_000}",
            isMissing = false
        )
    }

    private suspend fun <V : Any> PagingSource<Int, V>.firstPage(size: Int): List<V> {
        val result = load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = size, placeholdersEnabled = false)
        )
        return (result as PagingSource.LoadResult.Page).data
    }

    private data class Measurement(
        val libraryTracks: Int,
        val insertMs: Long,
        val firstPageMs: Long,
        val searchPageMs: Long,
        val sortSwitchMs: Long,
        val playCountPageMs: Long,
        val countMs: Long,
        val favoriteToggleMs: Long,
        val queueBuildMs: Long
    ) {
        override fun toString(): String =
            "n=$libraryTracks insert=${insertMs}ms firstPage=${firstPageMs}ms search=${searchPageMs}ms " +
                "sortSwitch=${sortSwitchMs}ms playCountSort=${playCountPageMs}ms count=${countMs}ms " +
                "favToggle=${favoriteToggleMs}ms queue2000=${queueBuildMs}ms"
    }

    private fun measure(libraryTracks: Int): Measurement = runBlocking {
        val dao = db.trackDao()
        val insertMs = measureTimeMillis {
            tracks(libraryTracks).chunked(5_000).forEach { dao.insertTracks(it) }
        }

        lateinit var firstPage: List<*>
        val firstPageMs = measureTimeMillis {
            firstPage = dao.pagingTracksByTitle("").firstPage(PAGE_SIZE)
        }
        assertEquals(PAGE_SIZE, firstPage.size)

        val searchPageMs = measureTimeMillis {
            dao.pagingTracksByTitle("track 0001").firstPage(PAGE_SIZE)
        }
        val sortSwitchMs = measureTimeMillis {
            dao.pagingTracksByDateAdded("").firstPage(PAGE_SIZE)
        }
        val playCountPageMs = measureTimeMillis {
            dao.pagingTracksByPlayCount("").firstPage(PAGE_SIZE)
        }
        val countMs = measureTimeMillis { dao.observeActiveTrackCount("").first() }
        val favoriteToggleMs = measureTimeMillis {
            db.favoriteDao().addFavorite(FavoriteTrackEntity(trackId = 1L))
            dao.pagingFavoriteTracks("").firstPage(PAGE_SIZE)
        }
        val queueBuildMs = measureTimeMillis {
            dao.getActiveTrackIdsByTitle("", limit = 2_000)
        }

        Measurement(
            libraryTracks = libraryTracks,
            insertMs = insertMs,
            firstPageMs = firstPageMs,
            searchPageMs = searchPageMs,
            sortSwitchMs = sortSwitchMs,
            playCountPageMs = playCountPageMs,
            countMs = countMs,
            favoriteToggleMs = favoriteToggleMs,
            queueBuildMs = queueBuildMs
        ).also { android.util.Log.i(TAG, "library-scale $it") }
    }

    @Test
    fun benchmarkLibraryScale1kTo50k() {
        val small = measure(1_000)
        // The same in-memory database grows, so each step measures a larger table than the last.
        val medium = measure(10_000)
        val large = measure(50_000)

        android.util.Log.i(TAG, "library-scale summary: $small | $medium | $large")

        // Structural assertion: first-page cost must not scale with the library.
        val ratio = if (small.firstPageMs <= 0L) {
            large.firstPageMs.toDouble()
        } else {
            large.firstPageMs.toDouble() / small.firstPageMs.toDouble()
        }
        assertTrue(
            "first-page cost grew with library size (1k=${small.firstPageMs}ms, 50k=${large.firstPageMs}ms)",
            ratio <= MAX_FIRST_PAGE_RATIO
        )
        // A bounded queue read must stay bounded regardless of table size.
        assertTrue(
            "bounded queue read grew unexpectedly (${large.queueBuildMs}ms at 50k)",
            large.queueBuildMs < MAX_QUEUE_BUILD_MS
        )
    }

    private companion object {
        const val TAG = "KaonBenchmark"
        const val PAGE_SIZE = 50

        /** 50x the data; anything approaching linear growth fails. */
        const val MAX_FIRST_PAGE_RATIO = 12.0

        /** 2,000 ids from a 50k table via an index-backed LIMIT. */
        const val MAX_QUEUE_BUILD_MS = 2_000L
    }
}
