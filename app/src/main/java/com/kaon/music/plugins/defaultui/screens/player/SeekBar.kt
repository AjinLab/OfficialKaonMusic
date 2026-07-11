package com.kaon.music.plugins.defaultui.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kaon.music.plugins.defaultui.util.FormatUtils

@Composable
fun SeekBar(
    position: Long,
    duration: Long,
    bufferedPosition: Long = 0L,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    tintColor: Color = MaterialTheme.colorScheme.primary
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    val currentProgress = if (isDragging) {
        dragPosition
    } else {
        if (duration > 0) position.toFloat() / duration.toFloat() else 0f
    }

    val bufferedProgress = if (duration > 0) bufferedPosition.toFloat() / duration.toFloat() else 0f

    val displayPosition = if (isDragging) {
        (dragPosition * duration).toLong()
    } else {
        position
    }

    Column(modifier = modifier) {
        Slider(
            value = currentProgress,
            onValueChange = {
                isDragging = true
                dragPosition = it
            },
            onValueChangeFinished = {
                isDragging = false
                onSeek((dragPosition * duration).toLong())
            },
            colors = SliderDefaults.colors(
                thumbColor = tintColor,
                activeTrackColor = tintColor,
                inactiveTrackColor = tintColor.copy(alpha = 0.24f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = FormatUtils.formatDuration(displayPosition),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = FormatUtils.formatDuration(duration),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
