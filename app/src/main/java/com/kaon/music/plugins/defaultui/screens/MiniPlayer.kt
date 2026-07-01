package com.kaon.music.plugins.defaultui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaon.music.plugins.defaultui.components.AsyncImage
import com.kaon.music.plugins.defaultui.components.PlayButton
import com.kaon.music.plugins.defaultui.components.SlimProgressBar
import com.kaon.music.plugins.defaultui.theme.KaonColors
import com.kaon.music.plugins.defaultui.viewmodels.PlaybackViewModel

/**
 * Mini player bar shown at the bottom of the screen (above bottom nav).
 * Matching screenshot 2: title + artist left, transport right, thin progress underline.
 */
@Composable
fun MiniPlayer(
    viewModel: PlaybackViewModel,
    onClick: () -> Unit
) {
    val state by viewModel.playbackState.collectAsState()

    if (state.currentSong == null) return

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album art thumbnail
                AsyncImage(
                    path = state.currentSong?.artworkPath,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp))
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Title + Artist
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = state.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = KaonColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Transport controls
                IconButton(
                    onClick = { viewModel.previous() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                PlayButton(
                    isPlaying = state.isPlaying,
                    onClick = { viewModel.togglePlayback() },
                    artworkColors = state.artworkColors,
                    size = 40.dp,
                    iconSize = 20.dp
                )

                IconButton(
                    onClick = { viewModel.next() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Thin progress underline
            SlimProgressBar(
                progress = if (state.duration > 0) {
                    state.currentPosition.toFloat() / state.duration.toFloat()
                } else 0f,
                onSeek = { fraction ->
                    viewModel.seekTo((fraction * state.duration).toLong())
                },
                artworkColors = state.artworkColors,
                idleHeight = 2f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            )
        }
    }
}
