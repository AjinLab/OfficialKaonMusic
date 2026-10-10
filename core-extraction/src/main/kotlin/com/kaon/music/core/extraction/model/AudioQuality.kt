package com.kaon.music.core.extraction.model

/**
 * Requestable quality tiers (ARCHITECTURE.md §3.3).
 *
 * [HIRES] and [LOSSLESS] exist in the vocabulary now so the quality policy can *refuse* them honestly
 * (YouTube cannot serve lossless to these clients). No UI exposes them yet — that waits for a policy
 * that can answer, not one that silently downgrades.
 */
enum class AudioQuality {
    LOW,
    HIGH,
    AUTO,
    HIRES,
    LOSSLESS
}
