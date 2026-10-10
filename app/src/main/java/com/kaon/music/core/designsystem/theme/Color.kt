package com.kaon.music.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Kaon's visual model is a darkened listening room: neutral layered surfaces, and colour only
 * where sound is — the accent is the signal. Every colour literal in the app lives here
 * (ARCHITECTURE.md §5.5); features pick from [KaonColors] roles so a palette retextures the app.
 */

data class AccentSwatch(
    val key: String,
    val name: String,
    val accent: Color,
    val variant: Color,
    // Ink drawn on top of filled accent controls. Each accent gets its own deep shade rather than
    // pure black, so the pressed/filled controls feel tinted by the accent's own hue.
    val onAccent: Color
)

// The four built-in accents. The `key` strings are persisted in DataStore — do not rename them.
val AccentSwatches = listOf(
    AccentSwatch("CORAL", "Coral Blush", Color(0xFFF0A89C), Color(0xFFE58B80), Color(0xFF2A1512)),
    AccentSwatch("VIOLET", "Violet Glow", Color(0xFF8B5CF6), Color(0xFF7C3AED), Color(0xFF17102A)),
    AccentSwatch("BLUE", "Ocean Blue", Color(0xFF38BDF8), Color(0xFF0284C7), Color(0xFF0A1A26)),
    AccentSwatch("EMERALD", "Emerald Mint", Color(0xFF34D399), Color(0xFF059669), Color(0xFF07231A))
)

fun accentSwatchFor(key: String): AccentSwatch =
    AccentSwatches.firstOrNull { it.key == key.uppercase() } ?: AccentSwatches.first()

@Immutable
data class KaonColors(
    val primary: Color = AccentSwatches.first().accent,
    val primaryVariant: Color = AccentSwatches.first().variant,
    // Ink for filled accent surfaces (play buttons, hero play glyph, selected chips' filled state).
    val onAccent: Color = AccentSwatches.first().onAccent,
    val secondary: Color = AccentSwatches.first().variant,
    val background: Color = Color(0xFF0B0C0F),
    val surface: Color = Color(0xFF121318),
    val surfaceElevated: Color = Color(0xFF1A1C22),
    val surfaceHighlight: Color = Color(0xFF23252E),
    val cardBackground: Color = Color(0xFF14161B),
    val cardDark: Color = Color(0xFF0E1013),
    val textPrimary: Color = Color(0xFFF2F3F5),
    val textSecondary: Color = Color(0xFF9DA3AE),
    val textTertiary: Color = Color(0xFF5C626E),
    val divider: Color = Color(0xFF22242C),
    val heartRed: Color = Color(0xFFF43F5E),
    val trackPlaceholder: Color = Color(0xFF171920),
    val accent: Color = AccentSwatches.first().accent,
    val accentPink: Color = Color(0xFFF43F5E)
) {
    // Derived, theme-consistent tints: the accent as a translucent wash over any surface, so
    // chips, badges and hero pools retexture with the palette instead of hardcoding hues.
    val accentTint: Color get() = accent.copy(alpha = 0.16f)
    val accentTintStrong: Color get() = accent.copy(alpha = 0.28f)
    val accentGlow: Color get() = accent.copy(alpha = 0.40f)
}

val LocalKaonColors = staticCompositionLocalOf { KaonColors() }

// Dynamic Accessors for Composables (Reactively update with theme and accent changes)
val KaonPrimary: Color @Composable get() = LocalKaonColors.current.primary
val KaonPrimaryVariant: Color @Composable get() = LocalKaonColors.current.primaryVariant
val KaonSecondary: Color @Composable get() = LocalKaonColors.current.secondary
val KaonOnAccent: Color @Composable get() = LocalKaonColors.current.onAccent
val KaonBackground: Color @Composable get() = LocalKaonColors.current.background
val KaonSurface: Color @Composable get() = LocalKaonColors.current.surface
val KaonSurfaceElevated: Color @Composable get() = LocalKaonColors.current.surfaceElevated
val KaonSurfaceHighlight: Color @Composable get() = LocalKaonColors.current.surfaceHighlight
val KaonCardBackground: Color @Composable get() = LocalKaonColors.current.cardBackground
val KaonCardDark: Color @Composable get() = LocalKaonColors.current.cardDark
val KaonTextPrimary: Color @Composable get() = LocalKaonColors.current.textPrimary
val KaonTextSecondary: Color @Composable get() = LocalKaonColors.current.textSecondary
val KaonTextTertiary: Color @Composable get() = LocalKaonColors.current.textTertiary
val KaonDivider: Color @Composable get() = LocalKaonColors.current.divider
val KaonHeartRed: Color @Composable get() = LocalKaonColors.current.heartRed
val KaonTrackPlaceholder: Color @Composable get() = LocalKaonColors.current.trackPlaceholder
val KaonAccent: Color @Composable get() = LocalKaonColors.current.accent
val KaonAccentPink: Color @Composable get() = LocalKaonColors.current.accentPink
val KaonAccentTint: Color @Composable get() = LocalKaonColors.current.accentTint
val KaonAccentTintStrong: Color @Composable get() = LocalKaonColors.current.accentTintStrong

// Genre & Mood Gradients — identity gradients for search genre tiles. Owned by the theme so no
// feature file carries colour literals.
val GenrePopBrush = Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)))
val GenreHipHopBrush = Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEF4444)))
val GenreRockBrush = Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFF7C2D12)))
val GenreElectronicBrush = Brush.linearGradient(listOf(Color(0xFF06B6D4), Color(0xFF3B82F6)))
val GenreRnBBrush = Brush.linearGradient(listOf(Color(0xFFEC4899), Color(0xFF831843)))
val GenreJazzBrush = Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF064E3B)))
val GenreIndieBrush = Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF4338CA)))
val GenreClassicalBrush = Brush.linearGradient(listOf(Color(0xFFD97706), Color(0xFF78350F)))

// Playlist-identity gradients (Liked Songs / playlist headers). These are domain conventions on
// exactly those surfaces, so they are fixed hues — but they live here, not in features.
val LikedSongsBrush = Brush.linearGradient(
    listOf(Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFFF43F5E))
)
val PlaylistHeaderBrush = Brush.linearGradient(
    listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFEC4899))
)
val AlbumFallbackBrush = Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6)))
