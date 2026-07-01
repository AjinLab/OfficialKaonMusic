package com.kaon.music.plugins.defaultui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaon.music.plugins.defaultui.theme.KaonColors

/** Tiny 3-bar equalizer animation indicating the currently playing track. */
@Composable
private fun EqualizerIndicator(active: Boolean, tint: Color, modifier: Modifier = Modifier) {
    if (!active) return
    val transition = rememberInfiniteTransition(label = "eq")
    val phases = (0..2).map { i ->
        transition.animateFloat(
            initialValue = 0.3f + i * 0.1f,
            targetValue = 1f - i * 0.15f,
            animationSpec = infiniteRepeatable(tween(380 + i * 90), RepeatMode.Reverse),
            label = "eq_$i"
        )
    }
    Row(
        modifier = modifier.size(width = 16.dp, height = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        phases.forEach { p ->
            androidx.compose.foundation.layout.Box(
                Modifier
                    .width(3.dp)
                    .height((4 + 10 * p.value).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tint)
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TrackListItem(
    trackNumber: String,
    title: String,
    artworkPath: String?,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    isPlaying: Boolean = false,
    subtitle: String? = null,
    onLongClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onPlay,
                onLongClick = onLongClick,
                onLongClickLabel = "Track options"
            )
            .background(
                if (isPlaying) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track number OR equalizer when playing
        if (isPlaying) {
            EqualizerIndicator(
                active = true,
                tint = KaonColors.RosePrimary,
                modifier = Modifier.width(28.dp)
            )
        } else {
            Text(
                text = trackNumber,
                style = MaterialTheme.typography.bodySmall,
                color = KaonColors.TextTertiary,
                modifier = Modifier.width(28.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        AsyncImage(
            path = artworkPath,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
        )

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isPlaying) KaonColors.RosePrimary
                        else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = KaonColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(onClick = onFavorite, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite
                              else Icons.Filled.FavoriteBorder,
                contentDescription = if (isFavorite) "Unfavorite" else "Favorite",
                tint = if (isFavorite) KaonColors.RosePrimary else KaonColors.TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(onClick = onMore, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = KaonColors.TextTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}