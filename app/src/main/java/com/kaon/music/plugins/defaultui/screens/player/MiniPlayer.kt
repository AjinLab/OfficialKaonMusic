package com.kaon.music.plugins.defaultui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaon.music.core.playback.PlayerController
import com.kaon.music.plugins.defaultui.components.ArtworkImage
import com.kaon.music.media.artwork.ArtworkSizes

import com.kaon.music.media.artwork.ArtworkSize

@Composable
fun MiniPlayer(
    playerController: PlayerController,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true
) {
    val currentSong by playerController.currentSong.collectAsState()
    val controls by playerController.controls.collectAsState()

    AnimatedVisibility(
        visible = isVisible && currentSong != null,
        enter = slideInVertically(initialOffsetY = { it }, animationSpec = androidx.compose.animation.core.tween(250)) + fadeIn(animationSpec = androidx.compose.animation.core.tween(250)),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = androidx.compose.animation.core.tween(250)) + fadeOut(animationSpec = androidx.compose.animation.core.tween(250)),
        modifier = modifier
    ) {
        val song = currentSong ?: return@AnimatedVisibility
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onExpand() }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtworkImage(
                artwork = song.artwork,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small),
                size = ArtworkSizes.MiniPlayer
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = { playerController.togglePlayback() }) {
                Icon(
                    imageVector = if (controls.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (controls.isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(onClick = { playerController.playNext() }) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
