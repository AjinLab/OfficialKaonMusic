package com.kaon.music.plugins.defaultui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaon.music.media.artwork.ArtworkColors
import com.kaon.music.plugins.defaultui.components.AsyncImage
import com.kaon.music.plugins.defaultui.components.PlayButton
import com.kaon.music.plugins.defaultui.components.SlimProgressBar
import com.kaon.music.plugins.defaultui.theme.KaonColors
import com.kaon.music.plugins.defaultui.viewmodels.PlaybackViewModel

@Composable
fun PlayerScreen(
    viewModel: PlaybackViewModel,
    onNavigateUp: () -> Unit
) {
    val state by viewModel.playbackState.collectAsState()
    val artworkColors = state.artworkColors

    val backgroundTop = artworkColors?.let { Color(it.dominant) } ?: KaonColors.GradientTop
    val backgroundBottom = artworkColors?.let { Color(it.muted) } ?: KaonColors.GradientBottom

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(backgroundTop, backgroundBottom)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            PlayerTopBar(
                albumName = state.album,
                onBack = onNavigateUp,
                onOpenQueue = { /* TODO: open queue */ }
            )

            Spacer(Modifier.height(16.dp))

            AlbumArtwork(artworkPath = state.currentSong?.artworkPath, artworkColors = artworkColors)

            Spacer(Modifier.height(28.dp))

            TrackInfo(title = state.title, artist = state.artist)

            Spacer(Modifier.height(20.dp))

            ProgressSection(
                positionMs = state.currentPosition,
                durationMs = state.duration,
                onSeek = { fraction -> viewModel.seekTo((fraction * state.duration).toLong()) },
                artworkColors = artworkColors
            )

            Spacer(Modifier.height(12.dp))

            PlayerControls(
                isPlaying = state.isPlaying,
                shuffleOn = state.shuffleMode == com.kaon.music.media.manager.ShuffleMode.ON,
                onShuffle = { viewModel.toggleShuffle() },
                onPrev = { viewModel.previous() },
                onPlayPause = { viewModel.togglePlayback() },
                onNext = { viewModel.next() },
                onRepeatMode = state.repeatMode,
                onRepeat = { viewModel.cycleRepeatMode() },
                artworkColors = artworkColors
            )

            Spacer(Modifier.height(24.dp))

            val upNext = state.queue.getOrNull(state.currentIndex + 1)
            UpNextCard(
                title = upNext?.title ?: "—",
                artist = upNext?.artist ?: "",
                onClick = { /* TODO: open queue */ },
                artworkColors = artworkColors
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PlayerTopBar(
    albumName: String,
    onBack: () -> Unit,
    onOpenQueue: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = "Collapse",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = albumName,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onOpenQueue) {
            Icon(
                Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = "Queue",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AlbumArtwork(artworkPath: String?, artworkColors: ArtworkColors?) {
    val artworkGradient = artworkColors?.let {
        Brush.radialGradient(
            colors = listOf(Color(it.vibrant), Color(it.dominant))
        )
    } ?: Brush.verticalGradient(listOf(KaonColors.RoseLight, KaonColors.RosePrimary))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(32.dp))
            .background(artworkGradient),
        contentAlignment = Alignment.Center
    ) {
        if (artworkPath != null) {
            AsyncImage(
                path = artworkPath,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Headphones,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(140.dp)
            )
        }
    }
}

@Composable
private fun TrackInfo(title: String, artist: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = artist,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProgressSection(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Float) -> Unit,
    artworkColors: ArtworkColors?
) {
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    Column {
        SlimProgressBar(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            onSeek = onSeek,
            artworkColors = artworkColors
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = formatTime(positionMs),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatTime(durationMs),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            )
        }
    }
}

@Composable
private fun PlayerControls(
    isPlaying: Boolean,
    shuffleOn: Boolean,
    onShuffle: () -> Unit,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRepeatMode: com.kaon.music.media.manager.RepeatMode,
    onRepeat: () -> Unit,
    artworkColors: ArtworkColors?
) {
    val accentColor = artworkColors?.let { Color(it.vibrant) } ?: KaonColors.RosePrimary
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        IconButton(onClick = onShuffle) {
            Icon(
                Icons.Filled.Shuffle,
                contentDescription = "Shuffle",
                tint = if (shuffleOn) accentColor
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        IconButton(onClick = onPrev) {
            Icon(
                Icons.Filled.SkipPrevious,
                contentDescription = "Previous",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(40.dp)
            )
        }
        PlayButton(
            isPlaying = isPlaying,
            onClick = onPlayPause,
            modifier = Modifier.size(72.dp),
            artworkColors = artworkColors,
            size = 72.dp,
            iconSize = 36.dp
        )
        IconButton(onClick = onNext) {
            Icon(
                Icons.Filled.SkipNext,
                contentDescription = "Next",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(40.dp)
            )
        }
        IconButton(onClick = onRepeat) {
            Icon(
                imageVector = when (onRepeatMode) {
                    com.kaon.music.media.manager.RepeatMode.OFF -> Icons.Filled.Repeat
                    com.kaon.music.media.manager.RepeatMode.ALL -> Icons.Filled.Repeat
                    com.kaon.music.media.manager.RepeatMode.ONE -> Icons.Filled.RepeatOne
                },
                contentDescription = "Repeat",
                tint = if (onRepeatMode != com.kaon.music.media.manager.RepeatMode.OFF) accentColor
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun UpNextCard(
    title: String,
    artist: String,
    onClick: () -> Unit,
    artworkColors: ArtworkColors?
) {
    val backgroundColor = artworkColors?.let { Color(it.muted).copy(alpha = 0.3f) } ?: KaonColors.SurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "UP NEXT",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (artist.isNotBlank()) {
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = "Play next",
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}
