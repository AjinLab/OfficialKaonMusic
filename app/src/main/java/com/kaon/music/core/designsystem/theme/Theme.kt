package com.kaon.music.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = KaonPrimary,
    onPrimary = KaonTextPrimary,
    primaryContainer = KaonSurfaceElevated,
    onPrimaryContainer = KaonPrimary,
    secondary = KaonSecondary,
    onSecondary = KaonTextPrimary,
    background = KaonBackground,
    onBackground = KaonTextPrimary,
    surface = KaonSurface,
    onSurface = KaonTextPrimary,
    surfaceVariant = KaonSurfaceElevated,
    onSurfaceVariant = KaonTextSecondary,
    outline = KaonDivider
)

@Composable
fun KaonTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = KaonTypography,
        content = content
    )
}
