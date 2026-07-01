package com.kaon.music.plugins.defaultui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val KaonLightColorScheme = lightColorScheme(
    primary = KaonColors.RosePrimary,
    onPrimary = Color.White,
    primaryContainer = KaonColors.RoseLight,
    onPrimaryContainer = KaonColors.TextPrimary,
    secondary = KaonColors.RosePrimary,
    onSecondary = Color.White,
    secondaryContainer = KaonColors.RoseLight,
    onSecondaryContainer = KaonColors.TextPrimary,
    surface = KaonColors.Surface,
    onSurface = KaonColors.TextPrimary,
    surfaceVariant = KaonColors.SurfaceVariant,
    onSurfaceVariant = KaonColors.TextSecondary,
    background = KaonColors.Surface,
    onBackground = KaonColors.TextPrimary,
    outline = KaonColors.Divider,
    outlineVariant = KaonColors.Divider,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = KaonColors.RoseSurface,
    surfaceContainer = KaonColors.SurfaceVariant,
    surfaceContainerHigh = KaonColors.SurfaceVariant,
    surfaceContainerHighest = KaonColors.SurfaceVariant
)

private val KaonTypography = Typography(
    // Player screen: "ALBUM" label
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 1.5.sp
    ),
    // Player screen: album title in header
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        letterSpacing = 0.sp
    ),
    // Player screen: song title
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = 0.sp
    ),
    // Mini player: song title
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 0.sp
    ),
    // Artist name, body text
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.25.sp
    ),
    // Song list items
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = 0.sp
    ),
    // Time labels, track numbers
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp
    ),
    // Tab labels, bottom nav labels
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun KaonTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = KaonLightColorScheme,
        typography = KaonTypography,
        content = content
    )
}
