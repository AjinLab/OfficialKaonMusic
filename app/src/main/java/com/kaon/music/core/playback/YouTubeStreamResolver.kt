package com.kaon.music.core.playback

import android.content.Context
import android.net.ConnectivityManager
import com.kaon.music.core.online.AudioQuality
import com.kaon.music.core.online.AudioType
import com.kaon.music.core.online.YTPlayerUtils
import com.kaon.music.core.online.cipher.CipherDeobfuscator
import com.metrolist.innertube.NewPipeExtractor
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.response.PlayerResponse
import com.metrolist.innertube.strategy.ContentHints
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import kotlin.math.min
import kotlin.random.Random

/**
 * Thrown when stream resolution requests exceed the allowed window quota.
 */
class RateLimitException(message: String) : Exception(message)

/**
 * Thrown when stream resolution times out or fails permanently.
 */
class StreamResolutionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Encapsulates the resolved playable stream and associated HTTP headers.
 */
data class ResolvedStreamData(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val clientName: String = "unknown",
    val expiresInSeconds: Int = 300
)

/**
 * Resolves YouTube audio streams on-demand:
 * - App-owned, cancellation-isolated coalescing (one shared resolution per track+options)
 * - Bounded total resolution budget (25s) with deadline-aware stages
 * - One bounded transient retry with jittered backoff on the primary path
 * - Deterministic fallback client rotation (WEB_REMIX, VISIONOS, ANDROID_VR, TVHTML5)
 * - Generation-aware volatile cache (5-minute TTL ceiling, CDN `expire` param honored)
 * - Rate limiting (Max 30/min)
 * - Wi-Fi-only gating, PoToken/Cipher via YTPlayerUtils, per-client CDN headers
 */
object YouTubeStreamResolver {

    private const val RESOLUTION_TIMEOUT_MS = 15_000L
    private const val RETRY_TIMEOUT_MS = 8_000L
    private const val RESOLUTION_BUDGET_MS = 25_000L
    private const val RETRY_BACKOFF_BASE_MS = 250L
    private const val RETRY_BACKOFF_JITTER_MS = 250L
    private const val MIN_STAGE_MS = 2_000L

    // A timeout means the current network path is hanging; retrying into the same
    // hang wastes the budget, so timeouts skip straight to fallback rotation.
    private suspend fun primaryWithRetry(
        videoId: String,
        quality: AudioQuality,
        audioType: AudioType,
        cm: ConnectivityManager?,
        deadlineMs: Long
    ): Result<ResolvedStreamData> {
        var lastFailure: Throwable? = null
        for (attempt in 0..1) {
            val remaining = deadlineMs - clock()
            if (remaining < MIN_STAGE_MS) break
            val timeoutMs = min(if (attempt == 0) RESOLUTION_TIMEOUT_MS else RETRY_TIMEOUT_MS, remaining)
            try {
                val result = withTimeout(timeoutMs) {
                    primaryApi.resolve(videoId, quality, audioType, cm)
                }
                return Result.success(
                    ResolvedStreamData(
                        url = result.url,
                        headers = result.headers,
                        clientName = result.clientName,
                        expiresInSeconds = result.expiresInSeconds
                    )
                )
            } catch (t: TimeoutCancellationException) {
                lastFailure = StreamResolutionException("Primary resolution timed out for $videoId", t)
                break
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                lastFailure = t
                if (t is RateLimitException) break
                if (!isTransientFailure(t)) break
            }
            if (attempt == 0) {
                val backoffMs = RETRY_BACKOFF_BASE_MS + Random.nextLong(RETRY_BACKOFF_JITTER_MS)
                if (clock() + backoffMs + MIN_STAGE_MS > deadlineMs) break
                delay(backoffMs)
            }
        }
        return Result.failure(lastFailure ?: StreamResolutionException("Primary resolution failed for $videoId"))
    }

    /**
     * Transient failures are worth one bounded retry; terminal failures must not be retried.
     */
    internal fun isTransientFailure(t: Throwable): Boolean = when (t) {
        is TimeoutCancellationException -> true
        is RateLimitException -> false
        is IllegalArgumentException -> false
        is IllegalStateException -> false
        is CancellationException -> false
        // IOException family: UnknownHostException, ConnectException, SocketTimeoutException,
        // and Media3/OHttp IO failures all indicate temporary network conditions.
        is java.io.IOException -> true
        else -> false
    }

    // ==================== Injectable seams (production defaults; swapped in tests) ====================

