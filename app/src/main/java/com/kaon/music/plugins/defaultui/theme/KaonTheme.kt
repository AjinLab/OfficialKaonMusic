package com.kaon.music.plugins.defaultui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.view.WindowCompat
import com.kaon.music.media.artwork.ArtworkColors
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006D75),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA7F0F6),
    onPrimaryContainer = Color(0xFF001F23),
    secondary = Color(0xFF9C4238),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDAD5),
    onSecondaryContainer = Color(0xFF3F0400),
    tertiary = Color(0xFF705C00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE16B),
    onTertiaryContainer = Color(0xFF221B00),
    background = Color(0xFFFAFCFC),
    onBackground = Color(0xFF171D1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171D1E),
    surfaceVariant = Color(0xFFE7EFF0),
    onSurfaceVariant = Color(0xFF435254),
    outline = Color(0xFF718184),
    outlineVariant = Color(0xFFC2D0D2),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF84D3DA),
    onPrimary = Color(0xFF00363B),
    primaryContainer = Color(0xFF004F55),
    onPrimaryContainer = Color(0xFFA7F0F6),
    secondary = Color(0xFFFFB4AA),
    onSecondary = Color(0xFF5F150E),
    secondaryContainer = Color(0xFF7E2B23),
    onSecondaryContainer = Color(0xFFFFDAD5),
    tertiary = Color(0xFFE6C449),
    onTertiary = Color(0xFF3A3000),
    tertiaryContainer = Color(0xFF554600),
    onTertiaryContainer = Color(0xFFFFE16B),
    background = Color(0xFF101415),
    onBackground = Color(0xFFE0E4E5),
    surface = Color(0xFF181C1D),
    onSurface = Color(0xFFE0E4E5),
    surfaceVariant = Color(0xFF3F484A),
    onSurfaceVariant = Color(0xFFBFC8CA),
    outline = Color(0xFF899295),
    outlineVariant = Color(0xFF3F484A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val KaonShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

@Composable
fun KaonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    artworkColors: ArtworkColors? = null,
    content: @Composable () -> Unit
) {
    // If artwork colors are provided, we can dynamically override the primary/secondary
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    val dynamicScheme = if (artworkColors != null) {
        val dominant = Color(artworkColors.dominant)
        val vibrant = Color(artworkColors.vibrant)
        
        colorScheme.copy(
            primary = vibrant,
            secondary = dominant
        )
    } else {
        colorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val context = view.context
            var currentContext = context
            while (currentContext is android.content.ContextWrapper) {
                if (currentContext is Activity) {
                    break
                }
                currentContext = currentContext.baseContext
            }
            val activity = currentContext as? Activity
            activity?.window?.let { window ->
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = dynamicScheme,
        typography = KaonTypography,
        shapes = KaonShapes,
        content = content
    )
}
