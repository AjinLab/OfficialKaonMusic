package com.kaon.music.core.online

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
import com.kaon.music.core.online.cipher.CipherDeobfuscator
import com.kaon.music.core.playback.YouTubeStreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
            if (!CipherDeobfuscator.isInitialized) {
                CipherDeobfuscator.initialize(context)
            }
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

                            println("[TEST_STREAM] Resolving URI: $uri, videoId: $videoId")
                            if (videoId.isNotBlank()) {
                                val resolved = runBlocking {
                                    val res = YouTubeStreamResolver.resolveStreamData(videoId)
                                    println("[TEST_STREAM] resolveStreamData result: isSuccess=${res.isSuccess}, error=${res.exceptionOrNull()?.message}")
                                    res.getOrNull()
                                }
                                if (resolved != null && resolved.url.isNotBlank()) {
                                    println("[TEST_STREAM] Resolved URL: ${resolved.url.take(80)}... client=${resolved.clientName}, headers=${resolved.headers.keys}")
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
                    println("[TEST_STREAM] onPlaybackStateChanged: state=$playbackState (READY=${Player.STATE_READY}, BUFFERING=${Player.STATE_BUFFERING})")
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    println("[TEST_STREAM] onIsPlayingChanged: isPlaying=$isPlaying")
                    if (isPlaying) {
                        playbackStarted.set(true)
                        isPlayingLatch.countDown()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    println("[TEST_STREAM] onPlayerError: ${error.errorCodeName} - ${error.message}")
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
}
