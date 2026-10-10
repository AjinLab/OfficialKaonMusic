package com.kaon.music.core.playback

import android.content.Context
import android.net.ConnectivityManager
import com.kaon.music.core.extraction.logging.Redact
import com.kaon.music.core.extraction.model.AudioQuality
import com.kaon.music.core.extraction.model.AudioType
import com.kaon.music.core.extraction.PlaybackData
import com.kaon.music.core.model.MediaLocator
import com.kaon.music.core.extraction.YouTubeStreamExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import kotlin.math.min
import kotlin.random.Random

/** Thrown when stream resolution requests exceed the allowed window quota. */
class RateLimitException(message: String) : Exception(message)

/**
 * Why a stream is being resolved.
 *
 * Foreground playback and speculative pre-resolution share one extractor and one rate-limit window,
 * so they must be distinguishable: a background pre-resolve must never consume the quota or the
 * concurrency that the track the user just tapped needs. WAVE hit exactly this contention and
 * responded by disabling pre-resolution outright; Kaon bounds it instead.
 */
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

    /**
     * Rate-limit slots reserved for foreground playback. A prefetch runs only while at least this
     * many acquisitions remain in the current window.
     */
    private const val PREFETCH_QUOTA_HEADROOM = 8

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
     * Whether the active network is unmetered (Wi-Fi or Ethernet): true, false, or null when there
     * is no connectivity manager to ask (the gate is then not enforceable and stays open — the
     * original gate's semantics). Injectable for JVM tests, which cannot construct a
     * [ConnectivityManager].
     */
    private val defaultUnmeteredCheck: () -> Boolean? = {
        val cm = connectivityManagerProvider()
        if (cm == null) {
            null
        } else {
            val activeNetwork = cm.activeNetwork
            val caps = if (activeNetwork != null) cm.getNetworkCapabilities(activeNetwork) else null
            caps != null && (
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
                )
        }
    }

    @Volatile
    internal var activeNetworkUnmetered: () -> Boolean? = defaultUnmeteredCheck

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

    /**
     * Foreground resolutions currently in flight.
     *
     * A prefetch checks this and gives up rather than queueing behind — or worse, ahead of — the
     * track the user is waiting for. Prefetch is speculative by definition, so skipping it costs
     * nothing; delaying playback costs the user.
     */
    private val foregroundInFlight = java.util.concurrent.atomic.AtomicInteger(0)

    internal fun resetForTesting(
        extraction: StreamExtractionApi = defaultExtractionApi,
        clockFn: () -> Long = System::currentTimeMillis,
        rateLimiterMax: Int = 30,
        connectivity: () -> ConnectivityManager? = { null }
    ) {
        extractionApi = extraction
        clock = clockFn
        connectivityManagerProvider = connectivity
        activeNetworkUnmetered = defaultUnmeteredCheck
        cache.clear()
        rateLimiter.reset(rateLimiterMax)
        foregroundInFlight.set(0)
        ResolutionMetrics.reset()
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
        wifiOnly: Boolean = false,
        purpose: ResolvePurpose = ResolvePurpose.PLAYBACK
    ): Result<ResolvedStreamData> {
        val trimmedId = videoId.trim()
        if (trimmedId.isBlank()) return Result.failure(IllegalArgumentException("Invalid videoId: blank"))

        // Enforce the Wi-Fi gate before the cache: serving a cached URL is still network streaming,
        // so the gate must not be bypassed by a warm entry. (The gate used to live inside the
        // coalesced pipeline, which the fast-path lookup below skipped entirely.)
        if (wifiOnly && activeNetworkUnmetered() == false) {
            Timber.tag("resolve").w("Streaming blocked: Wi-Fi only enabled but device is not on Wi-Fi")
            ResolutionMetrics.record(ResolutionMetrics.WIFI_BLOCKED, 0L)
            return Result.failure(
                IllegalStateException("Wi-Fi only streaming is enabled in settings, but device is not connected to Wi-Fi.")
            )
        }

        // Key excludes purpose on purpose: a prefetch and a playback request for the same track and
        // options must coalesce onto one extraction, and the prefetch result must be reusable by the
        // subsequent playback request.
        val key = cacheKey(trimmedId, quality, audioType)

        // Serve a warm entry without entering the coalescer at all — this is the cheapest path and
        // the one a prefetch is trying to create.
        cache.get(key)?.let { cached ->
            ResolutionMetrics.record(ResolutionMetrics.CACHE_HIT, 0L)
            return Result.success(cached)
        }

        if (purpose == ResolvePurpose.PLAYBACK) foregroundInFlight.incrementAndGet()
        return try {
            coalescer.execute(key) { resolveUncoalesced(trimmedId, quality, audioType, purpose) }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            if (purpose == ResolvePurpose.PLAYBACK) foregroundInFlight.decrementAndGet()
        }
    }

    suspend fun resolveStreamUrl(videoId: String): Result<String> =
        resolveStreamData(videoId).map { it.url }

    /**
     * Pre-resolves the upcoming queue item.
     *
     * Must use the same quality/audioType/wifiOnly the foreground playback will request, or the warm
     * entry is not a cache hit — the key is option-suffixed, and the gate would either run here with
     * wrong facts or (worse) be skipped while playback is blocked on metered.
     */
    suspend fun preResolve(
        videoId: String?,
        quality: AudioQuality = AudioQuality.AUTO,
        audioType: AudioType = AudioType.AUTO,
        wifiOnly: Boolean = false
    ) {
        if (videoId.isNullOrBlank()) return
        val trimmedId = videoId.trim()
        if (cache.get(cacheKey(trimmedId, quality, audioType)) != null) {
            ResolutionMetrics.record(ResolutionMetrics.PREFETCH_WARM, 0L)
            return
        }

        // A prefetch must respect the same Wi-Fi-only gate as the playback it is warming for;
        // otherwise it silently performs the exact network streaming the user opted out of.
        if (wifiOnly && activeNetworkUnmetered() == false) {
            Timber.tag("resolve").d("Pre-resolve skipped for $trimmedId: Wi-Fi-only gate active")
            ResolutionMetrics.record(ResolutionMetrics.PREFETCH_SKIPPED_WIFI_GATE, 0L)
            return
        }

        // Foreground priority: never start speculative work while the user is waiting for a track.
        // The next transition triggers pre-resolution again, so skipping here loses nothing.
        if (foregroundInFlight.get() > 0) {
            Timber.tag("resolve").d("Pre-resolve skipped for $trimmedId: foreground resolution in flight")
            ResolutionMetrics.record(ResolutionMetrics.PREFETCH_SKIPPED_FOREGROUND, 0L)
            return
        }

        // Leave headroom in the rate-limit window for foreground requests. Spending the last of the
        // quota on speculation is how a prefetch ends up rejecting the track the user then taps.
        if (!rateLimiter.hasHeadroom(PREFETCH_QUOTA_HEADROOM)) {
            Timber.tag("resolve").d("Pre-resolve skipped for $trimmedId: rate-limit headroom exhausted")
            ResolutionMetrics.record(ResolutionMetrics.PREFETCH_SKIPPED_HEADROOM, 0L)
            return
        }

        // Extraction failures usually arrive as a Result value (the pipeline models failures in its
        // return type), so the outcome is read from the result; the catch only covers wiring errors.
        val result = try {
            resolveStreamData(trimmedId, quality, audioType, wifiOnly, purpose = ResolvePurpose.PREFETCH)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResolutionMetrics.record(ResolutionMetrics.PREFETCH_FAILED, 0L)
            Timber.tag("resolve").d("Background pre-resolve ignored error: ${e.message}")
            return
        }
        if (result.isFailure) {
            ResolutionMetrics.record(ResolutionMetrics.PREFETCH_FAILED, 0L)
            Timber.tag("resolve").d("Background pre-resolve ignored failure: ${result.exceptionOrNull()?.message}")
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
        purpose: ResolvePurpose
    ): Result<ResolvedStreamData> = withContext(Dispatchers.IO) {
        val trace = ResolutionTrace(videoId, clock)
        val cm = connectivityManagerProvider()

        val key = cacheKey(videoId, quality, audioType)
        val deadlineMs = clock() + RESOLUTION_BUDGET_MS

        // Re-check after the Wi-Fi gate: a concurrent resolution may have populated the entry while
        // this call was queued in the coalescer.
        cache.get(key)?.let { cached ->
            trace.log(ResolutionMetrics.CACHE_HIT)
            return@withContext Result.success(cached)
        }
        trace.mark("cache-miss")

        if (!rateLimiter.tryAcquire()) {
            Timber.tag("resolve").w("Rate limit exceeded for video $videoId (purpose=$purpose)")
            trace.log(ResolutionMetrics.RATE_LIMITED)
            return@withContext Result.failure(RateLimitException("Too many requests. Please wait."))
        }

        // Generation captured before any network work: if invalidate() races this resolution (a 403
        // recovery, say), the stale completion cannot repopulate the cache with the rejected source.
        val generation = cache.generation(key)

        var attempts = 0
        val result = extractWithRetry(videoId, quality, audioType, cm, deadlineMs) { attempts++ }
        trace.mark("extract")

        val resolved = result.getOrElse { error ->
            trace.log(ResolutionMetrics.FAILED)
            return@withContext Result.failure(error)
        }

        val expiresAtMs = calculateExpiryTimestampMs(resolved.url, resolved.expiresInSeconds)
        cache.put(key, resolved, expiresAtMs, generation)
        // Fallback-frequency visibility: innertubex owns client rotation and its health monitor is
        // invisible from here, so the only observable is which client the successful extraction
        // came from. A histogram dominated by non-VISIONOS clients means the cheap-first catalog
        // (ARCHITECTURE.md §7.1) has regressed even when every resolve still succeeds.
        ResolutionMetrics.recordClient(resolved.clientName)
        trace.log(
            outcome = if (attempts > 1) ResolutionMetrics.EXTRACTED_RETRY else ResolutionMetrics.EXTRACTED,
            clientName = resolved.clientName
        )
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
        deadlineMs: Long,
        onAttempt: () -> Unit = {}
    ): Result<ResolvedStreamData> {
        var lastFailure: Throwable? = null
        for (attempt in 0..1) {
            val remaining = deadlineMs - clock()
            if (remaining < MIN_STAGE_MS) break
            val timeoutMs = min(if (attempt == 0) RESOLUTION_TIMEOUT_MS else RETRY_TIMEOUT_MS, remaining)
            try {
                onAttempt()
                return Result.success(
                    withTimeout(timeoutMs) { extractionApi.extract(videoId, quality, audioType, cm) }
                )
            } catch (t: TimeoutCancellationException) {
                lastFailure = StreamResolutionException("Stream extraction timed out for $videoId", t)
                break
            } catch (ce: CancellationException) {
                // A cancellation that left our own job running did not come from the caller (the
                // coalescer decouples waiters from the shared work) or from the withTimeout above:
                // innertubex's session guard aborts in-flight extractions when the InnerTube
                // session advances (a cold-start bootstrap racing the first resolve — observed on
                // device as "InnerTube session changed"). Under the new session the retry
                // succeeds, so this takes the one bounded retry. Only a scope teardown (our job
                // already cancelled) keeps propagating.
                if (!currentCoroutineContext().isActive) throw ce
                lastFailure = StreamResolutionException("Stream extraction aborted by a session change for $videoId", ce)
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

    /** True when at least [slots] acquisitions remain in the current window. */
    @Synchronized
    fun hasHeadroom(slots: Int): Boolean {
        val now = clock()
        while (timestamps.isNotEmpty() && (now - timestamps.first()) > windowMs) {
            timestamps.removeFirst()
        }
        return maxRequests - timestamps.size >= slots
    }

    @Synchronized
    fun reset(maxRequests: Int = this.maxRequests) {
        require(maxRequests > 0) { "maxRequests must be greater than zero" }
        this.maxRequests = maxRequests
        timestamps.clear()
    }
}


/**
 * The [StreamResolver] port bound to the proven object implementation.
 *
 * Phase 5's boundary work did not rewrite the resolver: the coalescer, rate limiter, generation-
 * guarded cache, session-change retry and VISIONOS-first ladder are all validated (ARCHITECTURE.md
 * §7). This adapter is the seam the contract requires, and the quality mapping is the only policy
 * translation — one function, from [StreamQuality] to the extractor's [AudioQuality].
 *
 * The deadlocks-free note on purpose: `resolve` never blocks; the loader-thread bridge in
 * `KaonPlaybackService` remains the single production `runBlocking`.
 */
class KaonStreamResolver : StreamResolver {

    override suspend fun resolve(request: StreamRequest): Result<ResolvedStream> {
        val result = YouTubeStreamResolver.resolveStreamData(
            videoId = request.locator.externalId,
            quality = request.quality.toExtractorQuality(),
            audioType = request.audioType,
            wifiOnly = request.wifiOnly,
            purpose = request.purpose.toResolverPurpose()
        )
        return result.map { resolved -> resolved.toResolvedStream() }
    }

    override suspend fun prewarm(
        locator: MediaLocator.Remote,
        quality: StreamQuality,
        audioType: AudioType
    ) {
        YouTubeStreamResolver.preResolve(
            videoId = locator.externalId,
            quality = quality.toExtractorQuality(),
            audioType = audioType
        )
    }

    override fun invalidate(locator: MediaLocator.Remote) {
        YouTubeStreamResolver.invalidate(locator.externalId)
    }

    private fun StreamQuality.toExtractorQuality(): AudioQuality = when (this) {
        StreamQuality.AUTO -> AudioQuality.AUTO
        StreamQuality.LOW -> AudioQuality.LOW
        StreamQuality.HIGH -> AudioQuality.HIGH
    }

    private fun ResolvePurpose.toResolverPurpose(): ResolvePurpose = when (this) {
        ResolvePurpose.PLAYBACK -> ResolvePurpose.PLAYBACK
        ResolvePurpose.PREFETCH -> ResolvePurpose.PREFETCH
        ResolvePurpose.DOWNLOAD -> ResolvePurpose.PLAYBACK
    }

    private fun ResolvedStreamData.toResolvedStream(): ResolvedStream {
        // Absolute expiry per ARCHITECTURE.md §8: capture time + reported TTL. The cache layer's
        // expire-parameter parsing already feeds its own absolute expiry internally; this is the
        // value the contract exposes, floored at "now" so a short TTL can never go negative.
        val expiresAt = System.currentTimeMillis() + expiresInSeconds.coerceAtLeast(0) * 1000L
        return ResolvedStream(
            url = url,
            headers = headers,
            clientName = clientName,
            expiresAtEpochMs = expiresAt,
            requireBoundedRange = requireBoundedRange,
            rangeChunkSizeBytes = rangeChunkSizeBytes,
            useRangeChunks = useRangeChunks
        )
    }
}
