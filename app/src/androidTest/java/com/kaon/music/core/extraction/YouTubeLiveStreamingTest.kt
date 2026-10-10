package com.kaon.music.core.extraction

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kaon.music.core.playback.ResolutionMetrics
import com.kaon.music.core.playback.YouTubeStreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * On-Device Hardware Streaming Test on physical device (e.g. moto g05).
 * Tests live stream resolution, HTTP header injection, buffering, and sustained
 * 10-second ExoPlayer audio playback directly against YouTube CDN.
 */
@RunWith(AndroidJUnit4::class)
class YouTubeLiveStreamingTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            YouTubeStreamExtractor.initialize(context)
            YouTubeStreamResolver.attachContext(context)
        }
    }

    /**
     * Requirement: Resolve -> Buffer -> Play for 10 seconds -> Verify no stalls or errors.
     */
    @Test
    fun testLiveOnDeviceStreamingPlayback10Seconds() = runBlocking {
        var player: ExoPlayer? = null
        val playbackError = AtomicReference<PlaybackException?>(null)
        val isPlayingLatch = CountDownLatch(1)
        val playbackStarted = AtomicBoolean(false)

        withContext(Dispatchers.Main) {
            val httpDataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(
                okhttp3.OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
            )

            val resolvingDataSourceFactory = ResolvingDataSource.Factory(
                httpDataSourceFactory,
                object : ResolvingDataSource.Resolver {
                    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                        val uri = dataSpec.uri
                        if (uri.scheme == "youtube" || uri.host == "youtube.com" || uri.host == "youtu.be") {
                            val videoId = when {
                                uri.scheme == "youtube" -> uri.authority ?: uri.host ?: uri.schemeSpecificPart?.removePrefix("//") ?: ""
                                uri.host == "youtube.com" || uri.host == "music.youtube.com" -> uri.getQueryParameter("v") ?: ""
                                uri.host == "youtu.be" -> uri.lastPathSegment ?: ""
                                else -> ""
                            }.trim()

                            android.util.Log.d("TEST_STREAM", "Resolving videoId=$videoId")
                            if (videoId.isNotBlank()) {
                                val resolved = runBlocking {
                                    val res = YouTubeStreamResolver.resolveStreamData(videoId)
                                    android.util.Log.d("TEST_STREAM", "resolveStreamData isSuccess=${res.isSuccess} error=${res.exceptionOrNull()?.message}")
                                    res.getOrNull()
                                }
                                if (resolved != null && resolved.url.isNotBlank()) {
                                    android.util.Log.d("TEST_STREAM", "Resolved client=${resolved.clientName} headers=${resolved.headers.keys}")
                                    val chunkLength = 512 * 1024L
                                    return dataSpec.buildUpon()
                                        .setUri(Uri.parse(resolved.url))
                                        .setHttpRequestHeaders(dataSpec.httpRequestHeaders + resolved.headers)
                                        .build()
                                        .subrange(dataSpec.uriPositionOffset, chunkLength)
                                }
                            }
                        }
                        return dataSpec
                    }
                }
            )

            val dataSourceFactory = DefaultDataSource.Factory(context, resolvingDataSourceFactory)
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

            val p = ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()

            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    android.util.Log.d("TEST_STREAM", "onPlaybackStateChanged: state=$playbackState")
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    android.util.Log.d("TEST_STREAM", "onIsPlayingChanged isPlaying=$isPlaying")
                    if (isPlaying) {
                        playbackStarted.set(true)
                        isPlayingLatch.countDown()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    android.util.Log.e("TEST_STREAM", "onPlayerError ${error.errorCodeName}: ${error.message}")
                    playbackError.set(error)
                }
            })

            val mediaItem = MediaItem.Builder()
                .setMediaId("rickroll")
                .setUri(Uri.parse("youtube://dQw4w9WgXcQ"))
                .build()

            p.setMediaItem(mediaItem)
            p.prepare()
            p.play()
            player = p
        }

        // Wait up to 45s for playback to start on real cellular/Wi-Fi network
        val started = isPlayingLatch.await(45, TimeUnit.SECONDS)
        assertTrue("Playback did not start within 45 seconds", started)
        assertNull("Playback failed with error: ${playbackError.get()?.message}", playbackError.get())

        // Let it play for 10 full seconds on hardware
        var elapsedPlayTimeMs = 0L
        val maxWaitMs = 15_000L
        val startTime = System.currentTimeMillis()

        while ((System.currentTimeMillis() - startTime) < maxWaitMs) {
            delay(1000)
            withContext(Dispatchers.Main) {
                player?.let {
                    elapsedPlayTimeMs = it.currentPosition
                }
            }
            if (elapsedPlayTimeMs >= 10_000L) {
                break
            }
        }

        withContext(Dispatchers.Main) {
            player?.stop()
            player?.release()
        }

        assertTrue("Playback position did not reach 10s (was ${elapsedPlayTimeMs}ms)", elapsedPlayTimeMs >= 10_000L)
        assertNull("Playback encountered error during 10s playback: ${playbackError.get()?.message}", playbackError.get())
    }

    /**
     * Measures the resolution stages the fast path is supposed to eliminate.
     *
     * Requires a device and network: the expensive stages (player request, cipher solve, PoToken mint)
     * live inside InnerTubeX, so only a live run can show whether the first attempt actually avoids
     * them. Reports timings and the outcome histogram rather than asserting a threshold, because a
     * wall-clock budget would make this test a flake on a slow network.
     */
    @Test
    fun measureFastPathResolutionLatency() = runBlocking {
        ResolutionMetrics.reset()
        // A cold measurement requires a cold cache: another test in the same process may have just
        // resolved the same video, and a warm entry would turn this into a 3ms cache-hit test.
        YouTubeStreamResolver.clearCache()

        val coldStart = System.currentTimeMillis()
        val cold = YouTubeStreamResolver.resolveStreamData(TEST_VIDEO_ID)
        val coldMs = System.currentTimeMillis() - coldStart
        assertTrue("cold resolution failed: ${cold.exceptionOrNull()?.message}", cold.isSuccess)

        val warmStart = System.currentTimeMillis()
        val warm = YouTubeStreamResolver.resolveStreamData(TEST_VIDEO_ID)
        val warmMs = System.currentTimeMillis() - warmStart
        assertTrue(warm.isSuccess)

        val resolved = cold.getOrThrow()
        android.util.Log.i(
            "TEST_STREAM",
            "resolution client=${resolved.clientName} cold=${coldMs}ms warm=${warmMs}ms " +
                "ttl=${resolved.expiresInSeconds}s boundedRange=${resolved.requireBoundedRange} " +
                "metrics=[${ResolutionMetrics.summary()}]"
        )

        assertEquals(
            "the second resolution must be a cache hit, not another extraction",
            1L,
            ResolutionMetrics.count(ResolutionMetrics.CACHE_HIT)
        )
        assertTrue("a warm hit should be effectively free, was ${warmMs}ms", warmMs < coldMs)
    }

    private companion object {
        const val TEST_VIDEO_ID = "dQw4w9WgXcQ"
    }
}
