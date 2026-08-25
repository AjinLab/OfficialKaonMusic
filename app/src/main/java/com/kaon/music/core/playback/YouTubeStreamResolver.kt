package com.kaon.music.core.playback

import com.metrolist.innertube.NewPipeExtractor
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.response.PlayerResponse
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
 * Resolves YouTube audio streams on-demand with rate limiting, volatile in-memory caching (5-minute TTL),
 * client rotation, signature deciphering, and pre-resolution for gapless queue transitions.
 */
object YouTubeStreamResolver {

    private const val RESOLUTION_TIMEOUT_MS = 10_000L
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
     * Checks in-memory cache first, enforces rate limiting, and performs fallback client resolution.
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

        // 3. Resolve stream URL with timeout
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
                        delay(50L * attempt) // Subtle backoff before next client
                    }
                }

                // Final fallback using default WEB_REMIX
                try {
                    val response = YouTube.player(trimmedId, client = YouTubeClient.WEB_REMIX).getOrNull()
                    if (response?.streamingData != null) {
                        val streamUrl = findBestAudioStream(response.streamingData!!, trimmedId)
                        if (streamUrl != null) {
                            streamCache[trimmedId] = CachedStream(streamUrl, now + CACHE_TTL_MS)
                            return@withTimeout Result.success(streamUrl)
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag("StreamResolver").e(e, "Final fallback failed for video $trimmedId")
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

        // Sort by audio bitrate descending (prefer highest quality audio stream)
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
