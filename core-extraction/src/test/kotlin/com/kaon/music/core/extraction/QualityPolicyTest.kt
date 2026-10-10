package com.kaon.music.core.extraction

import com.kaon.music.core.extraction.model.AudioQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The quality policy is the one place that may say "the requested quality is unavailable"
 * (ARCHITECTURE.md §3.3). These pin the honest answers, especially the refusal.
 */
class QualityPolicyTest {

    private val youtubeReality = setOf(AudioQuality.LOW, AudioQuality.HIGH, AudioQuality.AUTO)

    @Test
    fun satisfiableRequestIsSatisfied() {
        val outcome = QualityPolicy.select(AudioQuality.HIGH, youtubeReality)
        assertEquals(QualityOutcome.Satisfied(AudioQuality.HIGH), outcome)
    }

    @Test
    fun losslessIsRefusedWhateverTheProviderClaims() {
        // Even a provider that *reports* a lossless tier must be refused: YouTube cannot serve it.
        val outcome = QualityPolicy.select(
            AudioQuality.LOSSLESS,
            youtubeReality + AudioQuality.LOSSLESS
        )
        assertTrue("lossless must be Unsatisfiable", outcome is QualityOutcome.Unsatisfiable)
    }

    @Test
    fun unavailableTierDegradesToTheClosestLowerTier() {
        val outcome = QualityPolicy.select(AudioQuality.HIGH, setOf(AudioQuality.LOW, AudioQuality.AUTO))
        assertTrue(outcome is QualityOutcome.Degraded)
        outcome as QualityOutcome.Degraded
        assertEquals(AudioQuality.HIGH, outcome.requested)
        assertEquals(AudioQuality.LOW, outcome.delivered)
    }

    @Test
    fun autoNeverDegradesSilentlyToNothing() {
        val outcome = QualityPolicy.select(AudioQuality.AUTO, youtubeReality)
        assertTrue("auto always delivers", outcome is QualityOutcome.Satisfied || outcome is QualityOutcome.Degraded)
    }

    @Test
    fun emptyDeliverableSetIsUnsatisfiable() {
        val outcome = QualityPolicy.select(AudioQuality.HIGH, emptySet())
        assertTrue(outcome is QualityOutcome.Unsatisfiable)
    }

    @Test
    fun neverUpgradesSilently() {
        // LOW requested but only HIGH deliverable: must refuse rather than quietly cost the user data.
        val outcome = QualityPolicy.select(AudioQuality.LOW, setOf(AudioQuality.HIGH, AudioQuality.AUTO))
        assertTrue(outcome is QualityOutcome.Unsatisfiable)
    }
}
