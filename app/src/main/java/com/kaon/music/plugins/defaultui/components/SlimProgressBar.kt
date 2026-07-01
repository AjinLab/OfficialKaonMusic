package com.kaon.music.plugins.defaultui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.kaon.music.media.artwork.ArtworkColors
import com.kaon.music.plugins.defaultui.theme.KaonColors
import kotlinx.coroutines.launch

/**
 * Thin seekable progress bar with:
 *  - Combined tap + drag gestures (single pointerInput)
 *  - Visible thumb that appears on touch
 *  - Height grows while dragging for better UX
 *  - Buffered-range indicator (for streaming)
 *  - Haptic feedback on drag start / seek commit
 */
@Composable
fun SlimProgressBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    artworkColors: ArtworkColors? = null,
    buffered: Float = 0f,
    idleHeight: Float = 3f,
    activeHeight: Float = 6f
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(progress) }
    val haptic = LocalHapticFeedback.current

    val animatedProgress = remember { Animatable(if (isDragging) dragProgress else progress) }
    LaunchedEffect(progress, isDragging, dragProgress) {
        if (!isDragging) animatedProgress.animateTo(progress, tween(300))
    }

    val animatedHeight by animateDpAsState(
        targetValue = (if (isDragging) activeHeight else idleHeight).dp,
        animationSpec = tween(150),
        label = "progressHeight"
    )

    val activeColor = artworkColors?.let { Color(it.vibrant) } ?: KaonColors.RosePrimary
    val inactiveColor = KaonColors.TrackInactive
    val bufferColor = inactiveColor.copy(alpha = 0.4f)

    val currentProgress = if (isDragging) dragProgress else animatedProgress.value

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(animatedHeight)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val p = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(p)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = {
                        isDragging = false
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSeek(dragProgress)
                    },
                    onDragCancel = { isDragging = false },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragProgress = (dragProgress + dragAmount / size.width).coerceIn(0f, 1f)
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        val radius = CornerRadius(h / 2f)

        // Inactive track
        drawRoundRect(color = inactiveColor, size = Size(w, h), cornerRadius = radius)

        // Buffered range
        val bufW = w * buffered.coerceIn(0f, 1f)
        if (bufW > 0f) drawRoundRect(color = bufferColor, size = Size(bufW, h), cornerRadius = radius)

        // Active track
        val actW = w * currentProgress.coerceIn(0f, 1f)
        if (actW > 0f) drawRoundRect(color = activeColor, size = Size(actW, h), cornerRadius = radius)

        // Thumb (visible only while dragging)
        if (isDragging) {
            val cx = actW
            val cy = h / 2f
            val r = (h * 0.9f)
            drawCircle(color = activeColor, radius = r, center = Offset(cx, cy))
        }
    }
}