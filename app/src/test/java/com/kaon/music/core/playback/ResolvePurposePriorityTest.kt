package com.kaon.music.core.playback

import android.net.ConnectivityManager
import com.kaon.music.core.extraction.model.AudioQuality
import com.kaon.music.core.extraction.model.AudioType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Foreground priority for stream resolution.
 *
 * WAVE reached the same contention — a background pre-resolve competing with the track the user just
 * tapped — and resolved it by disabling pre-resolution entirely
 * (`media_kit_music_player_service.dart:_preloadNext` returns immediately). Kaon keeps pre-resolution
 * and bounds it instead; these tests pin that bound, because the failure mode is invisible in normal
 * use and only shows up as an occasional slow or rate-limited track start.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResolvePurposePriorityTest {

    private var now = 1_000_000_000L

    private class FakeExtractionApi(
        var behavior: suspend () -> ResolvedStreamData
    ) : YouTubeStreamResolver.StreamExtractionApi {
        var calls = 0
        override suspend fun extract(
            videoId: String,
            quality: AudioQuality,
            audioType: AudioType,
            connectivityManager: ConnectivityManager?
        ): ResolvedStreamData {
            calls++
            return behavior()
        }
    }

    private fun streamData(url: String = "https://cdn.example/audio", clientName: String = "VISIONOS") =
        ResolvedStreamData(
            url = url,
            headers = mapOf("User-Agent" to "test-agent"),
            clientName = clientName,
            expiresInSeconds = 300
        )

    @Before
    fun setUp() {
        YouTubeStreamResolver.resetForTesting(clockFn = { now })
    }

    @After
    fun tearDown() {
        YouTubeStreamResolver.resetForTesting()
    }

    @Test
    fun `prefetch is skipped while a foreground resolution is in flight`() = runBlocking {
        val entered = CountDownLatch(1)
        val gate = CompletableDeferred<ResolvedStreamData>()
        val extraction = FakeExtractionApi {
            entered.countDown()
            gate.await()
        }
        YouTubeStreamResolver.resetForTesting(extraction = extraction, clockFn = { now })

        val playback = launch(Dispatchers.IO) {
            YouTubeStreamResolver.resolveStreamData("foreground", purpose = ResolvePurpose.PLAYBACK)
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))

        // A different track, so it cannot coalesce onto the in-flight resolution — it would be a
        // second concurrent extraction competing for the same network stack and rate-limit window.
        YouTubeStreamResolver.preResolve("background")
        assertEquals("prefetch must not start while playback is resolving", 1, extraction.calls)
        assertEquals(1L, ResolutionMetrics.count(ResolutionMetrics.PREFETCH_SKIPPED_FOREGROUND))

        gate.complete(streamData())
        playback.join()
    }

    @Test
    fun `prefetch proceeds when nothing is in flight`() = runTest {
        val extraction = FakeExtractionApi { streamData() }
        YouTubeStreamResolver.resetForTesting(extraction = extraction, clockFn = { now })

        YouTubeStreamResolver.preResolve("vid")

        assertEquals(1, extraction.calls)
    }

    @Test
    fun `prefetch yields the rate limit window to foreground playback`() = runTest {
        val extraction = FakeExtractionApi { streamData() }
        // Window of 9 leaves 1 slot after 8 acquisitions, below the reserved headroom.
        YouTubeStreamResolver.resetForTesting(extraction = extraction, clockFn = { now }, rateLimiterMax = 9)

        repeat(8) { index ->
            assertTrue(YouTubeStreamResolver.resolveStreamData("fg$index").isSuccess)
        }
        assertEquals(8, extraction.calls)

        YouTubeStreamResolver.preResolve("speculative")
        assertEquals("prefetch must leave quota for playback", 8, extraction.calls)
        assertEquals(1L, ResolutionMetrics.count(ResolutionMetrics.PREFETCH_SKIPPED_HEADROOM))

        // The remaining slot is still available to a foreground request, which is the point.
        assertTrue(YouTubeStreamResolver.resolveStreamData("fg-last").isSuccess)
        assertEquals(9, extraction.calls)
    }

    @Test
    fun `a warm entry from a prefetch is reused by playback without re-extracting`() = runTest {
        val extraction = FakeExtractionApi { streamData() }
        YouTubeStreamResolver.resetForTesting(extraction = extraction, clockFn = { now })

        YouTubeStreamResolver.preResolve("vid", quality = AudioQuality.HIGH)
        assertEquals(1, extraction.calls)
        assertEquals(1L, ResolutionMetrics.count(ResolutionMetrics.EXTRACTED))

        // Same options, so the key matches and the prefetch actually pays off.
        val played = YouTubeStreamResolver.resolveStreamData("vid", AudioQuality.HIGH)

        assertTrue(played.isSuccess)
        assertEquals("playback must reuse the prefetched entry", 1, extraction.calls)
        assertEquals(1L, ResolutionMetrics.count(ResolutionMetrics.CACHE_HIT))
    }

    @Test
    fun `prefetch on an already-warm track records the warm outcome without extracting`() = runTest {
        val extraction = FakeExtractionApi { streamData() }
        YouTubeStreamResolver.resetForTesting(extraction = extraction, clockFn = { now })

        assertTrue(YouTubeStreamResolver.resolveStreamData("vid").isSuccess)
        YouTubeStreamResolver.preResolve("vid")

        assertEquals(1, extraction.calls)
        assertEquals(1L, ResolutionMetrics.count(ResolutionMetrics.PREFETCH_WARM))
    }

    @Test
    fun `prefetch under the wifi-only gate is skipped on metered`() = runTest {
        val extraction = FakeExtractionApi { streamData() }
        YouTubeStreamResolver.resetForTesting(extraction = extraction, clockFn = { now })
        YouTubeStreamResolver.activeNetworkUnmetered = { false }

        YouTubeStreamResolver.preResolve("vid", wifiOnly = true)

        assertEquals("a gated prefetch must not perform the streaming it would warm for", 0, extraction.calls)
        assertEquals(1L, ResolutionMetrics.count(ResolutionMetrics.PREFETCH_SKIPPED_WIFI_GATE))
    }

    @Test
    fun `prefetch failure is counted separately from extraction success`() = runTest {
        val extraction = FakeExtractionApi { throw java.io.IOException("offline") }
        YouTubeStreamResolver.resetForTesting(extraction = extraction, clockFn = { now })

        YouTubeStreamResolver.preResolve("vid")

        assertEquals(1L, ResolutionMetrics.count(ResolutionMetrics.PREFETCH_FAILED))
    }

    @Test
    fun `a concurrent prefetch and playback for the same track share one extraction`() = runBlocking {
        val entered = CountDownLatch(1)
        val gate = CompletableDeferred<ResolvedStreamData>()
        val extraction = FakeExtractionApi {
            entered.countDown()
            gate.await()
        }
        YouTubeStreamResolver.resetForTesting(extraction = extraction, clockFn = { now })

        val prefetch = launch(Dispatchers.IO) { YouTubeStreamResolver.preResolve("vid") }
        assertTrue(entered.await(5, TimeUnit.SECONDS))

        val playback = CompletableDeferred<Result<ResolvedStreamData>>()
        launch(Dispatchers.IO) { playback.complete(YouTubeStreamResolver.resolveStreamData("vid")) }
        Thread.sleep(150) // let the playback caller register on the in-flight request

        gate.complete(streamData())
        withTimeout(10_000) { assertTrue(playback.await().isSuccess) }
        prefetch.join()

        assertEquals("purpose must not fragment the coalescing key", 1, extraction.calls)
    }

    @Test
    fun `metrics distinguish outcomes`() = runTest {
        val extraction = FakeExtractionApi { streamData() }
        YouTubeStreamResolver.resetForTesting(extraction = extraction, clockFn = { now })

        assertTrue(YouTubeStreamResolver.resolveStreamData("vid").isSuccess)
        assertTrue(YouTubeStreamResolver.resolveStreamData("vid").isSuccess) // cache

        extraction.behavior = { throw IllegalArgumentException("gone") }
        assertTrue(YouTubeStreamResolver.resolveStreamData("other").isFailure)

        assertEquals(1L, ResolutionMetrics.count(ResolutionMetrics.EXTRACTED))
        assertEquals(1L, ResolutionMetrics.count(ResolutionMetrics.CACHE_HIT))
        assertEquals(1L, ResolutionMetrics.count(ResolutionMetrics.FAILED))
        assertTrue(ResolutionMetrics.summary().contains("extracted="))
    }

    @Test
    fun `retry outcome is reported separately from a first-attempt success`() = runTest {
        var attempt = 0
        val extraction = FakeExtractionApi {
            attempt++
            if (attempt == 1) throw java.io.IOException("reset") else streamData()
        }
        YouTubeStreamResolver.resetForTesting(extraction = extraction, clockFn = { now })

        assertTrue(YouTubeStreamResolver.resolveStreamData("vid").isSuccess)

        assertEquals(1L, ResolutionMetrics.count(ResolutionMetrics.EXTRACTED_RETRY))
        assertEquals(0L, ResolutionMetrics.count(ResolutionMetrics.EXTRACTED))
    }
}
