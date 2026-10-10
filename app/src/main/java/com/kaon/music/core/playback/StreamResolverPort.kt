package com.kaon.music.core.playback

import com.kaon.music.core.extraction.model.AudioType
import com.kaon.music.core.model.MediaLocator
import com.kaon.music.core.extraction.model.AudioQuality

/**
 * Resolution purpose (ARCHITECTURE.md §7.2).
 *
 * Foreground playback and speculative pre-resolution share one extractor and one rate-limit window,
 * so they must be distinguishable. [DOWNLOAD] is declared now so the future download subsystem
 * reuses this contract rather than growing a second resolver.
 */
enum class ResolvePurpose {
    /** The user is waiting. Always allowed, never yields. */
    PLAYBACK,

    /** Speculative warm-up for the next queue item. Yields to [PLAYBACK] and is skipped under load. */
    PREFETCH,

    /** A download is being prepared. Declared for the download phase; unused there yet. */
    DOWNLOAD
}

/**
 * Quality requested for a resolution.
 *
 * Stored per-purpose by the settings, but folded into the cache key so a LOW prefetch never serves a
 * HIGH playback (ARCHITECTURE.md §8: locator + policy + purpose).
 */
enum class StreamQuality {
    AUTO,
    LOW,
    HIGH
}

/**
 * The resolver port: everything playback knows about streaming.
 *
 * ARCHITECTURE.md §7. Playback constructs nothing, imports nothing provider-shaped — a request in,
 * a [ResolvedStream] out. The implementation behind it (coalescing, rate limiting, the Wi-Fi gate,
 * the VISIONOS-first client ladder, PoToken) is app wiring plus the `:core:extraction` module.
 */
interface StreamResolver {
    /**
     * Resolves a playable stream for [request].
     *
     * Implementations coalesce concurrent calls for the same track and never let a caller's
     * cancellation cancel the shared work.
     */
    suspend fun resolve(request: StreamRequest): Result<ResolvedStream>

    /** Speculative warm-up for [locator]; always lower priority than [ResolvePurpose.PLAYBACK]. */
    suspend fun prewarm(locator: MediaLocator.Remote, quality: StreamQuality, audioType: AudioType)

    /** Drops cached entries for [locator] (a 403/410 recovery). */
    fun invalidate(locator: MediaLocator.Remote)
}

/**
 * One resolution request.
 *
 * [deadlineMs] is a budget the implementation should respect end-to-end; it is wall-clock, captured
 * by the caller.
 */
data class StreamRequest(
    val locator: MediaLocator.Remote,
    val quality: StreamQuality = StreamQuality.AUTO,
    val audioType: AudioType = AudioType.AUTO,
    /** `true` when the user has restricted streaming to unmetered networks. */
    val wifiOnly: Boolean = false,
    val purpose: ResolvePurpose = ResolvePurpose.PLAYBACK,
    val deadlineMs: Long = DEFAULT_DEADLINE_MS
) {
    companion object {
        const val DEFAULT_DEADLINE_MS = 25_000L
    }
}

/**
 * One resolved, playable stream.
 *
 * [expiresAtEpochMs] is an absolute instant (ARCHITECTURE.md §8): "expires in N seconds" without a
 * capture timestamp is how a stale URL survives in a cache. [requireBoundedRange] and friends reach
 * the `DataSpec` untouched — dropping them fails playback partway through with an error that looks
 * like an expired URL.
 */
data class ResolvedStream(
    val url: String,
    val headers: Map<String, String>,
    /** Which extraction client produced this (VISIONOS, WEB_REMIX, …) — the fallback-frequency observable. */
    val clientName: String,
    val expiresAtEpochMs: Long,
    val requireBoundedRange: Boolean = false,
    val rangeChunkSizeBytes: Long = 0L,
    val useRangeChunks: Boolean = false
) {
    val isExpired: Boolean get() = System.currentTimeMillis() >= expiresAtEpochMs
}

/** Legacy identifier bridge: the historical resolver entry point still used by the service bridge. */
fun StreamRequest.videoId(): String = locator.externalId
