package com.kaon.music.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.kaon.music.core.data.repository.UserSettings

val LocalUserSettings = staticCompositionLocalOf { UserSettings() }

fun getKaonColors(
    themeMode: String = "DARK",
    accentColor: String = "CORAL",
    isSystemDark: Boolean = true
): KaonColors {
    val swatch = accentSwatchFor(accentColor)
    val isAmoled = themeMode.uppercase() == "AMOLED"

    return if (isAmoled) {
        // Pure-black tier: OLED battery saving, surfaces separated by elevation alone.
        KaonColors(
            primary = swatch.accent,
            primaryVariant = swatch.variant,
            onAccent = swatch.onAccent,
            secondary = swatch.variant,
            background = Color(0xFF000000),
            surface = Color(0xFF08090B),
            surfaceElevated = Color(0xFF111318),
            surfaceHighlight = Color(0xFF1A1D23),
            cardBackground = Color(0xFF0C0E11),
            cardDark = Color(0xFF060708),
            textPrimary = Color(0xFFF2F3F5),
            textSecondary = Color(0xFF9DA3AE),
            textTertiary = Color(0xFF5C626E),
            divider = Color(0xFF1B1E24),
            heartRed = Color(0xFFF43F5E),
            trackPlaceholder = Color(0xFF101216),
            accent = swatch.accent,
            accentPink = Color(0xFFF43F5E)
        )
    } else {
        // Kaon Dark — the listening room: warm charcoal layers, accent as the only hue signal.
        KaonColors(
            primary = swatch.accent,
            primaryVariant = swatch.variant,
            onAccent = swatch.onAccent,
            secondary = swatch.variant,
            background = Color(0xFF0B0C0F),
            surface = Color(0xFF121318),
            surfaceElevated = Color(0xFF1A1C22),
            surfaceHighlight = Color(0xFF23252E),
            cardBackground = Color(0xFF14161B),
            cardDark = Color(0xFF0E1013),
            textPrimary = Color(0xFFF2F3F5),
            textSecondary = Color(0xFF9DA3AE),
            textTertiary = Color(0xFF5C626E),
            divider = Color(0xFF22242C),
            heartRed = Color(0xFFF43F5E),
            trackPlaceholder = Color(0xFF171920),
            accent = swatch.accent,
            accentPink = Color(0xFFF43F5E)
        )
    }
}

@Composable
fun KaonTheme(
    userSettings: UserSettings = UserSettings(),
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val kaonColors = getKaonColors(
        themeMode = userSettings.themeMode,
        accentColor = userSettings.accentColor,
        isSystemDark = isSystemDark
    )

    val colorScheme = darkColorScheme(
        primary = kaonColors.primary,
        onPrimary = kaonColors.onAccent,
        primaryContainer = kaonColors.surfaceElevated,
        onPrimaryContainer = kaonColors.primary,
        secondary = kaonColors.secondary,
        onSecondary = kaonColors.onAccent,
        background = kaonColors.background,
        onBackground = kaonColors.textPrimary,
        surface = kaonColors.surface,
        onSurface = kaonColors.textPrimary,
        surfaceVariant = kaonColors.surfaceElevated,
        onSurfaceVariant = kaonColors.textSecondary,
        outline = kaonColors.divider
    )

    CompositionLocalProvider(
        LocalKaonColors provides kaonColors,
        LocalUserSettings provides userSettings
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KaonTypography,
            content = content
        )
    }
}
