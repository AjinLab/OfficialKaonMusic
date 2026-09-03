package com.kaon.music.core.playback

import android.content.Context
import android.net.ConnectivityManager
import com.kaon.music.core.logging.Redact
import com.kaon.music.core.online.AudioQuality
import com.kaon.music.core.online.AudioType
import com.kaon.music.core.online.PlaybackData
import com.kaon.music.core.online.YouTubeStreamExtractor
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

/** Thrown when stream resolution requests exceed the allowed window quota. */
class RateLimitException(message: String) : Exception(message)

/** Thrown when stream resolution times out or fails permanently. */
class StreamResolutionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** A resolved playable stream plus the transport facts the player needs. */
data class ResolvedStreamData(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val clientName: String = "unknown",
    val expiresInSeconds: Int = 300,
    /** The CDN requires ranged requests for this stream; a single unbounded GET will be rejected. */
    val requireBoundedRange: Boolean = false,
    val rangeChunkSizeBytes: Long = 0L,
    val useRangeChunks: Boolean = false
)

/**
 * Resolution policy for YouTube audio streams.
 *
 * ARCHITECTURE.md §7 and §8. This owns what Kaon owns:
 * - request coalescing, so concurrent callers for the same track share one resolution and a caller
 *   cancelling its wait does not cancel the shared work,
 * - a bounded total budget with deadline-aware stages,
 * - one retry for transient failures only,
 * - a generation-guarded cache keyed on the CDN `expire` parameter,
 * - rate limiting and the Wi-Fi-only gate.
 *
 * Extraction itself — client selection, fallback rotation, cipher solving, PoToken — belongs to
 * [YouTubeStreamExtractor] and, beneath it, innertubex. Kaon used to duplicate the fallback rotation
 * here with its own client list and format picker; innertubex's `PlayerClientDirector` and
 * `ClientHealthMonitor` already do that with health tracking, so the duplicate has been removed.
 */
object YouTubeStreamResolver {

    private const val RESOLUTION_TIMEOUT_MS = 15_000L
    private const val RETRY_TIMEOUT_MS = 8_000L
    private const val RESOLUTION_BUDGET_MS = 25_000L
    private const val RETRY_BACKOFF_BASE_MS = 250L
    private const val RETRY_BACKOFF_JITTER_MS = 250L
    private const val MIN_STAGE_MS = 2_000L

    // ==================== Injectable seams (production defaults; swapped in tests) ====================

    internal interface StreamExtractionApi {
        suspend fun extract(
            videoId: String,
            quality: AudioQuality,
            audioType: AudioType,
            connectivityManager: ConnectivityManager?
        ): ResolvedStreamData
    }

    private val defaultExtractionApi = object : StreamExtractionApi {
        override suspend fun extract(
            videoId: String,
            quality: AudioQuality,
            audioType: AudioType,
            connectivityManager: ConnectivityManager?
        ): ResolvedStreamData {
            val cm = connectivityManager
                ?: throw StreamResolutionException("No connectivity manager available for $videoId")
            val playbackData = YouTubeStreamExtractor.resolve(
                videoId = videoId,
                audioQuality = quality,
                connectivityManager = cm
            ).getOrElse { throw it }
            if (playbackData.streamUrl.isBlank()) {
                throw StreamResolutionException("Extractor returned a blank stream URL for $videoId")
            }
            return playbackData.toResolvedStreamData()
        }
    }

    @Volatile
    internal var extractionApi: StreamExtractionApi = defaultExtractionApi

    @Volatile
    internal var clock: () -> Long = System::currentTimeMillis

    /**
     * Supplies the [ConnectivityManager] for metered-network checks and the Wi-Fi gate.
     *
     * Injected from [com.kaon.music.app.di.AppContainer] rather than read off a global. The resolver
     * previously reached into `CipherDeobfuscator.appContext` — a cipher object acting as the app's
     * service locator, which ARCHITECTURE.md §7 forbids.
     */
    @Volatile
    private var connectivityManagerProvider: () -> ConnectivityManager? = { null }

    fun attachContext(context: Context) {
        val appContext = context.applicationContext
        connectivityManagerProvider = {
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        }
    }

    // ==================== State ====================

    private val resolverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val coalescer = KeyedRequestCoalescer<String, Result<ResolvedStreamData>>(resolverScope)
    private val cache = StreamSourceCache<ResolvedStreamData>(maxEntries = 200, currentTimeMillis = { clock() })
    private val rateLimiter = SlidingWindowRateLimiter(maxRequests = 30, windowMs = 60_000L, clock = { clock() })

    internal fun resetForTesting(
        extraction: StreamExtractionApi = defaultExtractionApi,
        clockFn: () -> Long = System::currentTimeMillis,
        rateLimiterMax: Int = 30,
        connectivity: () -> ConnectivityManager? = { null }
    ) {
        extractionApi = extraction
        clock = clockFn
        connectivityManagerProvider = connectivity
        cache.clear()
        rateLimiter.reset(rateLimiterMax)
    }

    /**
     * Transient failures are worth one bounded retry; terminal failures must not be retried.
     *
     * Typed classification matters: the extractor unwraps `StreamResolveException.Reason.NETWORK` to
     * its `IOException` cause precisely so this check applies. Untyped `Exception(String)` throws —
     * which the old engine produced for every real resolution failure — fall through to `false` and
     * skip the retry, which is why it never fired.
     */
    internal fun isTransientFailure(t: Throwable): Boolean = when (t) {
        is TimeoutCancellationException -> true
        is RateLimitException -> false
        is IllegalArgumentException -> false
        is IllegalStateException -> false
        is CancellationException -> false
        is java.io.IOException -> true
        else -> false
    }

