package com.kaon.music.plugins.defaultui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design tokens for the Kaon Music rose/blush pink theme.
 * These are the fallback colors used when artwork palette extraction fails.
 */
object KaonColors {
    // Primary rose palette
    val RosePrimary = Color(0xFFE8A0A0)
    val RoseLight = Color(0xFFF5C6C6)
    val RoseSurface = Color(0xFFFDF0EF)

    // Gradient fallback
    val GradientTop = Color(0xFFF0C4BC)
    val GradientBottom = Color(0xFFFEFBFA)

    // Text
    val TextPrimary = Color(0xFF1C1B1B)
    val TextSecondary = Color(0xFF7A7575)
    val TextTertiary = Color(0xFF9E9999)

    // Progress / track
    val TrackInactive = Color(0xFFE8E0DF)

    // Navigation
    val NavInactive = Color(0xFF9E9999)
    val NavActive = Color(0xFF1C1B1B)

    // Surface
    val Surface = Color(0xFFFFFBFA)
    val SurfaceVariant = Color(0xFFF5EDEC)

    // Divider
    val Divider = Color(0xFFF0E8E7)
}
