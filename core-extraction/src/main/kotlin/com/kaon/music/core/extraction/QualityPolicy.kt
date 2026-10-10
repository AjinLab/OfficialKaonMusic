package com.kaon.music.core.extraction

import com.kaon.music.core.extraction.model.AudioQuality

/**
 * What the user asked for versus what a provider can actually deliver.
 *
 * ARCHITECTURE.md §3.3. [Unsatisfiable] is load-bearing: YouTube serves no lossless audio to these
 * clients, so a "Lossless" setting is only honest if selection can refuse. A policy that silently
 * downgrades is how UI ends up advertising something that was never played.
 */
sealed interface QualityOutcome {
    /** The requested tier was delivered as requested. */
    data class Satisfied(val requested: AudioQuality) : QualityOutcome

    /**
     * Something was delivered, but not the requested tier. [reason] is user-presentable, not a log
     * line: "Lossless unavailable — serving High" is the honest form.
     */
    data class Degraded(
        val requested: AudioQuality,
        val delivered: AudioQuality,
        val reason: String
    ) : QualityOutcome

    /** Nothing deliverable matches the request's constraints. Playback must not start. */
    data class Unsatisfiable(val requested: AudioQuality, val reason: String) : QualityOutcome
}

/**
 * The one quality-selection policy (ARCHITECTURE.md §4: quality policy exists in exactly one function).
 *
 * Deliberately not WAVE's bitrate ranking: ranking picks *the best available* and calls it done. This
 * policy first asks whether the request's constraints can be met at all, and answers in terms the UI
 * can show truthfully.
 *
 * The ladder encodes what these clients can actually receive (spec §7): lossless does not exist for
 * YouTube, hires exists only where a provider reports it, everything else degrades downward.
 *
 * Pure function: no I/O, no provider calls — the extractor hands over what it *has* and this decides
 * what to say about it. JVM-testable by construction.
 */
object QualityPolicy {

    /**
     * Tiers a provider *can never* serve, whatever the stream metadata says. Lossless is permanent;
     * the entry exists so a future provider that can serve it changes one line, not the shape.
     */
    private val NEVER_AVAILABLE = setOf(AudioQuality.LOSSLESS)

    fun select(
        requested: AudioQuality,
        available: Set<AudioQuality>
    ): QualityOutcome {
        val deliverable = available - NEVER_AVAILABLE
        if (deliverable.isEmpty()) {
            return QualityOutcome.Unsatisfiable(requested, "No deliverable quality for this provider")
        }

        if (requested == AudioQuality.AUTO) {
            // AUTO means "best you honestly have" — not a request that can be unmet.
            val best = deliverable.maxOrNull() ?: AudioQuality.LOW
            return QualityOutcome.Satisfied(requested).takeIf { best == AudioQuality.AUTO }
                ?: QualityOutcome.Degraded(
                    requested = requested,
                    delivered = best,
                    reason = "Auto: serving ${best.name.lowercase()}"
                )
        }

        if (requested == AudioQuality.LOSSLESS) {
            return QualityOutcome.Unsatisfiable(
                requested,
                "Lossless is not available from this provider"
            )
        }

        return when (requested) {
            in deliverable -> QualityOutcome.Satisfied(requested)
            else -> {
                // Requested tier unavailable: degrade to the closest tier at or below the request,
                // preserving the user's bandwidth intent. Never *upgrade* silently.
                val downgrade = deliverable
                    .filter { it.ordinal < requested.ordinal }
                    .maxOrNull()
                if (downgrade == null) {
                    QualityOutcome.Unsatisfiable(
                        requested,
                        "${requested.name.lowercase()} unavailable and no lower tier exists"
                    )
                } else {
                    QualityOutcome.Degraded(
                        requested,
                        downgrade,
                        "${requested.name.lowercase()} unavailable — serving ${downgrade.name.lowercase()}"
                    )
                }
            }
        }
    }
}