    internal data class PrimaryStreamResult(
        val url: String,
        val headers: Map<String, String>,
        val clientName: String,
        val expiresInSeconds: Int
    )

    internal data class FallbackStreamResult(
        val url: String,
        val expiresInSeconds: Int
    )

    internal interface PrimaryStreamApi {
        suspend fun resolve(videoId: String, quality: AudioQuality, audioType: AudioType, cm: ConnectivityManager?): PrimaryStreamResult
    }

    internal interface FallbackStreamApi {
        fun clients(): List<YouTubeClient>
        /** Returns a playable URL for [client] or null when this client has no playable audio. */
        suspend fun resolve(videoId: String, client: YouTubeClient): FallbackStreamResult
    }

    private val defaultPrimaryApi = object : PrimaryStreamApi {
        override suspend fun resolve(
            videoId: String,
            quality: AudioQuality,
            audioType: AudioType,
            cm: ConnectivityManager?
        ): PrimaryStreamResult {
            val connectivityManager = cm
                ?: throw StreamResolutionException("No connectivity manager available for $videoId")
            val playbackData = YTPlayerUtils.playerResponseForPlayback(
                videoId = videoId,
                audioQuality = quality,
                audioType = audioType,
                connectivityManager = connectivityManager,
                contentHints = ContentHints()
            ).getOrElse { throw it }
            if (playbackData.streamUrl.isBlank()) {
                throw StreamResolutionException("Primary resolver returned a blank stream URL for $videoId")
            }
            return PrimaryStreamResult(
                url = playbackData.streamUrl,
                headers = playbackData.streamHeaders,
                clientName = playbackData.streamClient,
                expiresInSeconds = playbackData.streamExpiresInSeconds
            )
        }
    }

    private val defaultFallbackApi = object : FallbackStreamApi {
        override fun clients(): List<YouTubeClient> = fallbackClients

        override suspend fun resolve(videoId: String, client: YouTubeClient): FallbackStreamResult {
            val response = YouTube.player(videoId, client = client).getOrElse { throw it }
            val streamingData = response.streamingData
            if (response.playabilityStatus.status != "OK" || streamingData == null) {
                return FallbackStreamResult(url = "", expiresInSeconds = 0)
            }
            val url = findBestAudioStream(streamingData, videoId)
                ?: throw StreamResolutionException("No decipherable audio format from ${client.clientName}")
            return FallbackStreamResult(url, streamingData.expiresInSeconds ?: 300)
        }
    }

    @Volatile
    internal var primaryApi: PrimaryStreamApi = defaultPrimaryApi

    @Volatile
    internal var fallbackApi: FallbackStreamApi = defaultFallbackApi

    @Volatile
    internal var clock: () -> Long = System::currentTimeMillis

    // ==================== State ====================

    private val resolverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val coalescer = KeyedRequestCoalescer<String, Result<ResolvedStreamData>>(resolverScope)
    private val cache = StreamSourceCache<ResolvedStreamData>(maxEntries = 200, currentTimeMillis = { clock() })
    private val rateLimiter = SlidingWindowRateLimiter(maxRequests = 30, windowMs = 60_000L, clock = { clock() })

    private val fallbackClients = listOf(
        YouTubeClient.ANDROID_VR_1_65_10,
        YouTubeClient.VISIONOS,
        YouTubeClient.WEB_REMIX,
        YouTubeClient.TVHTML5
    )

    internal fun resetForTesting(
        primary: PrimaryStreamApi = defaultPrimaryApi,
        fallback: FallbackStreamApi = defaultFallbackApi,
        clockFn: () -> Long = System::currentTimeMillis,
        rateLimiterMax: Int = 30
    ) {
        primaryApi = primary
        fallbackApi = fallback
        clock = clockFn
        cache.clear()
        rateLimiter.reset(rateLimiterMax)
    }