    /**
     * Resolves a stream, coalescing concurrent requests for the same track and options.
     *
     * The shared resolution runs in an app-owned scope, so a caller cancelling (the user skipped the
     * track) neither cancels nor corrupts the resolution other callers are waiting on.
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

    suspend fun resolveStreamUrl(videoId: String): Result<String> =
        resolveStreamData(videoId).map { it.url }

    /**
     * Pre-resolves the upcoming queue item.
     *
     * Must use the same quality/audioType the foreground playback will request, or the warm entry is
     * not a cache hit — the key is option-suffixed.
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
            Timber.tag("resolve").d("Background pre-resolve ignored error: ${e.message}")
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
        val cm = connectivityManagerProvider()

        // Enforce the Wi-Fi gate before the cache: serving a cached URL is still network streaming,
        // so the gate must not be bypassed by a warm entry.
        if (wifiOnly && cm != null) {
            val activeNetwork = cm.activeNetwork
            val caps = if (activeNetwork != null) cm.getNetworkCapabilities(activeNetwork) else null
            val isWifiOrEthernet = caps != null && (
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
                )
            if (!isWifiOrEthernet) {
                Timber.tag("resolve").w("Streaming blocked: Wi-Fi only enabled but device is not on Wi-Fi")
                return@withContext Result.failure(
                    IllegalStateException("Wi-Fi only streaming is enabled in settings, but device is not connected to Wi-Fi.")
                )
            }
        }

        val key = cacheKey(videoId, quality, audioType)
        val deadlineMs = clock() + RESOLUTION_BUDGET_MS

        cache.get(key)?.let { cached ->
            Timber.tag("resolve").d("Cache hit for $videoId")
            return@withContext Result.success(cached)
        }

        if (!rateLimiter.tryAcquire()) {
            Timber.tag("resolve").w("Rate limit exceeded for video $videoId")
            return@withContext Result.failure(RateLimitException("Too many requests. Please wait."))
        }

        // Generation captured before any network work: if invalidate() races this resolution (a 403
        // recovery, say), the stale completion cannot repopulate the cache with the rejected source.
        val generation = cache.generation(key)

        val result = extractWithRetry(videoId, quality, audioType, cm, deadlineMs)
        val resolved = result.getOrElse { error ->
            return@withContext Result.failure(error)
        }

        val expiresAtMs = calculateExpiryTimestampMs(resolved.url, resolved.expiresInSeconds)
        cache.put(key, resolved, expiresAtMs, generation)
        Timber.tag("resolve").d(
            "Resolved $videoId client=${resolved.clientName} ttl=${(expiresAtMs - clock()) / 1000}s " +
                "url=${Redact.url(resolved.url)}"
        )
        Result.success(resolved)
    }

    /**
     * One extraction attempt plus at most one retry for transient failures.
     *
     * A timeout means the current network path is hanging; retrying into the same hang wastes the
     * budget, so timeouts fail immediately rather than consuming the retry.
     */
    private suspend fun extractWithRetry(
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
                return Result.success(
                    withTimeout(timeoutMs) { extractionApi.extract(videoId, quality, audioType, cm) }
                )
            } catch (t: TimeoutCancellationException) {
                lastFailure = StreamResolutionException("Stream extraction timed out for $videoId", t)
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
        return Result.failure(
            lastFailure ?: StreamResolutionException("Stream extraction failed for $videoId")
        )
    }

    internal fun cacheKey(videoId: String, quality: AudioQuality, audioType: AudioType): String =
        "$videoId|$quality|$audioType"

    /**
     * Converts stream lifetime into an absolute expiry instant.
     *
     * The CDN `expire` parameter is authoritative when present; the extractor's relative value is the
     * fallback. The 5-minute ceiling is deliberate belt-and-braces for a response that reports an
     * implausibly long lifetime — a stale URL costs a rebuffer plus a full re-extraction, so the
     * cache prefers to re-resolve early.
     */
    internal fun calculateExpiryTimestampMs(streamUrl: String, fallbackExpiresInSeconds: Int): Long {
        val now = clock()
        val expireEpochSeconds = parseExpireQueryParam(streamUrl)
        if (expireEpochSeconds != null && expireEpochSeconds > 0) {
            val expireEpochMs = expireEpochSeconds * 1000L
            if (expireEpochMs > now + 60_000L) {
                return expireEpochMs - 60_000L // 1-minute safety buffer before CDN expiry
            }
        }
        val fallbackMs = (fallbackExpiresInSeconds.toLong() * 1000L)
            .coerceIn(30_000L, 5 * 60 * 1000L)
        return now + fallbackMs
    }

    /**
     * Reads the CDN `expire` query parameter.
     *
     * Deliberately string-based rather than `Uri.parse`: expiry arithmetic is the cache's correctness
     * boundary and must be unit-testable without the Android framework, which returns stubs under
     * `unitTests.isReturnDefaultValues`.
     */
    private fun parseExpireQueryParam(streamUrl: String): Long? {
        val queryStart = streamUrl.indexOf('?')
        if (queryStart < 0 || queryStart == streamUrl.lastIndex) return null
        return streamUrl.substring(queryStart + 1)
            .split('&')
            .firstNotNullOfOrNull { param ->
                val separator = param.indexOf('=')
                if (separator <= 0) return@firstNotNullOfOrNull null
                if (param.substring(0, separator) != "expire") return@firstNotNullOfOrNull null
                param.substring(separator + 1).toLongOrNull()
            }
    }

    private fun PlaybackData.toResolvedStreamData() = ResolvedStreamData(
        url = streamUrl,
        headers = streamHeaders,
        clientName = streamClient,
        expiresInSeconds = streamExpiresInSeconds,
        requireBoundedRange = requireBoundedRange,
        rangeChunkSizeBytes = rangeChunkSizeBytes,
        useRangeChunks = useRangeChunks
    )
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
