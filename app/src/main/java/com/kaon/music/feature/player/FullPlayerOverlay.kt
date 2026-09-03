package com.kaon.music.feature.player

import android.content.Intent
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
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
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaon.music.core.artwork.SizeBucket
import com.kaon.music.core.data.model.LyricsResult
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
import com.kaon.music.core.designsystem.theme.KaonTextTertiary
import com.kaon.music.core.playback.model.NowPlaying
import com.kaon.music.core.playback.model.PlaybackProgress
import com.kaon.music.core.playback.model.PlaybackQueue
import com.kaon.music.core.playback.model.RepeatMode
import kotlinx.coroutines.flow.StateFlow

/**
 * Expanded player.
 *
 * ARCHITECTURE.md §3.2: takes [nowPlaying] and [queue] as values, but [progressFlow] as a flow. The
 * position ticks every 500 ms; collecting it here would recompose this entire 300-line column —
 * artwork, queue item provider, lyrics list — twice per second, and did so even while collapsed.
 * Only [PlayerScrubber] and [SyncedLyrics] observe it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerOverlay(
    nowPlaying: NowPlaying,
    queue: PlaybackQueue,
    progressFlow: StateFlow<PlaybackProgress>,
    isExpanded: Boolean,
    onCollapse: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onMoveQueueItem: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onRemoveQueueItem: (index: Int) -> Unit = {},
    onClearQueue: () -> Unit = {},
    onSaveQueueAsPlaylist: (String) -> Unit = {},
    lyrics: LyricsResult? = null,
    isLoadingLyrics: Boolean = false,
    onRefreshLyrics: () -> Unit = {}
) {
    AnimatedVisibility(
        visible = isExpanded && nowPlaying.currentTrack != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier.fillMaxSize()
    ) {
        // Null only during the exit animation after the queue is cleared; drawing nothing
        // avoids an opaque, non-dismissable panel.
        val currentTrack = nowPlaying.currentTrack ?: return@AnimatedVisibility

        BackHandler(onBack = onCollapse)

        val context = LocalContext.current
        var isQueueSheetVisible by remember { mutableStateOf(false) }
        var isLyricsSheetVisible by remember { mutableStateOf(false) }
        var isInfoDialogVisible by remember { mutableStateOf(false) }
        var isSavePlaylistDialogVisible by remember { mutableStateOf(false) }
        var playlistName by remember { mutableStateOf("") }

        val duration = nowPlaying.durationMs.coerceAtLeast(1L)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(KaonBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
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
                    val userSettings = com.kaon.music.core.designsystem.theme.LocalUserSettings.current
                    if (userSettings.showFormatBadges) {
                        val isLosslessHighlight = currentTrack.isLossless && userSettings.showLosslessBadges
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isLosslessHighlight) KaonPrimary.copy(alpha = 0.18f) else KaonSurfaceElevated.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                if (isLosslessHighlight) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(KaonPrimary)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = currentTrack.formatBadge.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        letterSpacing = 1.1.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = if (isLosslessHighlight) KaonPrimary else KaonTextSecondary
                                )
                            }
                        }
                    }
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

            // Center Artwork Hero. Weighted so a tall square can never push the
            // transport controls off the bottom of shorter screens.
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .aspectRatio(1f)
                    .shadow(elevation = 16.dp, shape = RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
            ) {
                ArtworkImage(
                    track = currentTrack,
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

            PlayerScrubber(
                progressFlow = progressFlow,
                durationMs = duration,
                onSeekTo = onSeekTo
            )

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
                        imageVector = if (nowPlaying.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (nowPlaying.repeatMode != RepeatMode.OFF) KaonPrimary else KaonTextSecondary,
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
                            imageVector = if (nowPlaying.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (nowPlaying.isPlaying) "Pause" else "Play",
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
                        tint = if (nowPlaying.isShuffleEnabled) KaonPrimary else KaonTextSecondary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Actions Row (Share, Lyrics, Queue Drawer, Track Info)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val sendIntent = TrackShareHelper.createShareIntent(currentTrack)
                    context.startActivity(Intent.createChooser(sendIntent, "Share Track"))
                }) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share",
                        tint = KaonTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Lyrics Button (LRCLIB Integration)
                IconButton(onClick = { isLyricsSheetVisible = true }) {
                    Icon(
                        imageVector = if (isLyricsSheetVisible || lyrics?.hasLyrics == true) Icons.Filled.Lyrics else Icons.Outlined.Lyrics,
                        contentDescription = "Lyrics",
                        tint = if (isLyricsSheetVisible || lyrics?.hasLyrics == true) KaonPrimary else KaonTextSecondary,
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

        // Lyrics Bottom Sheet
        if (isLyricsSheetVisible) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { isLyricsSheetVisible = false },
                sheetState = sheetState,
                containerColor = KaonSurfaceElevated,
                dragHandle = null
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Lyrics",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = KaonTextPrimary
                            )
                            Text(
                                text = "${currentTrack.displayTitle} • ${currentTrack.displayArtist}",
                                style = MaterialTheme.typography.bodySmall,
                                color = KaonTextSecondary
                            )
                        }

                        IconButton(onClick = { isLyricsSheetVisible = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = KaonTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isLoadingLyrics) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = KaonPrimary)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Finding lyrics...", color = KaonTextSecondary, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else if (lyrics?.isInstrumental == true) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🎵 Instrumental Track\nThis song has no lyrics.",
                                textAlign = TextAlign.Center,
                                color = KaonTextSecondary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else if (lyrics != null && lyrics.syncedLyrics.isNotEmpty()) {
                        val listState = rememberLazyListState()
                        val activeLineIndex = rememberActiveLyricLine(progressFlow, lyrics)

                        LaunchedEffect(activeLineIndex) {
                            // Don't fight the user: skip auto-scroll while they are scrolling manually.
                            if (activeLineIndex >= 2 && !listState.isScrollInProgress) {
                                listState.animateScrollToItem(activeLineIndex - 2)
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(440.dp),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            itemsIndexed(lyrics.syncedLyrics, key = { index, line -> "${line.timestampMs}_$index" }) { index, line ->
                                val isCurrent = index == activeLineIndex
                                Text(
                                    text = line.text.ifBlank { "• • •" },
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = if (isCurrent) 20.sp else 16.sp
                                    ),
                                    color = if (isCurrent) KaonPrimary else KaonTextSecondary.copy(alpha = 0.55f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onSeekTo(line.timestampMs) }
                                        .padding(horizontal = 8.dp)
                                )
                            }
                        }
                    } else if (lyrics?.plainLyrics?.isNotBlank() == true) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(440.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                text = lyrics.plainLyrics,
                                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                                color = KaonTextPrimary
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "No lyrics found for this track",
                                    color = KaonTextTertiary,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = onRefreshLyrics,
                                    colors = ButtonDefaults.buttonColors(containerColor = KaonPrimary)
                                ) {
                                    Text("Retry Search")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Source: ${lyrics?.source ?: "LRCLIB • MusicMeta"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = KaonTextTertiary,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
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
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Artist: ${currentTrack.displayArtist}", color = KaonTextSecondary, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Album: ${currentTrack.displayAlbum}", color = KaonTextSecondary, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Duration: ${formatDuration(currentTrack.durationMs)}", color = KaonTextSecondary, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Format: ${currentTrack.formatBadge}", color = KaonTextSecondary, style = MaterialTheme.typography.bodyMedium)
                        if (currentTrack.mimeType != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("MIME Type: ${currentTrack.mimeType}", color = KaonTextSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (currentTrack.sizeBytes > 0L) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val sizeMb = String.format(java.util.Locale.getDefault(), "%.2f MB", currentTrack.sizeBytes / (1024.0 * 1024.0))
                            Text("Size: $sizeMb", color = KaonTextSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
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
                            text = "Now Playing Queue (${queue.tracks.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = KaonTextPrimary
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    val timeStamp = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                                    playlistName = "Queue - $timeStamp"
                                    isSavePlaylistDialogVisible = true
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = KaonPrimary)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save")
                            }
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
                            items = queue.tracks,
                            // Keyed on identity only. Embedding the index meant removing item 0
                            // changed every subsequent key, discarding item state across the mutation.
                            key = { _, track -> track.id }
                        ) { index, track ->
                            val isCurrent = index == queue.currentIndex
                            QueueItemRow(
                                track = track,
                                index = index,
                                totalCount = queue.tracks.size,
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

        // Save Queue as Playlist Dialog
        if (isSavePlaylistDialogVisible) {
            AlertDialog(
                onDismissRequest = { isSavePlaylistDialogVisible = false },
                title = {
                    Text(
                        text = "Save Queue as Playlist",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = KaonTextPrimary
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Save all ${queue.tracks.size} songs from your current queue into a new playlist.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KaonTextSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = playlistName,
                            onValueChange = { playlistName = it },
                            label = { Text("Playlist Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (playlistName.isNotBlank()) {
                                onSaveQueueAsPlaylist(playlistName.trim())
                                isSavePlaylistDialogVisible = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = KaonPrimary)
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isSavePlaylistDialogVisible = false }) {
                        Text("Cancel")
                    }
                },
                containerColor = KaonSurfaceElevated
            )
        }
    }
}

/**
 * Seek bar and timestamps — the only part of the expanded player that observes playback position.
 *
 * Seek state is local: while the user drags, the thumb follows the gesture rather than the player, and
 * the seek is committed once on release.
 */
