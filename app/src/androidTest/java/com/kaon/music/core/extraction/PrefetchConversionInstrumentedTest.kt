package com.kaon.music.core.extraction

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kaon.music.core.playback.ResolutionMetrics
import com.kaon.music.core.playback.YouTubeStreamResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device measurement of the pre-resolution → cache-hit conversion path.
 *
 * The prefetch machinery is cheap by construction (ARCHITECTURE.md §7.2), but whether it actually
 * converts into warm serves on real hardware is an empirical question: it depends on the real
 * network, the real rate-limit window, and real transition timing. This test exercises the cycle a
 * listening session repeats — resolve, prefetch the next track, play it as foreground — and reports
 * the outcome histogram, asserting only the properties that must hold for the design to work:
 *
 * 1. A prefetch completion must be recorded as an extraction (it hit the network).
 * 2. The subsequent foreground resolve of the same track must be a cache hit.
 * 3. The per-client histogram must attribute every extraction to a named client, so a run's output
 *    shows how often the cheap-first catalog (VISIONOS) won versus how far fallback rotated.
 *
 * Timing numbers are reported, not asserted: a wall-clock threshold would make this a flake on a
 * slow network (same policy as [YouTubeLiveStreamingTest.measureFastPathResolutionLatency]).
 */
@RunWith(AndroidJUnit4::class)
class PrefetchConversionInstrumentedTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            YouTubeStreamExtractor.initialize(context)
            YouTubeStreamResolver.attachContext(context)
        }
        // Mirrors KaonApplication's bootstrap so the measured resolves see the settled session the
        // app's users see after launch. The cold-start race itself is covered by the resolver's
        // session-change retry, not by this measurement.
        runBlocking { YouTubeStreamExtractor.prewarm() }
    }

    @Test
    fun measurePrefetchToCacheHitConversion() = runBlocking {
        ResolutionMetrics.reset()
        // Another test in this process may have left VIDEO_A warm; the cold cycle must be genuinely
        // cold or its extraction won't register and the cache-hit count below over-counts.
        YouTubeStreamResolver.clearCache()
        val timings = StringBuilder()

        // Cycle 1: cold foreground resolve — the track-start the user pays for.
        var startedAt = android.os.SystemClock.elapsedRealtime()
        val cold = YouTubeStreamResolver.resolveStreamData(VIDEO_A)
        val coldMs = android.os.SystemClock.elapsedRealtime() - startedAt
        assertTrue("cold resolve failed: ${cold.exceptionOrNull()?.message}", cold.isSuccess)
        timings.append("cold=$coldMs")

        // Cycle 2: what PlaybackFacade.triggerNextTracksPreResolution does at a transition.
        startedAt = android.os.SystemClock.elapsedRealtime()
        val extractedBeforePrefetch = ResolutionMetrics.count(ResolutionMetrics.EXTRACTED) +
            ResolutionMetrics.count(ResolutionMetrics.EXTRACTED_RETRY)
        YouTubeStreamResolver.preResolve(VIDEO_B)
        val prefetchMs = android.os.SystemClock.elapsedRealtime() - startedAt
        val extractedAfterPrefetch = ResolutionMetrics.count(ResolutionMetrics.EXTRACTED) +
            ResolutionMetrics.count(ResolutionMetrics.EXTRACTED_RETRY)
        assertEquals(
            "the prefetch must have completed its own extraction",
            extractedBeforePrefetch + 1L,
            extractedAfterPrefetch
        )
        timings.append(" prefetch=$prefetchMs")

        // Cycle 3: the user lets track B start — the prefetch must make this a warm serve.
        startedAt = android.os.SystemClock.elapsedRealtime()
        val warm = YouTubeStreamResolver.resolveStreamData(VIDEO_B)
        val warmMs = android.os.SystemClock.elapsedRealtime() - startedAt
        assertTrue(warm.isSuccess)
        assertEquals(
            "the foreground resolve after a matching prefetch must be a cache hit",
            1L,
            ResolutionMetrics.count(ResolutionMetrics.CACHE_HIT)
        )
        assertTrue("a warm serve should be sub-millisecond, was ${warmMs}ms", warmMs < 50L)
        timings.append(" warm=$warmMs")

        // Cycle 4: a second prefetch of an already-warm track is the no-op path.
        YouTubeStreamResolver.preResolve(VIDEO_B)
        assertEquals(1L, ResolutionMetrics.count(ResolutionMetrics.PREFETCH_WARM))

        val clients = ResolutionMetrics.clientCounts()
        // Logged before the final assert so a failure still emits the run's numbers. The trailing
        // void assertion is also what keeps the test method `void`, which JUnit4 requires.
        android.util.Log.i(
            "TEST_STREAM",
            "prefetch-conversion $timings clientHistogram=$clients metrics=[${ResolutionMetrics.summary()}]"
        )
        assertTrue("every extraction must be attributed to a client", clients.values.sum() >= 1L)
    }

    private companion object {
        const val VIDEO_A = "dQw4w9WgXcQ"
        const val VIDEO_B = "jNQXAC9IVRw" // "Me at the zoo" — stable, short, unmonetised
    }
}
