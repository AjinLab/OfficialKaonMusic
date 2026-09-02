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
    val (primary, primaryVariant) = when (accentColor.uppercase()) {
        "VIOLET" -> KaonVioletPrimary to KaonVioletVariant
        "BLUE" -> KaonBluePrimary to KaonBlueVariant
        "EMERALD" -> KaonEmeraldPrimary to KaonEmeraldVariant
        else -> KaonCoralPrimary to KaonCoralVariant
    }

    val isAmoled = themeMode.uppercase() == "AMOLED"

    return if (isAmoled) {
        KaonColors(
            primary = primary,
            primaryVariant = primaryVariant,
            secondary = primary,
            background = Color(0xFF000000),
            surface = Color(0xFF0A0A0C),
            surfaceElevated = Color(0xFF141418),
            surfaceHighlight = Color(0xFF1C1C22),
            cardBackground = Color(0xFF0F0F12),
            cardDark = Color(0xFF08080A),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFFA0A0AB),
            textTertiary = Color(0xFF6B6B76),
            divider = Color(0xFF202026),
            heartRed = Color(0xFFF43F5E),
            trackPlaceholder = Color(0xFF141418)
        )
    } else {
        // Standard Kaon Dark
        KaonColors(
            primary = primary,
            primaryVariant = primaryVariant,
            secondary = if (accentColor.uppercase() == "VIOLET") KaonCoralPrimary else Color(0xFF8B5CF6),
            background = Color(0xFF0F0F11),
            surface = Color(0xFF16161A),
            surfaceElevated = Color(0xFF222228),
            surfaceHighlight = Color(0xFF2C2C34),
            cardBackground = Color(0xFF1A1A1E),
            cardDark = Color(0xFF141418),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFFA0A0AB),
            textTertiary = Color(0xFF6B6B76),
            divider = Color(0xFF26262E),
            heartRed = Color(0xFFF43F5E),
            trackPlaceholder = Color(0xFF202026)
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
        onPrimary = kaonColors.textPrimary,
        primaryContainer = kaonColors.surfaceElevated,
        onPrimaryContainer = kaonColors.primary,
        secondary = kaonColors.secondary,
        onSecondary = kaonColors.textPrimary,
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