    /**
     * Resolves a stream while coalescing concurrent requests for the same track and options.
     * The shared resolution runs in an app-owned scope: callers cancelling (e.g. user skipped
     * the track) neither cancel nor corrupt the resolution other callers are waiting on.
     */
    suspend fun resolveStreamData(
        videoId: String,
        quality: AudioQuality = AudioQuality.AUTO,
        audioType: AudioType = AudioType.AUTO,
        wifiOnly: Boolean = false
    ): Result<ResolvedStreamData> {
        val trimmedId = videoId.trim()
        if (trimmedId.isBlank()) return Result.failure(IllegalArgumentException("Invalid videoId: blank"))
        val key = cacheKey(trimmedId, quality, audioType)
        return try {
            coalescer.execute(key) { resolveUncoalesced(trimmedId, quality, audioType, wifiOnly) }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Resolves a playable audio stream URL for [videoId].
     */
    suspend fun resolveStreamUrl(videoId: String): Result<String> {
        return resolveStreamData(videoId).map { it.url }
    }

    /**
     * Pre-resolves and caches the stream source for the upcoming track in the queue.
     * Must use the same quality/audioType the foreground playback will request so the
     * warm entry is actually a cache hit (cache keys are option-suffixed).
     */
    suspend fun preResolve(
        videoId: String?,
        quality: AudioQuality = AudioQuality.AUTO,
        audioType: AudioType = AudioType.AUTO
    ) {
        if (videoId.isNullOrBlank()) return
        val trimmedId = videoId.trim()
        if (cache.get(cacheKey(trimmedId, quality, audioType)) != null) return
        try {
            resolveStreamData(trimmedId, quality, audioType)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("StreamResolver").d("Background pre-resolve ignored error: ${e.message}")
        }
    }

    fun invalidate(videoId: String) {
        cache.invalidatePrefix("${videoId.trim()}|")
    }

    fun clearCache() {
        cache.clear()
    }

    // ==================== Resolution pipeline ====================

    private suspend fun resolveUncoalesced(
        videoId: String,
        quality: AudioQuality,
        audioType: AudioType,
        wifiOnly: Boolean
    ): Result<ResolvedStreamData> = withContext(Dispatchers.IO) {
        val context = if (CipherDeobfuscator.isInitialized) CipherDeobfuscator.appContext else null
        val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        // Enforce Wi-Fi only restriction before anything else: serving a cached URL would
        // still be network streaming, so the gate must not be bypassed by the cache.
        if (wifiOnly && cm != null) {
            val activeNetwork = cm.activeNetwork
            val caps = if (activeNetwork != null) cm.getNetworkCapabilities(activeNetwork) else null
            val isWifiOrEthernet = caps != null && (
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
                )
            if (!isWifiOrEthernet) {
                Timber.tag("StreamResolver").w("Streaming blocked: Wi-Fi only enabled in settings, but device is not connected to Wi-Fi")
                return@withContext Result.failure(IllegalStateException("Wi-Fi only streaming is enabled in settings, but device is not connected to Wi-Fi."))
            }
        }

        val key = cacheKey(videoId, quality, audioType)
        val deadlineMs = clock() + RESOLUTION_BUDGET_MS

        // 1. Volatile cache with expiry (TTL derived from the CDN `expire` param)
        cache.get(key)?.let { cached ->
            Timber.tag("StreamResolver").d("Serving cached stream data for $videoId")
            return@withContext Result.success(cached)
        }

        // 2. Rate limiting (max 30/min); rejected requests do not consume quota
        if (!rateLimiter.tryAcquire()) {
            Timber.tag("StreamResolver").w("Rate limit exceeded for video $videoId")
            return@withContext Result.failure(RateLimitException("Too many requests. Please wait."))
        }

        // Generation captured before any network work: if invalidate() races this
        // resolution (e.g. a 403 recovery), the stale completion cannot repopulate
        // the cache with the rejected source.
        val generation = cache.generation(key)

        // 3. Primary resolution with one bounded transient retry
        val primaryResult = primaryWithRetry(videoId, quality, audioType, cm, deadlineMs)
        val primary = primaryResult.getOrNull()
        if (primary != null) {
            val expiresAtMs = calculateExpiryTimestampMs(primary.url, primary.expiresInSeconds)
            cache.put(key, primary, expiresAtMs, generation)
            Timber.tag("StreamResolver").d(
                "Resolved stream via primary path for $videoId (client=${primary.clientName}, ttl=${(expiresAtMs - clock()) / 1000}s)"
            )
            return@withContext Result.success(primary)
        }
        val primaryError = primaryResult.exceptionOrNull() ?: StreamResolutionException("Primary resolution failed for $videoId")
        if (primaryError is RateLimitException) return@withContext Result.failure(primaryError)

        // 4. Deterministic fallback client rotation, bounded by the resolution deadline
        val clients = fallbackApi.clients()
        for ((attemptIndex, client) in clients.withIndex()) {
            val remainingMs = deadlineMs - clock()
            if (remainingMs < MIN_STAGE_MS) break
            try {
                val result = withTimeout(min(RESOLUTION_TIMEOUT_MS, remainingMs)) {
                    fallbackApi.resolve(videoId, client)
                }
                if (result.url.isNotBlank()) {
                    val resolved = ResolvedStreamData(
                        url = result.url,
                        headers = streamHeaders(client),
                        clientName = client.clientName,
                        expiresInSeconds = result.expiresInSeconds
                    )
                    val expiresAtMs = calculateExpiryTimestampMs(resolved.url, resolved.expiresInSeconds)
                    cache.put(key, resolved, expiresAtMs, generation)
                    Timber.tag("StreamResolver").d(
                        "Resolved stream via fallback client ${client.clientName} for $videoId (ttl=${(expiresAtMs - clock()) / 1000}s)"
                    )
                    return@withContext Result.success(resolved)
                }
            } catch (t: TimeoutCancellationException) {
                Timber.tag("StreamResolver").w("Fallback client ${client.clientName} timed out on video $videoId")
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Timber.tag("StreamResolver").w("Client ${client.clientName} failed on video $videoId: ${e.message}")
            }

            if (attemptIndex < clients.size - 1) {
                delay(50L * (attemptIndex + 1))
            }
        }

        Result.failure(
            StreamResolutionException("No playable audio stream found for videoId: $videoId", primaryError)
        )
    }

    internal fun cacheKey(videoId: String, quality: AudioQuality, audioType: AudioType): String =
        "$videoId|$quality|$audioType"

    internal fun calculateExpiryTimestampMs(streamUrl: String, fallbackExpiresInSeconds: Int): Long {
        val now = clock()
        try {
            val uri = android.net.Uri.parse(streamUrl)
            val expireParam = uri.getQueryParameter("expire")?.toLongOrNull()
            if (expireParam != null && expireParam > 0) {
                val expireEpochMs = expireParam * 1000L
                if (expireEpochMs > now + 60_000L) {
                    return expireEpochMs - 60_000L // 1-minute safety buffer before CDN expiry
                }
            }
        } catch (e: Exception) {
            Timber.tag("StreamResolver").w("Failed to parse expire param from URL: ${e.message}")
        }
        // A CDN URL must never be retained beyond the lifetime supplied by YouTube. A short
        // fallback lifetime still gets a small floor to avoid a tight resolve loop when `expire`
        // is omitted from a response.
        val fallbackMs = (fallbackExpiresInSeconds.toLong() * 1000L)
            .coerceIn(30_000L, 5 * 60 * 1000L)
        return now + fallbackMs
    }

    /**
     * CDN headers per client. Browser-style Origin/Referer must only be attached to web
     * clients: mobile/TV stream URLs are commonly rejected when those headers are present.
     */
    internal fun streamHeaders(client: YouTubeClient): Map<String, String> = buildMap {
        put("User-Agent", client.userAgent)
        put("Accept", "*/*")
        put("Accept-Language", "en-US,en;q=0.9")
        if (client.clientName == "WEB_REMIX") {
            put("Referer", "https://music.youtube.com/")
            put("Origin", "https://music.youtube.com")
        }
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
            if (!format.signatureCipher.isNullOrBlank() || !format.cipher.isNullOrBlank()) {
                val deciphered = NewPipeExtractor.getStreamUrl(format, videoId)
                if (!deciphered.isNullOrBlank()) {
                    return deciphered
                }
            }
        }

        return null
    }
}

/**
 * Sliding-window rate limiter: at most [maxRequests] acquisitions per [windowMs].
 * Rejected acquisitions do not consume quota.
 */
internal class SlidingWindowRateLimiter(
    private var maxRequests: Int,
    private val windowMs: Long,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val timestamps = ArrayDeque<Long>()

    init {
        require(maxRequests > 0) { "maxRequests must be greater than zero" }
    }

    @Synchronized
    fun tryAcquire(): Boolean {
        val now = clock()
        while (timestamps.isNotEmpty() && (now - timestamps.first()) > windowMs) {
            timestamps.removeFirst()
        }
        return if (timestamps.size < maxRequests) {
            timestamps.addLast(now)
            true
        } else {
            false
        }
    }

    @Synchronized
    fun reset(maxRequests: Int = this.maxRequests) {
        require(maxRequests > 0) { "maxRequests must be greater than zero" }
        this.maxRequests = maxRequests
        timestamps.clear()
    }
}
