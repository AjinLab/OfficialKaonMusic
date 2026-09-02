package com.kaon.music.core.playback

import android.net.ConnectivityManager
import com.kaon.music.core.online.AudioQuality
import com.kaon.music.core.online.AudioType
import com.kaon.music.core.playback.YouTubeStreamResolver.FallbackStreamResult
import com.kaon.music.core.playback.YouTubeStreamResolver.PrimaryStreamResult
import com.metrolist.innertube.models.YouTubeClient
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

@OptIn(ExperimentalCoroutinesApi::class)
class YouTubeStreamResolverTest {

    private var now = 1_000_000_000L

    private class FakePrimaryApi(
        var behavior: suspend () -> PrimaryStreamResult
    ) : YouTubeStreamResolver.PrimaryStreamApi {
        var calls = 0
        override suspend fun resolve(
            videoId: String,
            quality: AudioQuality,
            audioType: AudioType,
            cm: ConnectivityManager?
        ): PrimaryStreamResult {
            calls++
            return behavior()
        }
    }

    private class FakeFallbackApi(
        var behavior: suspend (YouTubeClient) -> FallbackStreamResult
    ) : YouTubeStreamResolver.FallbackStreamApi {
        var calls = 0
        val calledClients = mutableListOf<String>()
        override fun clients(): List<YouTubeClient> = listOf(
            YouTubeClient.ANDROID_VR_1_65_10,
            YouTubeClient.VISIONOS,
            YouTubeClient.WEB_REMIX,
            YouTubeClient.TVHTML5
        )

        override suspend fun resolve(videoId: String, client: YouTubeClient): FallbackStreamResult {
            calls++
            calledClients.add(client.clientName)
            return behavior(client)
        }
    }

    private fun primaryResult(
        url: String = "https://cdn.example/audio",
        expiresInSeconds: Int = 300
    ) = PrimaryStreamResult(url, mapOf("User-Agent" to "test-agent"), "WEB_REMIX", expiresInSeconds)

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
        val primary = FakePrimaryApi { primaryResult() }
        YouTubeStreamResolver.resetForTesting(primary = primary, clockFn = { now })

        val first = YouTubeStreamResolver.resolveStreamData("vid")
        assertTrue(first.isSuccess)
        assertEquals("WEB_REMIX", first.getOrThrow().clientName)