@Composable
private fun PlayerScrubber(
    progressFlow: StateFlow<PlaybackProgress>,
    durationMs: Long,
    onSeekTo: (Long) -> Unit
) {
    val progress by progressFlow.collectAsStateWithLifecycle()
    var isSeeking by remember { mutableStateOf(false) }
    var seekPositionFraction by remember { mutableFloatStateOf(0f) }

    val displayedFraction = if (isSeeking) seekPositionFraction else progress.fraction
    val displayedPositionMs = if (isSeeking) (seekPositionFraction * durationMs).toLong() else progress.positionMs

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = displayedFraction,
            onValueChange = { fraction ->
                isSeeking = true
                seekPositionFraction = fraction
            },
            onValueChangeFinished = {
                onSeekTo((seekPositionFraction * durationMs).toLong())
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
                text = formatDuration(displayedPositionMs),
                style = MaterialTheme.typography.bodySmall,
                color = KaonTextSecondary
            )
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = KaonTextSecondary
            )
        }
    }
}

/**
 * Resolves the active synced-lyric line from playback position.
 *
 * The linear scan is unavoidable, but [derivedStateOf] confines it to ticks that actually change the
 * highlighted line, so the lyrics list is not invalidated on every 500 ms emission.
 */
@Composable
private fun rememberActiveLyricLine(
    progressFlow: StateFlow<PlaybackProgress>,
    lyrics: LyricsResult
): Int {
    val progress by progressFlow.collectAsStateWithLifecycle()
    val activeLineIndex by remember(lyrics) {
        derivedStateOf {
            // -1 while playback has not yet reached the first lyric line
            lyrics.syncedLyrics.indexOfLast { it.timestampMs <= progress.positionMs }
        }
    }
    return activeLineIndex
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
            track = track,
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
                text = "${track.displayArtist} • ${track.audioFormatLabel}",
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
