package com.kaon.music.plugins.defaultui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.kaon.music.media.artwork.ArtworkColors
import com.kaon.music.plugins.defaultui.theme.KaonColors

/**
 * Animated three-stop vertical gradient with a subtle vertical drift.
 * Colors animate over 600ms when [artworkColors] changes.
 */
@Composable
fun GradientBackground(
    artworkColors: ArtworkColors?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val dominantBase = artworkColors?.let { Color(it.dominant) } ?: KaonColors.GradientTop
    val accent = artworkColors?.let { Color(it.vibrant) } ?: KaonColors.RosePrimary

    val animatedTop by animateColorAsState(
        targetValue = dominantBase.copy(alpha = 0.35f),
        animationSpec = tween(600), label = "gradientTop"
    )
    val animatedAccent by animateColorAsState(
        targetValue = accent.copy(alpha = 0.18f),
        animationSpec = tween(600), label = "gradientAccent"
    )
    val animatedMid by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(600), label = "gradientMid"
    )
    val animatedBottom by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surface,
        animationSpec = tween(600), label = "gradientBottom"
    )

    // Subtle drift for a "living" background.
    val transition = rememberInfiniteTransition(label = "bgDrift")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(12000), RepeatMode.Reverse),
        label = "drift"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val h = size.height
                // Drift range ±6% of height.
                val dy = h * 0.06f * (drift - 0.5f)
                val brush = Brush.verticalGradient(
                    colors = listOf(animatedTop, animatedAccent, animatedMid, animatedBottom),
                    startY = dy,
                    endY = h + dy
                )
                onDrawBehind { drawRect(brush) }
            }
    ) { content() }
}

