package com.kaon.music.media.artwork

import androidx.annotation.ColorInt

/**
 * Palette colors extracted from album artwork, stored as platform-level @ColorInt values.
 * Converted to Compose Color only in the UI layer — no Compose dependency here.
 */
data class ArtworkColors(
    @ColorInt val dominant: Int,
    @ColorInt val vibrant: Int,
    @ColorInt val muted: Int,
    @ColorInt val onDominant: Int
) {
    companion object {
        /** Fallback rose/blush pink palette matching the design mockup */
        val FALLBACK = ArtworkColors(
            dominant = 0xFFF0C4BC.toInt(),
            vibrant = 0xFFE8A0A0.toInt(),
            muted = 0xFFF5C6C6.toInt(),
            onDominant = 0xFF1C1B1B.toInt()
        )
    }
}