        val second = YouTubeStreamResolver.resolveStreamData("vid")
        assertTrue(second.isSuccess)
        assertEquals(1, primary.calls)
    }

    @Test
    fun `cache entry expires and re-resolves through the network`() = runTest {
        val expiryUrl = "https://cdn.example/audio?expire=${(now + 3_600_000L) / 1000}"
        val primary = FakePrimaryApi { primaryResult(url = expiryUrl) }
        YouTubeStreamResolver.resetForTesting(primary = primary, clockFn = { now })

        assertTrue(YouTubeStreamResolver.resolveStreamData("vid").isSuccess)
        now += 3_545_000L // past the parsed CDN expiry (expire - 60s safety buffer)

        assertTrue(YouTubeStreamResolver.resolveStreamData("vid").isSuccess)
        assertEquals(2, primary.calls)
    }

    @Test
    fun `transient primary failure retries once then falls back deterministically`() = runTest {
        val primary = FakePrimaryApi { throw java.io.IOException("connection reset") }
        val fallback = FakeFallbackApi { client ->
            if (client.clientName == "VISIONOS") FallbackStreamResult("https://cdn.example/fallback", 300)
            else throw java.io.IOException("client unavailable")
        }
        YouTubeStreamResolver.resetForTesting(primary = primary, fallback = fallback, clockFn = { now })

        val result = YouTubeStreamResolver.resolveStreamData("vid")

        assertTrue(result.isSuccess)
        assertEquals("https://cdn.example/fallback", result.getOrThrow().url)
        assertEquals(2, primary.calls) // bounded transient retry: exactly one extra attempt
        assertEquals(
            listOf("ANDROID_VR", "VISIONOS"),
            fallback.calledClients
        )
        // Mobile/TV client headers must not carry browser Origin/Referer.
        val headers = result.getOrThrow().headers
        assertTrue(headers.containsKey("User-Agent"))
        assertFalse(headers.containsKey("Origin"))
        assertFalse(headers.containsKey("Referer"))
    }

    @Test
    fun `terminal primary failure is not retried but fallback still rotates`() = runTest {
        val primary = FakePrimaryApi { throw IllegalArgumentException("track not found") }
        val fallback = FakeFallbackApi { FallbackStreamResult("https://cdn.example/fallback", 300) }
        YouTubeStreamResolver.resetForTesting(primary = primary, fallback = fallback, clockFn = { now })

        val result = YouTubeStreamResolver.resolveStreamData("vid")

        assertTrue(result.isSuccess)
        assertEquals(1, primary.calls) // terminal failures are never retried
        assertEquals(1, fallback.calls)
    }

    @Test
    fun `all backends failing terminates with a classified error`() = runTest {
        val primary = FakePrimaryApi { throw java.io.IOException("offline") }
        val fallback = FakeFallbackApi { throw java.io.IOException("refused") }
        YouTubeStreamResolver.resetForTesting(primary = primary, fallback = fallback, clockFn = { now })

        val result = YouTubeStreamResolver.resolveStreamData("vid")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is StreamResolutionException)
        assertEquals(2, primary.calls)
        assertEquals(4, fallback.calls)
    }

    @Test
    fun `rate limit rejects without consuming more quota or calling the network`() = runTest {
        val primary = FakePrimaryApi { primaryResult() }
        YouTubeStreamResolver.resetForTesting(primary = primary, clockFn = { now }, rateLimiterMax = 1)

        assertTrue(YouTubeStreamResolver.resolveStreamData("a").isSuccess)
        val limited = YouTubeStreamResolver.resolveStreamData("b")

        assertTrue(limited.isFailure)
        assertTrue(limited.exceptionOrNull() is RateLimitException)
        assertEquals(1, primary.calls)
    }

    @Test
    fun `blank videoId fails fast without any network call`() = runTest {
        val primary = FakePrimaryApi { primaryResult() }
        YouTubeStreamResolver.resetForTesting(primary = primary, clockFn = { now })

        val result = YouTubeStreamResolver.resolveStreamData("   ")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, primary.calls)
    }

    @Test
    fun `duplicate concurrent requests coalesce into one resolution`() = runBlocking {
        val entered = CountDownLatch(1)
        val gate = CompletableDeferred<PrimaryStreamResult>()
        val primary = FakePrimaryApi {
            entered.countDown()
            gate.await()
        }
        YouTubeStreamResolver.resetForTesting(primary = primary, clockFn = { now })

        val first = CompletableDeferred<Result<ResolvedStreamData>>()
        launch(Dispatchers.IO) { first.complete(YouTubeStreamResolver.resolveStreamData("vid")) }
        assertTrue(entered.await(5, TimeUnit.SECONDS))

        val second = CompletableDeferred<Result<ResolvedStreamData>>()
        launch(Dispatchers.IO) { second.complete(YouTubeStreamResolver.resolveStreamData("vid")) }
        Thread.sleep(150) // let the second caller register on the in-flight request

        gate.complete(primaryResult())
        withTimeout(10_000) {
            assertTrue(first.await().isSuccess)
            assertTrue(second.await().isSuccess)
        }
        assertEquals(1, primary.calls)
    }

    @Test
    fun `caller cancellation does not poison the shared in-flight resolution`() = runBlocking {
        val entered = CountDownLatch(1)
        val gate = CompletableDeferred<PrimaryStreamResult>()
        val primary = FakePrimaryApi {
            entered.countDown()
            gate.await()
        }
        YouTubeStreamResolver.resetForTesting(primary = primary, clockFn = { now })

        val leader = launch(Dispatchers.IO) { YouTubeStreamResolver.resolveStreamData("vid") }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        leader.cancel()
        leader.join()

        // The shared resolution continues in the app-owned scope and serves a new caller.
        val joined = CompletableDeferred<Result<ResolvedStreamData>>()
        launch(Dispatchers.IO) { joined.complete(YouTubeStreamResolver.resolveStreamData("vid")) }
        Thread.sleep(150)
        gate.complete(primaryResult())

        withTimeout(10_000) {
            assertTrue(joined.await().isSuccess)
        }
        assertEquals(1, primary.calls)
    }

    @Test
    fun `invalidation during in-flight resolution prevents the stale source from being cached`() = runBlocking {
        val entered = CountDownLatch(1)
        val gate = CompletableDeferred<PrimaryStreamResult>()
        val primary = FakePrimaryApi {
            entered.countDown()
            gate.await()
        }
        YouTubeStreamResolver.resetForTesting(primary = primary, clockFn = { now })

        val inFlight = CompletableDeferred<Result<ResolvedStreamData>>()
        launch(Dispatchers.IO) { inFlight.complete(YouTubeStreamResolver.resolveStreamData("vid")) }
        assertTrue(entered.await(5, TimeUnit.SECONDS))

        // A 403-style recovery invalidates while the old resolution is still running.
        YouTubeStreamResolver.invalidate("vid")
        gate.complete(primaryResult())

        withTimeout(10_000) { assertTrue(inFlight.await().isSuccess) }

        // The stale completion must not have repopulated the cache: the next request re-resolves.
        primary.behavior = { primaryResult(url = "https://cdn.example/fresh") }
        val next = YouTubeStreamResolver.resolveStreamData("vid")
        assertTrue(next.isSuccess)
        assertEquals("https://cdn.example/fresh", next.getOrThrow().url)
        assertEquals(2, primary.calls)
    }

    @Test
    fun `preResolve with matching options is a cache hit, mismatched options re-resolve`() = runTest {
        val primary = FakePrimaryApi { primaryResult() }
        YouTubeStreamResolver.resetForTesting(primary = primary, clockFn = { now })

        assertTrue(YouTubeStreamResolver.resolveStreamData("vid", AudioQuality.HIGH).isSuccess)
        YouTubeStreamResolver.preResolve("vid", quality = AudioQuality.HIGH)
        assertEquals(1, primary.calls)

        YouTubeStreamResolver.preResolve("vid", quality = AudioQuality.AUTO)
        assertEquals(2, primary.calls)
    }

    @Test
    fun `web remix client gets Origin and Referer, native clients do not`() {
        val webHeaders = YouTubeStreamResolver.streamHeaders(YouTubeClient.WEB_REMIX)
        assertTrue(webHeaders.containsKey("Origin"))
        assertTrue(webHeaders.containsKey("Referer"))

        val nativeHeaders = YouTubeStreamResolver.streamHeaders(YouTubeClient.ANDROID_VR_1_65_10)
        assertFalse(nativeHeaders.containsKey("Origin"))
        assertFalse(nativeHeaders.containsKey("Referer"))
        assertTrue(nativeHeaders.containsKey("User-Agent"))
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
