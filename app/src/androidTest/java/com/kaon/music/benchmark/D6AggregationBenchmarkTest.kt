package com.kaon.music.benchmark

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaon.music.core.data.db.KaonDatabase
import com.kaon.music.core.data.db.entity.PlayEventEntity
import com.kaon.music.core.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.util.Random
import kotlin.system.measureNanoTime

/**
 * Benchmark test for Milestone 4 Failure-Mode Matrix Row 28 (D6 Aggregation Trigger Check).
 *
 * Requirements:
 * - 50,000 synthetic play_event rows distributed across 500 distinct trackIds
 * - Timestamps spread over 90 days, mixed event types (PLAY / SKIP)
 * - Measure cold query time and warm query time for Recently Played and Most Played queries
 * - Record exact timing numbers to evaluate against the D6 aggregation threshold (~200ms)
 */
@RunWith(AndroidJUnit4::class)
class D6AggregationBenchmarkTest {

    private lateinit var db: KaonDatabase
    private val random = Random(42) // Fixed seed for repeatability

    @Before
    fun setupDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KaonDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun benchmark50kPlayEventsAggregationQueries() = runBlocking {
        val trackDao = db.trackDao()
        val playEventDao = db.playEventDao()

        val trackCount = 500
        val eventCount = 50_000
        val now = System.currentTimeMillis()
        val ninetyDaysMs = 90L * 24 * 3600 * 1000L

        Timber.d("=== Row 28 Benchmark: Seeding $trackCount tracks and $eventCount play events ===")

        // 1. Seed 500 tracks in chunks
        val tracks = (1..trackCount).map { i ->
            TrackEntity(
                trackId = i.toLong(),
                mediaStoreId = 10000L + i,
                title = "Track Number $i",
                artist = "Artist ${i % 30}",
                artistId = (i % 30).toLong(),
                album = "Album ${i % 50}",
                albumId = (i % 50).toLong(),
                trackNumber = i % 12 + 1,
                discNumber = 1,
                year = 2000 + (i % 24),
                durationMs = 180000L + (i % 120) * 1000L,
                sizeBytes = 4000000L + (i % 120) * 50000L,
                dateModified = now - (i * 3600000L),
                dateAdded = now - (i * 7200000L),
                relativePath = "Music/Artist ${i % 30}/Track_$i.mp3",
                titleNormalized = "track number $i",
                artistNormalized = "artist ${i % 30}",
                albumNormalized = "album ${i % 50}",
                isMissing = false,
                lastSeenTimestamp = now
            )
        }
        trackDao.insertTracks(tracks)
        assertEquals(trackCount, trackDao.getAllStoredTracks().size)

        // 2. Seed 50,000 play events in chunks (batch size 1,000 for fast insertion)
        val batchSize = 1000
        var insertedEvents = 0
        for (batch in 0 until (eventCount / batchSize)) {
            db.runInTransaction {
                for (j in 0 until batchSize) {
                    val trackId = (random.nextInt(trackCount) + 1).toLong()
                    val isPlay = random.nextDouble() < 0.85 // 85% play, 15% skip
                    val eventType = if (isPlay) "PLAY" else "SKIP"
                    val timestamp = now - (random.nextDouble() * ninetyDaysMs).toLong()
                    val playedMs = if (isPlay) 30000L + random.nextInt(200000) else random.nextInt(15000).toLong()

                    db.openHelper.writableDatabase.execSQL(
                        "INSERT INTO play_events (track_id, event_type, played_at, played_ms) VALUES (?, ?, ?, ?)",
                        arrayOf(trackId, eventType, timestamp, playedMs)
                    )
                }
            }
            insertedEvents += batchSize
        }
        assertEquals(eventCount, insertedEvents)

        // =========================================================================
        // Benchmark 1: Recently Played Query (MAX(played_at) GROUP BY track_id JOIN tracks)
        // =========================================================================
        // Cold Run
        val coldRecentNanos = measureNanoTime {
            val coldResult = playEventDao.getRecentlyPlayedTrackEntities(100)
            assertNotNull(coldResult)
            assertTrue(coldResult.isNotEmpty())
        }
        val coldRecentMs = coldRecentNanos / 1_000_000.0

        // Warm Runs (10 iterations)
        val warmRecentTimes = mutableListOf<Double>()
        repeat(10) {
            val nanos = measureNanoTime {
                val result = playEventDao.getRecentlyPlayedTrackEntities(100)
                assertEquals(100, result.size)
            }
            warmRecentTimes.add(nanos / 1_000_000.0)
        }
        val avgWarmRecentMs = warmRecentTimes.average()
        val minWarmRecentMs = warmRecentTimes.minOrNull() ?: 0.0
        val maxWarmRecentMs = warmRecentTimes.maxOrNull() ?: 0.0

        // =========================================================================
        // Benchmark 2: Most Played Query (COUNT(*) GROUP BY track_id JOIN tracks)
        // =========================================================================
        // Cold Run
        val coldMostNanos = measureNanoTime {
            val coldResult = playEventDao.getMostPlayedTrackEntities(100)
            assertNotNull(coldResult)
            assertTrue(coldResult.isNotEmpty())
        }
        val coldMostMs = coldMostNanos / 1_000_000.0

        // Warm Runs (10 iterations)
        val warmMostTimes = mutableListOf<Double>()
        repeat(10) {
            val nanos = measureNanoTime {
                val result = playEventDao.getMostPlayedTrackEntities(100)
                assertEquals(100, result.size)
            }
            warmMostTimes.add(nanos / 1_000_000.0)
        }
        val avgWarmMostMs = warmMostTimes.average()
        val minWarmMostMs = warmMostTimes.minOrNull() ?: 0.0
        val maxWarmMostMs = warmMostTimes.maxOrNull() ?: 0.0

        // =========================================================================
        // Benchmark 3: Flow Observation Emission Time
        // =========================================================================
        val flowRecentNanos = measureNanoTime {
            val flowResult = playEventDao.observeRecentlyPlayedTrackEntities(100).first()
            assertEquals(100, flowResult.size)
        }
        val flowRecentMs = flowRecentNanos / 1_000_000.0

        val flowMostNanos = measureNanoTime {
            val flowResult = playEventDao.observeMostPlayedTrackEntities(100).first()
            assertEquals(100, flowResult.size)
        }
        val flowMostMs = flowMostNanos / 1_000_000.0

        // =========================================================================
        // Report Results
        // =========================================================================
        val report = buildString {
            appendLine("========================================================================")
            appendLine("   ROW 28 BENCHMARK RESULTS: 50,000 SYNTHETIC PLAY EVENTS (500 TRACKS)   ")
            appendLine("========================================================================")
            appendLine("Target Database: Android SQLite Room Engine (In-Memory)")
            appendLine("Event Count: $eventCount rows across $trackCount tracks over 90 days")
            appendLine("D6 Trigger Latency Threshold: 200.0 ms")
            appendLine("------------------------------------------------------------------------")
            appendLine("1. RECENTLY PLAYED QUERY (MAX(played_at) GROUP BY track_id):")
            appendLine("   - Cold execution: %.2f ms".format(coldRecentMs))
            appendLine("   - Warm average:   %.2f ms (min: %.2f ms, max: %.2f ms)".format(avgWarmRecentMs, minWarmRecentMs, maxWarmRecentMs))
            appendLine("   - Flow emission:  %.2f ms".format(flowRecentMs))
            appendLine("------------------------------------------------------------------------")
            appendLine("2. MOST PLAYED QUERY (COUNT(*) GROUP BY track_id):")
            appendLine("   - Cold execution: %.2f ms".format(coldMostMs))
            appendLine("   - Warm average:   %.2f ms (min: %.2f ms, max: %.2f ms)".format(avgWarmMostMs, minWarmMostMs, maxWarmMostMs))
            appendLine("   - Flow emission:  %.2f ms".format(flowMostMs))
            appendLine("------------------------------------------------------------------------")
            val maxObservedMs = maxOf(coldRecentMs, coldMostMs, avgWarmRecentMs, avgWarmMostMs)
            if (maxObservedMs < 200.0) {
                appendLine("CONCLUSION: D6 TRIGGER NOT ACTIVATED (Max observed latency: %.2f ms << 200ms threshold).".format(maxObservedMs))
                appendLine("No aggregation table or caching layer needed; pure SQLite query model is fully validated.")
            } else {
                appendLine("CONCLUSION: D6 TRIGGER ACTIVATED (Query exceeded 200ms threshold).")
            }
            appendLine("========================================================================")
        }

        println(report)
        Timber.i(report)

        // Regression assertion: Neither query may exceed the D6 threshold of 200ms
        assertTrue("Cold Recently Played query too slow: ${coldRecentMs}ms", coldRecentMs < 200.0)
        assertTrue("Cold Most Played query too slow: ${coldMostMs}ms", coldMostMs < 200.0)
        assertTrue("Warm Recently Played avg too slow: ${avgWarmRecentMs}ms", avgWarmRecentMs < 200.0)
        assertTrue("Warm Most Played avg too slow: ${avgWarmMostMs}ms", avgWarmMostMs < 200.0)
    }
}
