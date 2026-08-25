package com.kaon.music.core.playback

import android.content.Context
import android.net.ConnectivityManager
import com.kaon.music.core.online.AudioQuality
import com.kaon.music.core.online.YTPlayerUtils
import com.kaon.music.core.online.cipher.CipherDeobfuscator
import com.metrolist.innertube.NewPipeExtractor
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.response.PlayerResponse
import com.metrolist.innertube.strategy.ContentHints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Thrown when stream resolution requests exceed the allowed window quota.
 */
class RateLimitException(message: String) : Exception(message)

/**
 * Thrown when stream resolution times out or fails permanently.
 */
class StreamResolutionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Resolves YouTube audio streams on-demand using Metrolist's exact playback & PoToken/Cipher architecture:
 * - Rate limiting (Max 30/min)
 * - In-memory volatile caching (5-minute TTL)
 * - PoToken generation (BotGuard headless WebView)
 * - Cipher / STS signature deciphering (JS AST solver)
 * - Content-aware client rotation (WEB_REMIX, VISIONOS, ANDROID_VR, TVHTML5)
 * - Pre-resolution for gapless queue transitions
 */
object YouTubeStreamResolver {

    private const val RESOLUTION_TIMEOUT_MS = 12_000L
    private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes in-memory TTL

    private data class CachedStream(
        val streamUrl: String,
        val expiresAtMs: Long
    )

    private val streamCache = ConcurrentHashMap<String, CachedStream>()

    // Rate limiter: Max 30 requests per 60 seconds
    private val requestTimestamps = ArrayDeque<Long>()
    private const val MAX_REQUESTS_PER_WINDOW = 30
    private const val RATE_LIMIT_WINDOW_MS = 60_000L

    private val fallbackClients = listOf(
        YouTubeClient.ANDROID_VR_1_65_10,
        YouTubeClient.VISIONOS,
        YouTubeClient.WEB_REMIX,
        YouTubeClient.TVHTML5
    )

    @Synchronized
    private fun checkRateLimit(): Boolean {
        val now = System.currentTimeMillis()
        while (requestTimestamps.isNotEmpty() && (now - requestTimestamps.first()) > RATE_LIMIT_WINDOW_MS) {
            requestTimestamps.removeFirst()
        }
        return if (requestTimestamps.size < MAX_REQUESTS_PER_WINDOW) {
            requestTimestamps.addLast(now)
            true
        } else {
            false
        }
    }

    /**
     * Resolves a playable audio stream URL for [videoId].
     * Checks in-memory cache first, enforces rate limiting, and performs Metrolist YTPlayerUtils resolution.
     */
    suspend fun resolveStreamUrl(videoId: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmedId = videoId.trim()
        if (trimmedId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Invalid videoId: blank"))
        }

        // 1. Check in-memory volatile cache
        val now = System.currentTimeMillis()
        val cached = streamCache[trimmedId]
        if (cached != null && cached.expiresAtMs > now) {
            Timber.tag("StreamResolver").d("Serving cached stream URL for $trimmedId")
            return@withContext Result.success(cached.streamUrl)
        }

        // 2. Enforce Rate Limiting
        if (!checkRateLimit()) {
            Timber.tag("StreamResolver").w("Rate limit exceeded for video $trimmedId")
            return@withContext Result.failure(RateLimitException("Too many requests. Please wait."))
        }

        // 3. Primary Metrolist YTPlayerUtils resolution with PoToken and Cipher
        val context = if (CipherDeobfuscator.isInitialized) CipherDeobfuscator.appContext else null
        val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        if (cm != null) {
            try {
                withTimeout(RESOLUTION_TIMEOUT_MS) {
                    val playbackResult = YTPlayerUtils.playerResponseForPlayback(
                        videoId = trimmedId,
                        audioQuality = AudioQuality.HIGH,
                        connectivityManager = cm,
                        contentHints = ContentHints()
                    )
                    val playbackData = playbackResult.getOrNull()
                    if (playbackData != null && playbackData.streamUrl.isNotBlank()) {
                        val expiryMs = (playbackData.streamExpiresInSeconds.toLong() * 1000L).coerceAtLeast(CACHE_TTL_MS)
                        streamCache[trimmedId] = CachedStream(playbackData.streamUrl, now + expiryMs)
                        Timber.tag("StreamResolver").d("Resolved stream via Metrolist YTPlayerUtils for $trimmedId")
                        return@withTimeout Result.success(playbackData.streamUrl)
                    }
                }
            } catch (e: Exception) {
                Timber.tag("StreamResolver").w(e, "YTPlayerUtils primary resolution failed for $trimmedId, falling back")
            }
        }

        // 4. Fallback multi-client rotation
        try {
            withTimeout(RESOLUTION_TIMEOUT_MS) {
                var attempt = 0
                for (client in fallbackClients) {
                    try {
                        val playerResult = YouTube.player(trimmedId, client = client)
                        val response = playerResult.getOrNull()
                        if (response != null && response.playabilityStatus.status == "OK" && response.streamingData != null) {
                            val streamUrl = findBestAudioStream(response.streamingData!!, trimmedId)
                            if (streamUrl != null) {
                                streamCache[trimmedId] = CachedStream(streamUrl, now + CACHE_TTL_MS)
                                return@withTimeout Result.success(streamUrl)
                            }
                        }
                    } catch (e: Exception) {
                        Timber.tag("StreamResolver").w("Client ${client.clientName} failed on video $trimmedId: ${e.message}")
                    }

                    attempt++
                    if (attempt < fallbackClients.size) {
                        delay(50L * attempt)
                    }
                }

                Result.failure(StreamResolutionException("No playable audio stream found for videoId: $trimmedId"))
            }
        } catch (e: TimeoutCancellationException) {
            Timber.tag("StreamResolver").e("Stream resolution timed out for $trimmedId")
            Result.failure(StreamResolutionException("YouTube stream resolution timed out after ${RESOLUTION_TIMEOUT_MS}ms", e))
        } catch (e: Exception) {
            Timber.tag("StreamResolver").e(e, "Stream resolution error for $trimmedId")
            Result.failure(StreamResolutionException("Stream resolution failed: ${e.message}", e))
        }
    }

    /**
     * Pre-resolves and caches the stream URL for the upcoming track in the queue.
     */
    suspend fun preResolve(videoId: String?) {
        if (videoId.isNullOrBlank()) return
        val trimmedId = videoId.trim()
        val now = System.currentTimeMillis()
        val cached = streamCache[trimmedId]
        if (cached == null || cached.expiresAtMs <= now) {
            try {
                resolveStreamUrl(trimmedId)
            } catch (e: Throwable) {
                Timber.tag("StreamResolver").d("Background pre-resolve ignored error: ${e.message}")
            }
        }
    }

    fun clearCache() {
        streamCache.clear()
    }

    private fun findBestAudioStream(
        streamingData: PlayerResponse.StreamingData,
        videoId: String
    ): String? {
        val allFormats = (streamingData.adaptiveFormats.orEmpty() + streamingData.formats.orEmpty())
        val audioFormats = allFormats.filter { it.mimeType.startsWith("audio/") }

        // Sort by audio bitrate descending
        val sortedAudio = audioFormats.sortedByDescending { it.bitrate ?: 0 }

        for (format in sortedAudio) {
            if (!format.url.isNullOrBlank()) {
                return format.url
            }
            if (!format.signatureCipher.isNullOrBlank()) {
                val deciphered = NewPipeExtractor.getStreamUrl(format, videoId)
                if (!deciphered.isNullOrBlank()) {
                    return deciphered
                }
            }
        }

        return null
    }
}
