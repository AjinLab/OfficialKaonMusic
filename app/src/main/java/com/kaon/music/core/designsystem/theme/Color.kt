package com.kaon.music.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Static Accent & Theme Presets
val KaonCoralPrimary = Color(0xFFF0A89C) // Warm Blush-Coral
val KaonCoralVariant = Color(0xFFE58B80)

val KaonVioletPrimary = Color(0xFF8B5CF6) // Violet Glow
val KaonVioletVariant = Color(0xFF7C3AED)

val KaonBluePrimary = Color(0xFF38BDF8) // Ocean Blue
val KaonBlueVariant = Color(0xFF0284C7)

val KaonEmeraldPrimary = Color(0xFF34D399) // Emerald Mint
val KaonEmeraldVariant = Color(0xFF059669)

@Immutable
data class KaonColors(
    val primary: Color = KaonCoralPrimary,
    val primaryVariant: Color = KaonCoralVariant,
    val secondary: Color = Color(0xFF8B5CF6),
    val background: Color = Color(0xFF0F0F11),
    val surface: Color = Color(0xFF16161A),
    val surfaceElevated: Color = Color(0xFF222228),
    val surfaceHighlight: Color = Color(0xFF2C2C34),
    val cardBackground: Color = Color(0xFF1A1A1E),
    val cardDark: Color = Color(0xFF141418),
    val textPrimary: Color = Color(0xFFFFFFFF),
    val textSecondary: Color = Color(0xFFA0A0AB),
    val textTertiary: Color = Color(0xFF6B6B76),
    val divider: Color = Color(0xFF26262E),
    val heartRed: Color = Color(0xFFF43F5E),
    val trackPlaceholder: Color = Color(0xFF202026),
    val accent: Color = Color(0xFF06B6D4),
    val accentPink: Color = Color(0xFFF43F5E)
)

val LocalKaonColors = staticCompositionLocalOf { KaonColors() }

// Dynamic Accessors for Composables (Reactively update with theme and accent changes)
val KaonPrimary: Color @Composable get() = LocalKaonColors.current.primary
val KaonPrimaryVariant: Color @Composable get() = LocalKaonColors.current.primaryVariant
val KaonSecondary: Color @Composable get() = LocalKaonColors.current.secondary
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

// Genre & Mood Gradients
val GenrePopBrush = Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)))
val GenreHipHopBrush = Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEF4444)))
val GenreRockBrush = Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFF7C2D12)))
val GenreElectronicBrush = Brush.linearGradient(listOf(Color(0xFF06B6D4), Color(0xFF3B82F6)))
val GenreRnBBrush = Brush.linearGradient(listOf(Color(0xFFEC4899), Color(0xFF831843)))
val GenreJazzBrush = Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF064E3B)))
val GenreIndieBrush = Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF4338CA)))
val GenreClassicalBrush = Brush.linearGradient(listOf(Color(0xFFD97706), Color(0xFF78350F)))
