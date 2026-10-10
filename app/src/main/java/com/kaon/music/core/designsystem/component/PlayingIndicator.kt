package com.kaon.music.core.designsystem.component

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kaon.music.core.designsystem.theme.KaonPrimary

/**
 * Three bars marking the row where sound is playing — the visual shorthand for "here". The bars
 * bounce only while audio actually is playing; paused rows hold a static low bar, and a zero
 * animator-duration scale (accessibility "remove animations") freezes them entirely.
 */
@Composable
fun PlayingIndicator(
    modifier: Modifier = Modifier,
    color: Color = KaonPrimary,
    isPlaying: Boolean = true
) {
    val context = LocalContext.current
    val reducedMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }

    val heights: List<Float> = if (reducedMotion || !isPlaying) {
        listOf(0.3f, 0.3f, 0.3f)
    } else {
        val transition = rememberInfiniteTransition(label = "PlayingBars")
        val bar1 by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
            label = "Bar1"
        )
        val bar2 by transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(tween(560, 140), RepeatMode.Reverse),
            label = "Bar2"
        )
        val bar3 by transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(480, 280), RepeatMode.Reverse),
            label = "Bar3"
        )
        listOf(bar1, bar2, bar3)
    }

    Row(
        modifier = modifier.height(14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { fraction ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 1.dp)
                    .width(3.dp)
                    .height((14 * fraction).dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
    }
}
