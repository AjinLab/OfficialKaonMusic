package com.kaon.music.plugins.defaultui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.kaon.music.media.artwork.ArtworkColors

// Blush/Rose default palette from mockup
private val BlushPrimary = Color(0xFFE8A0A0)
private val BlushSecondary = Color(0xFFF0C4BC)
private val BlushBackground = Color(0xFFFCF4F4)
private val BlushSurface = Color(0xFFFFFFFF)
private val BlushOnBackground = Color(0xFF1C1B1B)

// Dark version
private val DarkBlushPrimary = Color(0xFFE8A0A0)
private val DarkBlushSecondary = Color(0xFFC78B8B)
private val DarkBlushBackground = Color(0xFF121212)
private val DarkBlushSurface = Color(0xFF1E1E1E)
private val DarkBlushOnBackground = Color(0xFFE0E0E0)

private val LightColorScheme = lightColorScheme(
    primary = BlushPrimary,
    secondary = BlushSecondary,
    background = BlushBackground,
    surface = BlushSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = BlushOnBackground,
    onSurface = BlushOnBackground,
    surfaceVariant = Color(0xFFF3E8E8),
    onSurfaceVariant = Color(0xFF534343)
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkBlushPrimary,
    secondary = DarkBlushSecondary,
    background = DarkBlushBackground,
    surface = DarkBlushSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = DarkBlushOnBackground,
    onSurface = DarkBlushOnBackground,
    surfaceVariant = Color(0xFF332B2B),
    onSurfaceVariant = Color(0xFFD3C5C5)
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
        content = content
    )
}
