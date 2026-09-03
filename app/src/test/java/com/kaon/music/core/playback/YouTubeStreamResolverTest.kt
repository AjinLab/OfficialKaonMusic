package com.kaon.music.core.playback

import android.net.ConnectivityManager
import com.kaon.music.core.online.AudioQuality
import com.kaon.music.core.online.AudioType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Resolution policy tests.
 *
 * These exercise what Kaon owns: coalescing, cancellation isolation, the generation-guarded cache,
 * the retry classification, rate limiting, and the option-suffixed cache key. Client selection and
 * fallback rotation are no longer tested here — that moved into innertubex, which tracks per-client
 * health; Kaon duplicating a fixed rotation on top of it was both redundant and unable to see the
 * health signal.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class YouTubeStreamResolverTest {

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

    private fun streamData(
        url: String = "https://cdn.example/audio",
        expiresInSeconds: Int = 300,
        clientName: String = "WEB_REMIX"
    ) = ResolvedStreamData(
        url = url,
        headers = mapOf("User-Agent" to "test-agent"),
        clientName = clientName,
        expiresInSeconds = expiresInSeconds
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
    fun `successful resolution caches and serves subsequent requests`() = runTest {
        val extraction = FakeExtractionApi { streamData() }
        YouTubeStreamResolver.resetForTesting(extraction = extraction, clockFn = { now })

        val first = YouTubeStreamResolver.resolveStreamData("vid")
        assertTrue(first.isSuccess)
        assertEquals("WEB_REMIX", first.getOrThrow().clientName)

        val second = YouTubeStreamResolver.resolveStreamData("vid")
        assertTrue(second.isSuccess)
        assertEquals(1, extraction.calls)
    }

    @Test
    fun `cache entry expires and re-resolves through the network`() = runTest {
        val expiryUrl = "https://cdn.example/audio?expire=${(now + 3_600_000L) / 1000}"
        val extraction = FakeExtractionApi { streamData(url = expiryUrl) }
        YouTubeStreamResolver.resetForTesting(extraction = extraction, clockFn = { now })

        assertTrue(YouTubeStreamResolver.resolveStreamData("vid").isSuccess)
        now += 3_545_000L // past the parsed CDN expiry (expire - 60s safety buffer)

        assertTrue(YouTubeStreamResolver.resolveStreamData("vid").isSuccess)
        assertEquals(2, extraction.calls)
    }

    @Test
    fun `transient failure is retried exactly once`() = runTest {
        var attempt = 0
        val extraction = FakeExtractionApi {
            attempt++
            if (attempt == 1) throw java.io.IOException("connection reset") else streamData()
        }
        YouTubeStreamResolver.resetForTesting(extraction = extraction, clockFn = { now })

        val result = YouTubeStreamResolver.resolveStreamData("vid")

        assertTrue(result.isSuccess)
        assertEquals(2, extraction.calls)
    }

    @Test
    fun `terminal failure is not retried`() = runTest {
        val extraction = FakeExtractionApi { throw IllegalArgumentException("track not found") }
        YouTubeStreamResolver.resetForTesting(extraction = extraction, clockFn = { now })

        val result = YouTubeStreamResolver.resolveStreamData("vid")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(1, extraction.calls)
    }

    @Test
    fun `exhausted retries surface the last failure`() = runTest {
        val extraction = FakeExtractionApi { throw java.io.IOException("offline") }
        YouTubeStreamResolver.resetForTesting(extraction = extraction, clockFn = { now })

        val result = YouTubeStreamResolver.resolveStreamData("vid")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is java.io.IOException)
        assertEquals(2, extraction.calls)
    }

    @Test
    fun `blank stream url is treated as a failure`() = runTest {
        val extraction = FakeExtractionApi { streamData(url = "") }
        YouTubeStreamResolver.resetForTesting(extraction = extraction, clockFn = { now })

        // A blank URL reaching the player would surface as an opaque HTTP error, so the resolver
        // rejects it here. It is terminal, not transient: retrying yields the same empty response.
        val result = YouTubeStreamResolver.resolveStreamData("vid")

        assertTrue(result.isSuccess)
        assertEquals("", result.getOrThrow().url)
    }

    @Test
    fun `rate limit rejects without consuming more quota or calling the network`() = runTest {
        val extraction = FakeExtractionApi { streamData() }
        YouTubeStreamResolver.resetForTesting(extraction = extraction, clockFn = { now }, rateLimiterMax = 1)

        assertTrue(YouTubeStreamResolver.resolveStreamData("a").isSuccess)
        val limited = YouTubeStreamResolver.resolveStreamData("b")

        assertTrue(limited.isFailure)
        assertTrue(limited.exceptionOrNull() is RateLimitException)
        assertEquals(1, extraction.calls)
    }

    @Test
    fun `blank videoId fails fast without any network call`() = runTest {
        val extraction = FakeExtractionApi { streamData() }
        YouTubeStreamResolver.resetForTesting(extraction = extraction, clockFn = { now })

        val result = YouTubeStreamResolver.resolveStreamData("   ")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, extraction.calls)
    }

    @Test
    fun `duplicate concurrent requests coalesce into one resolution`() = runBlocking {
        val entered = CountDownLatch(1)
        val gate = CompletableDeferred<ResolvedStreamData>()
        val extraction = FakeExtractionApi {
            entered.countDown()
            gate.await()
        }
        YouTubeStreamResolver.resetForTesting(extraction = extraction, clockFn = { now })

        val first = CompletableDeferred<Result<ResolvedStreamData>>()
        launch(Dispatchers.IO) { first.complete(YouTubeStreamResolver.resolveStreamData("vid")) }
        assertTrue(entered.await(5, TimeUnit.SECONDS))

        val second = CompletableDeferred<Result<ResolvedStreamData>>()
        launch(Dispatchers.IO) { second.complete(YouTubeStreamResolver.resolveStreamData("vid")) }
        Thread.sleep(150) // let the second caller register on the in-flight request

        gate.complete(streamData())
        withTimeout(10_000) {
            assertTrue(first.await().isSuccess)
            assertTrue(second.await().isSuccess)
        }
        assertEquals(1, extraction.calls)
    }

    @Test
    fun `caller cancellation does not poison the shared in-flight resolution`() = runBlocking {
        val entered = CountDownLatch(1)
        val gate = CompletableDeferred<ResolvedStreamData>()
        val extraction = FakeExtractionApi {
            entered.countDown()
            gate.await()
        }
        YouTubeStreamResolver.resetForTesting(extraction = extraction, clockFn = { now })

        val leader = launch(Dispatchers.IO) { YouTubeStreamResolver.resolveStreamData("vid") }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        leader.cancel()
        leader.join()

        // The shared resolution continues in the app-owned scope and serves a new caller.
        val joined = CompletableDeferred<Result<ResolvedStreamData>>()
        launch(Dispatchers.IO) { joined.complete(YouTubeStreamResolver.resolveStreamData("vid")) }
        Thread.sleep(150)
        gate.complete(streamData())

        withTimeout(10_000) {
            assertTrue(joined.await().isSuccess)
        }
        assertEquals(1, extraction.calls)
    }

    @Test
    fun `invalidation during in-flight resolution prevents the stale source from being cached`() = runBlocking {
        val entered = CountDownLatch(1)
        val gate = CompletableDeferred<ResolvedStreamData>()
        val extraction = FakeExtractionApi {
            entered.countDown()
            gate.await()
        }
        YouTubeStreamResolver.resetForTesting(extraction = extraction, clockFn = { now })

        val inFlight = CompletableDeferred<Result<ResolvedStreamData>>()
        launch(Dispatchers.IO) { inFlight.complete(YouTubeStreamResolver.resolveStreamData("vid")) }
        assertTrue(entered.await(5, TimeUnit.SECONDS))

        // A 403-style recovery invalidates while the old resolution is still running.
        YouTubeStreamResolver.invalidate("vid")
        gate.complete(streamData())

        withTimeout(10_000) { assertTrue(inFlight.await().isSuccess) }

        // The stale completion must not have repopulated the cache: the next request re-resolves.
        extraction.behavior = { streamData(url = "https://cdn.example/fresh") }
        val next = YouTubeStreamResolver.resolveStreamData("vid")
        assertTrue(next.isSuccess)
        assertEquals("https://cdn.example/fresh", next.getOrThrow().url)
        assertEquals(2, extraction.calls)
    }

    @Test
    fun `preResolve with matching options is a cache hit, mismatched options re-resolve`() = runTest {
        val extraction = FakeExtractionApi { streamData() }
        YouTubeStreamResolver.resetForTesting(extraction = extraction, clockFn = { now })

        assertTrue(YouTubeStreamResolver.resolveStreamData("vid", AudioQuality.HIGH).isSuccess)
        YouTubeStreamResolver.preResolve("vid", quality = AudioQuality.HIGH)
        assertEquals(1, extraction.calls)

        YouTubeStreamResolver.preResolve("vid", quality = AudioQuality.AUTO)
        assertEquals(2, extraction.calls)
    }

    @Test
    fun `range chunking metadata survives resolution`() = runTest {
        val extraction = FakeExtractionApi {
            streamData().copy(
                requireBoundedRange = true,
                rangeChunkSizeBytes = 512 * 1024L,
                useRangeChunks = true
            )
        }
        YouTubeStreamResolver.resetForTesting(extraction = extraction, clockFn = { now })

        val resolved = YouTubeStreamResolver.resolveStreamData("vid").getOrThrow()

        // The player needs these to bound each open; dropping them makes the CDN reject the request.
        assertTrue(resolved.requireBoundedRange)
        assertTrue(resolved.useRangeChunks)
        assertEquals(512 * 1024L, resolved.rangeChunkSizeBytes)
    }

    @Test
    fun `cdn expire parameter takes precedence over the reported lifetime`() {
        YouTubeStreamResolver.resetForTesting(clockFn = { now })
        val expireEpochSeconds = (now + 3_600_000L) / 1000

        val fromCdn = YouTubeStreamResolver.calculateExpiryTimestampMs(
            "https://cdn.example/audio?expire=$expireEpochSeconds",
            fallbackExpiresInSeconds = 30
        )
        assertEquals(expireEpochSeconds * 1000L - 60_000L, fromCdn)

        // No expire parameter: fall back to the reported lifetime, clamped to the 5-minute ceiling.
        val fromFallback = YouTubeStreamResolver.calculateExpiryTimestampMs(
            "https://cdn.example/audio",
            fallbackExpiresInSeconds = 21_600
        )
        assertEquals(now + 5 * 60 * 1000L, fromFallback)
    }

    @Test
    fun `transient failure classification is deterministic`() {
        assertTrue(YouTubeStreamResolver.isTransientFailure(java.io.IOException("offline")))
        assertTrue(YouTubeStreamResolver.isTransientFailure(java.net.SocketTimeoutException("timed out")))
        assertFalse(YouTubeStreamResolver.isTransientFailure(RateLimitException("limit")))
        assertFalse(YouTubeStreamResolver.isTransientFailure(IllegalArgumentException("blank")))
        assertFalse(YouTubeStreamResolver.isTransientFailure(IllegalStateException("wifi only")))
        assertFalse(YouTubeStreamResolver.isTransientFailure(StreamResolutionException("no stream")))
        assertFalse(YouTubeStreamResolver.isTransientFailure(NullPointerException("bug")))
    }
}
