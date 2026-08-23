package com.kaon.music.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaon.music.core.artwork.SizeBucket
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.designsystem.component.ArtworkImage
import com.kaon.music.core.designsystem.component.formatDuration
import com.kaon.music.core.designsystem.theme.KaonBackground
import com.kaon.music.core.designsystem.theme.KaonCardBackground
import com.kaon.music.core.designsystem.theme.KaonDivider
import com.kaon.music.core.designsystem.theme.KaonHeartRed
import com.kaon.music.core.designsystem.theme.KaonPrimary
import com.kaon.music.core.designsystem.theme.KaonSurfaceElevated
import com.kaon.music.core.designsystem.theme.KaonTextPrimary
import com.kaon.music.core.designsystem.theme.KaonTextSecondary
import com.kaon.music.core.playback.model.PlaybackState
import com.kaon.music.core.playback.model.RepeatMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerOverlay(
    playbackState: PlaybackState,
    isExpanded: Boolean,
    onCollapse: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onMoveQueueItem: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onRemoveQueueItem: (index: Int) -> Unit = {},
    onClearQueue: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isExpanded,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        val currentTrack = playbackState.currentTrack
        if (currentTrack == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(KaonBackground)
            )
            return@AnimatedVisibility
        }

        BackHandler(onBack = onCollapse)

        var isSeeking by remember { mutableStateOf(false) }
        var seekPositionFraction by remember { mutableFloatStateOf(0f) }
        var isQueueSheetVisible by remember { mutableStateOf(false) }
        var isInfoDialogVisible by remember { mutableStateOf(false) }

        val duration = playbackState.durationMs.coerceAtLeast(1L)
        val currentPos = if (isSeeking) {
            (seekPositionFraction * duration).toLong()
        } else {
            playbackState.playbackPositionMs
        }
        val currentFraction = (currentPos.toFloat() / duration).coerceIn(0f, 1f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(KaonBackground)
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Navigation Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse Player",
                        tint = KaonTextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "PLAYING FROM ALBUM",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = KaonTextSecondary
                    )
                    Text(
                        text = currentTrack.displayAlbum,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = KaonTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = { isInfoDialogVisible = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = KaonTextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Center Artwork Hero
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shadow(elevation = 16.dp, shape = RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
            ) {
                ArtworkImage(
                    albumId = currentTrack.albumId,
                    sizeBucket = SizeBucket.FULL,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 20.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Metadata & Heart Toggle Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTrack.displayTitle,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = KaonTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentTrack.displayArtist,
                        style = MaterialTheme.typography.titleMedium,
                        color = KaonTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { onToggleFavorite(currentTrack.id) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (currentTrack.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (currentTrack.isFavorite) KaonHeartRed else KaonTextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Scrubber Slider & Timestamps
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = if (isSeeking) seekPositionFraction else currentFraction,
                    onValueChange = { fraction ->
                        isSeeking = true
                        seekPositionFraction = fraction
                    },
                    onValueChangeFinished = {
                        val seekTargetMs = (seekPositionFraction * duration).toLong()
                        onSeekTo(seekTargetMs)
                        isSeeking = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = KaonPrimary,
                        activeTrackColor = KaonPrimary,
                        inactiveTrackColor = KaonDivider
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(currentPos),
                        style = MaterialTheme.typography.bodySmall,
                        color = KaonTextSecondary
                    )
                    Text(
                        text = formatDuration(duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = KaonTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Playback Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Repeat Mode Button
                IconButton(onClick = onToggleRepeat) {
                    Icon(
                        imageVector = if (playbackState.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (playbackState.repeatMode != RepeatMode.OFF) KaonPrimary else KaonTextSecondary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Previous Button
                IconButton(
                    onClick = onSkipPreviousClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Track",
                        tint = KaonTextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Large Prominent Play/Pause Button (Coral Blush #F0A89C)
                Surface(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onPlayPauseClick),
                    shape = CircleShape,
                    color = KaonPrimary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            tint = Color(0xFF141418),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Next Button
                IconButton(
                    onClick = onSkipNextClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Track",
                        tint = KaonTextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Shuffle Button
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playbackState.isShuffleEnabled) KaonPrimary else KaonTextSecondary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Actions Row (Share, Heart, Queue Drawer, Track Info)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* Share action */ }) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share",
                        tint = KaonTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(onClick = { onToggleFavorite(currentTrack.id) }) {
                    Icon(
                        imageVector = if (currentTrack.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (currentTrack.isFavorite) KaonHeartRed else KaonTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(onClick = { isQueueSheetVisible = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Queue",
                        tint = if (isQueueSheetVisible) KaonPrimary else KaonTextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = { isInfoDialogVisible = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Details",
                        tint = KaonTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Track Info Dialog
        if (isInfoDialogVisible) {
            AlertDialog(
                onDismissRequest = { isInfoDialogVisible = false },
                title = {
                    Text(
                        text = "Track Details",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = KaonTextPrimary
                    )
                },
                text = {
                    Column {
                        Text("Title: ${currentTrack.displayTitle}", color = KaonTextPrimary, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Artist: ${currentTrack.displayArtist}", color = KaonTextSecondary, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Album: ${currentTrack.displayAlbum}", color = KaonTextSecondary, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Duration: ${formatDuration(currentTrack.durationMs)}", color = KaonTextSecondary, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Size: ${currentTrack.sizeBytes / (1024 * 1024)} MB", color = KaonTextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { isInfoDialogVisible = false }) {
                        Text("Close", color = KaonPrimary)
                    }
                },
                containerColor = KaonSurfaceElevated
            )
        }

        // Interactive Queue Bottom Sheet
        if (isQueueSheetVisible) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { isQueueSheetVisible = false },
                sheetState = sheetState,
                containerColor = KaonSurfaceElevated,
                dragHandle = null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Now Playing Queue (${playbackState.queue.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = KaonTextPrimary
                        )

                        Row {
                            TextButton(
                                onClick = {
                                    onClearQueue()
                                    isQueueSheetVisible = false
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = KaonHeartRed)
                            ) {
                                Text("Clear")
                            }
                            IconButton(onClick = { isQueueSheetVisible = false }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = KaonTextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                    ) {
                        itemsIndexed(
                            items = playbackState.queue,
                            key = { index, track -> "${track.id}_$index" }
                        ) { index, track ->
                            val isCurrent = index == playbackState.currentIndex
                            QueueItemRow(
                                track = track,
                                index = index,
                                totalCount = playbackState.queue.size,
                                isCurrent = isCurrent,
                                onToggleFavorite = { onToggleFavorite(track.id) },
                                onMoveUp = { onMoveQueueItem(index, index - 1) },
                                onMoveDown = { onMoveQueueItem(index, index + 1) },
                                onRemove = { onRemoveQueueItem(index) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueItemRow(
    track: Track,
    index: Int,
    totalCount: Int,
    isCurrent: Boolean,
    onToggleFavorite: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCurrent) KaonCardBackground else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isCurrent) {
            Text(
                text = "▶",
                color = KaonPrimary,
                fontSize = 12.sp,
                modifier = Modifier.width(20.dp)
            )
        } else {
            Text(
                text = "${index + 1}",
                color = KaonTextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.width(20.dp)
            )
        }

        ArtworkImage(
            albumId = track.albumId,
            sizeBucket = SizeBucket.THUMBNAIL,
            modifier = Modifier.size(40.dp),
            cornerRadius = 6.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.displayTitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (isCurrent) KaonPrimary else KaonTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.displayArtist,
                style = MaterialTheme.typography.bodySmall,
                color = KaonTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (track.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (track.isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (track.isFavorite) KaonHeartRed else KaonTextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        IconButton(
            onClick = onMoveUp,
            enabled = index > 0,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowUpward,
                contentDescription = "Move Up",
                tint = if (index > 0) KaonTextSecondary else KaonTextSecondary.copy(alpha = 0.3f),
                modifier = Modifier.size(18.dp)
            )
        }

        IconButton(
            onClick = onMoveDown,
            enabled = index < totalCount - 1,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowDownward,
                contentDescription = "Move Down",
                tint = if (index < totalCount - 1) KaonTextSecondary else KaonTextSecondary.copy(alpha = 0.3f),
                modifier = Modifier.size(18.dp)
            )
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Remove",
                tint = KaonTextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
