package com.kaon.music.plugins.defaultui.components

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaon.music.core.playback.PlaybackState
import com.kaon.music.core.playback.PlayerController
import com.kaon.music.media.library.LibraryController
import com.kaon.music.media.model.Playlist
import com.kaon.music.media.model.Song
import com.kaon.music.plugins.defaultui.util.FormatUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongContextSheet(
    song: Song,
    playbackState: PlaybackState,
    playerController: PlayerController,
    libraryController: LibraryController,
    onDismiss: () -> Unit,
    onGoToAlbum: (Long) -> Unit,
    onGoToArtist: (Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showInfoDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }

    var audioInfo by remember(song.id) { mutableStateOf<com.kaon.music.media.codec.AudioInfo?>(null) }
    LaunchedEffect(song.id) {
        withContext(Dispatchers.IO) {
            audioInfo = com.kaon.music.media.codec.AudioMetadataExtractor.extract(
                context = context,
                uriString = song.uri,
                mimeTypeString = song.mimeType,
                path = song.path
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 8.dp)
        ) {
            // Header: Song info preview
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Context Menu Options
            ContextOptionRow(
                icon = Icons.Rounded.QueuePlayNext,
                text = "Play Next",
                onClick = {
                    playerController.playNext(song)
                    onDismiss()
                }
            )

            ContextOptionRow(
                icon = Icons.Rounded.Queue,
                text = "Add to Queue",
                onClick = {
                    val currentQueue = playbackState.queue
                    if (!currentQueue.any { it.id == song.id }) {
                        playerController.setQueue(currentQueue + song, playbackState.currentIndex)
                    }
                    onDismiss()
                }
            )

            ContextOptionRow(
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                text = "Add to Playlist",
                onClick = {
                    showPlaylistPicker = true
                }
            )

            ContextOptionRow(
                icon = Icons.Rounded.Album,
                text = "Go to Album",
                onClick = {
                    onGoToAlbum(song.albumId)
                    onDismiss()
                }
            )

            ContextOptionRow(
                icon = Icons.Rounded.Person,
                text = "Go to Artist",
                onClick = {
                    onGoToArtist(song.artistId)
                    onDismiss()
                }
            )

            ContextOptionRow(
                icon = Icons.Rounded.Share,
                text = "Share Song",
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "audio/*"
                            putExtra(Intent.EXTRA_STREAM, Uri.parse(song.uri))
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Song"))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    onDismiss()
                }
            )

            ContextOptionRow(
                icon = Icons.Rounded.Info,
                text = "Song Info",
                onClick = {
                    showInfoDialog = true
                }
            )

            ContextOptionRow(
                icon = Icons.Rounded.Delete,
                text = "Delete from Device",
                textColor = MaterialTheme.colorScheme.error,
                iconColor = MaterialTheme.colorScheme.error,
                onClick = {
                    showDeleteDialog = true
                }
            )
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { 
                showInfoDialog = false 
                onDismiss()
            },
            title = { Text("Song Info") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    InfoLabel("Title", song.title)
                    InfoLabel("Artist", song.artist)
                    InfoLabel("Album", song.album)
                    InfoLabel("Duration", FormatUtils.formatDuration(song.duration))
                    InfoLabel("File Size", Formatter.formatShortFileSize(context, song.size))
                    InfoLabel("Path", song.path)
                    
                    val info = audioInfo
                    if (info != null) {
                        InfoLabel("Codec", info.codec)
                        info.bitrate?.let { InfoLabel("Bitrate", "${it / 1000} kbps") }
                        info.sampleRate?.let { 
                            val rateKhz = it / 1000.0
                            val rateStr = if (rateKhz % 1.0 == 0.0) "${rateKhz.toInt()} kHz" else String.format("%.1f kHz", rateKhz)
                            InfoLabel("Sample Rate", rateStr)
                        }
                        info.bitDepth?.let { InfoLabel("Bit Depth", "$it-bit") }
                        info.channels?.let { InfoLabel("Channels", "$it channels") }
                    } else {
                        song.mimeType?.let { InfoLabel("Format", it) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    showInfoDialog = false 
                    onDismiss()
                }) {
                    Text("OK")
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Song") },
            text = { Text("Are you sure you want to permanently delete \"${song.title}\" from your device?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            libraryController.deleteSong(song.id)
                            showDeleteDialog = false
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPlaylistPicker) {
        PlaylistPickerDialog(
            libraryController = libraryController,
            onPlaylistSelected = { playlist ->
                coroutineScope.launch {
                    libraryController.addSongsToPlaylist(playlist.id, listOf(song.id))
                    showPlaylistPicker = false
                    onDismiss()
                }
            },
            onDismiss = {
                showPlaylistPicker = false
                onDismiss()
            }
        )
    }
}

@Composable
fun ContextOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    iconColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor
        )
    }
}

@Composable
fun InfoLabel(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPickerDialog(
    libraryController: LibraryController,
    onPlaylistSelected: (Playlist) -> Unit,
    onDismiss: () -> Unit
) {
    val playlists by libraryController.playlistsFlow().collectAsState(initial = emptyList())
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create New Playlist")
                }

                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No playlists found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(playlists) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlaylistSelected(playlist) }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${playlist.songCount} songs",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            coroutineScope.launch {
                                val id = libraryController.createPlaylist(newPlaylistName)
                                onPlaylistSelected(Playlist(id, newPlaylistName, 0))
                                showCreateDialog = false
                            }
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
