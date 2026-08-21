package com.kaon.music.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaon.music.core.artwork.SizeBucket
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.designsystem.component.ArtworkImage
import com.kaon.music.core.designsystem.component.PlayPauseButton
import com.kaon.music.core.designsystem.component.formatDuration
import com.kaon.music.core.designsystem.theme.KaonBackground
import com.kaon.music.core.designsystem.theme.KaonDivider
import com.kaon.music.core.designsystem.theme.KaonHeartRed
import com.kaon.music.core.designsystem.theme.KaonPrimary
import com.kaon.music.core.designsystem.theme.KaonTextPrimary
import com.kaon.music.core.designsystem.theme.KaonTextSecondary
import com.kaon.music.core.designsystem.theme.KaonTextTertiary
import com.kaon.music.core.playback.model.PlaybackState
import com.kaon.music.core.playback.model.RepeatMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerOverlay(
    isExpanded: Boolean,
    playbackState: PlaybackState,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isExpanded) {
        BackHandler(onBack = onCollapse)
    }

    AnimatedVisibility(
        visible = isExpanded,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        val track = playbackState.currentTrack ?: return@AnimatedVisibility

        var isDraggingSlider by remember { mutableStateOf(false) }
        var sliderDragPosition by remember { mutableFloatStateOf(0f) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(KaonBackground)
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top App Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse Player",
                        tint = KaonTextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "NOW PLAYING",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = KaonTextSecondary
                )

                IconButton(onClick = { onToggleFavorite(track.id) }) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (track.isFavorite) KaonHeartRed else KaonTextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Large Artwork
            ArtworkImage(
                albumId = track.albumId,
                sizeBucket = SizeBucket.FULL,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp)),
                cornerRadius = 24.dp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Title & Artist
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = track.displayTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = KaonTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "${track.displayArtist} — ${track.displayAlbum}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = KaonTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Progress Slider
            val currentPos = if (isDraggingSlider) {
                (sliderDragPosition * playbackState.durationMs).toLong()
            } else {
                playbackState.playbackPositionMs
            }

            Slider(
                value = if (playbackState.durationMs > 0) {
                    (currentPos.toFloat() / playbackState.durationMs).coerceIn(0f, 1f)
                } else 0f,
                onValueChange = {
                    isDraggingSlider = true
                    sliderDragPosition = it
                },
                onValueChangeFinished = {
                    val targetMs = (sliderDragPosition * playbackState.durationMs).toLong()
                    onSeekTo(targetMs)
                    isDraggingSlider = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = KaonPrimary,
                    activeTrackColor = KaonPrimary,
                    inactiveTrackColor = KaonDivider
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Timestamps
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(currentPos),
                    style = MaterialTheme.typography.labelSmall,
                    color = KaonTextTertiary
                )
                Text(
                    text = formatDuration(playbackState.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = KaonTextTertiary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Shuffle
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Toggle Shuffle",
                        tint = if (playbackState.isShuffleEnabled) KaonPrimary else KaonTextTertiary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Previous
                IconButton(onClick = onSkipPrevious) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Track",
                        tint = KaonTextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Play/Pause
                PlayPauseButton(
                    isPlaying = playbackState.isPlaying,
                    onClick = onTogglePlayPause,
                    size = 68.dp,
                    iconSize = 40.dp
                )

                // Next
                IconButton(onClick = onSkipNext) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Track",
                        tint = KaonTextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Repeat Mode
                IconButton(onClick = onCycleRepeatMode) {
                    Icon(
                        imageVector = when (playbackState.repeatMode) {
                            RepeatMode.ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = "Repeat Mode",
                        tint = if (playbackState.repeatMode != RepeatMode.OFF) KaonPrimary else KaonTextTertiary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}
