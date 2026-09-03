package com.kaon.music.core.playback

import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Stage timings for a single stream resolution.
 *
 * ARCHITECTURE.md §5.3. Exists because "the fast path is faster" is otherwise unfalsifiable: the
 * expensive stages (player request, cipher solve, PoToken mint) are inside InnerTubeX, so the only
 * place Kaon can observe them is the wall time around its own calls.
 *
 * Recorded at DEBUG, so R8 strips it from release entirely (see proguard-rules.pro).
 */
internal class ResolutionTrace(
    private val videoId: String,
    private val clock: () -> Long
) {
    private val startedAt = clock()
    private val stages = LinkedHashMap<String, Long>()
    private var lastMark = startedAt

    fun mark(stage: String) {
        val now = clock()
        stages[stage] = now - lastMark
        lastMark = now
    }

    /** Total elapsed time, regardless of which stages were marked. */
    val elapsedMs: Long get() = clock() - startedAt

    fun log(outcome: String, clientName: String? = null) {
        val total = elapsedMs
        ResolutionMetrics.record(outcome, total)
        if (stages.isEmpty()) {
            Timber.tag(TAG).d("resolve $videoId $outcome total=${total}ms")
            return
        }
        val breakdown = stages.entries.joinToString(" ") { "${it.key}=${it.value}ms" }
        val client = clientName?.let { " client=$it" }.orEmpty()
        Timber.tag(TAG).d("resolve $videoId $outcome total=${total}ms$client $breakdown")
    }

    private companion object {
        const val TAG = "resolve"
    }
}

/**
 * Process-lifetime resolution counters, aggregated by outcome.
 *
 * Deliberately tiny: a count and a total per outcome, no percentiles and no per-video retention.
 * The point is to answer "how often does the fast path actually hit, and what does each outcome
 * cost on average" during a benchmark run or a bug report — not to build a metrics pipeline.
 */
object ResolutionMetrics {

    /** Outcome labels. Kept as constants so the benchmark and the resolver cannot drift apart. */
    const val CACHE_HIT = "cache-hit"
    const val EXTRACTED = "extracted"
    const val EXTRACTED_RETRY = "extracted-after-retry"
    const val FAILED = "failed"
    const val RATE_LIMITED = "rate-limited"
    const val WIFI_BLOCKED = "wifi-blocked"

    private val counts = ConcurrentHashMap<String, AtomicLong>()
    private val totalMs = ConcurrentHashMap<String, AtomicLong>()

    internal fun record(outcome: String, durationMs: Long) {
        counts.computeIfAbsent(outcome) { AtomicLong() }.incrementAndGet()
        totalMs.computeIfAbsent(outcome) { AtomicLong() }.addAndGet(durationMs)
    }

    fun count(outcome: String): Long = counts[outcome]?.get() ?: 0L

    fun averageMs(outcome: String): Long {
        val n = count(outcome)
        return if (n == 0L) 0L else (totalMs[outcome]?.get() ?: 0L) / n
    }

    /** Single-line summary suitable for a benchmark assertion or a bug report. */
    fun summary(): String = counts.keys.sorted().joinToString(", ") { outcome ->
        "$outcome=${count(outcome)}@${averageMs(outcome)}ms"
    }.ifEmpty { "no resolutions recorded" }

    fun reset() {
        counts.clear()
        totalMs.clear()
    }
}
