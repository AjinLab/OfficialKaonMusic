package com.kaon.music.plugins.defaultui.screens.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaon.music.core.playback.PlayerController
import com.kaon.music.media.manager.RepeatMode
import com.kaon.music.media.artwork.ArtworkLoader
import com.kaon.music.media.library.LibraryController
import com.kaon.music.plugins.defaultui.components.ArtworkImage
import com.kaon.music.plugins.defaultui.theme.KaonTheme
import com.kaon.music.plugins.defaultui.components.NowPlayingMenu
import com.kaon.music.plugins.defaultui.components.QueueSheet
import com.kaon.music.media.artwork.ArtworkSize
import androidx.compose.ui.platform.LocalConfiguration

import com.kaon.music.media.artwork.ArtworkRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
    controller: PlayerController,
    artworkRepository: ArtworkRepository,
    libraryController: LibraryController,
    onDismiss: () -> Unit,
    onGoToAlbum: (Long) -> Unit,
    onGoToArtist: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showMenu by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    val songState by controller.currentSong.collectAsState()
    val controlsState by controller.controls.collectAsState()
    val progressState by controller.progress.collectAsState()

    val song = songState ?: return

    val artworkScale by animateFloatAsState(
        targetValue = if (controlsState.isPlaying) 1.0f else 0.82f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "ArtworkScale"
    )

    // Dynamic background colors from artwork
    val defaultTop = MaterialTheme.colorScheme.surface
    val defaultBottom = MaterialTheme.colorScheme.background
    
    val targetTop = song.artworkColors?.let { Color(it.muted) } ?: defaultTop
    val targetBottom = song.artworkColors?.let { Color(it.dominant) } ?: defaultBottom

    val animatedTop by animateColorAsState(targetValue = targetTop, animationSpec = tween(500), label = "topColor")
    val animatedBottom by animateColorAsState(targetValue = targetBottom, animationSpec = tween(500), label = "bottomColor")

    val gradientBrush = remember(animatedTop, animatedBottom) {
        Brush.verticalGradient(listOf(animatedTop, animatedBottom))
    }

    val configuration = LocalConfiguration.current
    val screenWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { configuration.screenWidthDp.dp.roundToPx() }
    val artworkSize = remember(screenWidthPx) { ArtworkSize(screenWidthPx) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color.Transparent,
        modifier = Modifier.fillMaxSize()
    ) {
        KaonTheme(artworkColors = song.artworkColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(brush = gradientBrush)
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "ALBUM",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                            Text(
                                text = song.album,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "More",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Artwork
                    ArtworkImage(
                        artwork = song.artwork,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                            .graphicsLayer {
                                scaleX = artworkScale
                                scaleY = artworkScale
                            },
                        cornerRadius = 16,
                        size = artworkSize
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Title & Artist
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )

                        val audioInfoText = remember(song.audioInfo) {
                            song.audioInfo?.let { info ->
                                val parts = mutableListOf<String>()
                                parts.add(info.codec)
                                if (info.bitDepth != null) {
                                    parts.add("${info.bitDepth}-bit")
                                }
                                if (info.sampleRate != null) {
                                    val rateKhz = info.sampleRate / 1000.0
                                    val rateStr = if (rateKhz % 1.0 == 0.0) "${rateKhz.toInt()} kHz" else String.format("%.1f kHz", rateKhz)
                                    parts.add(rateStr)
                                } else if (info.bitrate != null) {
                                    parts.add("${info.bitrate / 1000} kbps")
                                }
                                parts.joinToString(" • ")
                            }
                        }

                        if (!audioInfoText.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = audioInfoText,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Seek Bar
                    NowPlayingSeekBar(
                        controller = controller,
                        tintColor = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Controls
                    PlayerControls(
                        isPlaying = controlsState.isPlaying,
                        onPlayPause = { controller.togglePlayback() },
                        onPrevious = { controller.playPrevious() },
                        onNext = { controller.playNext() },
                        primaryColor = MaterialTheme.colorScheme.primary,
                        onPrimaryColor = MaterialTheme.colorScheme.onPrimary,
                        iconColor = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Bottom Actions (Shuffle, Repeat, Queue)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { controller.toggleShuffle() }) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (controlsState.shuffleMode == com.kaon.music.media.manager.ShuffleMode.ON)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }

                        IconButton(onClick = { 
                            val nextMode = when (controlsState.repeatMode) {
                                RepeatMode.OFF -> RepeatMode.ALL
                                RepeatMode.ALL -> RepeatMode.ONE
                                RepeatMode.ONE -> RepeatMode.OFF
                            }
                            controller.setRepeatMode(nextMode)
                        }) {
                            Icon(
                                imageVector = if (controlsState.repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                contentDescription = "Repeat",
                                tint = if (controlsState.repeatMode == RepeatMode.OFF)
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f) 
                                else MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(onClick = { showQueue = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = "Queue",
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
            
            if (showMenu) {
                NowPlayingMenu(
                    playerController = controller,
                    onDismiss = { showMenu = false },
                    onGoToAlbum = onGoToAlbum,
                    onGoToArtist = onGoToArtist
                )
            }
            
            if (showQueue) {
                QueueSheet(
                    playerController = controller,
                    artworkRepository = artworkRepository,
                    onDismiss = { showQueue = false }
                )
            }
        }
    }
}

@Composable
fun NowPlayingSeekBar(
    controller: PlayerController,
    tintColor: Color
) {
    val progressState by controller.progress.collectAsState()
    
    SeekBar(
        position = progressState.currentPosition,
        duration = progressState.duration,
        bufferedPosition = progressState.bufferedPosition,
        onSeek = { controller.seekTo(it) },
        tintColor = tintColor
    )
}
