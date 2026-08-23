package com.kaon.music.feature.library.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaon.music.core.data.model.Playlist
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.designsystem.component.TrackItem
import com.kaon.music.core.designsystem.component.formatDuration
import com.kaon.music.core.designsystem.theme.KaonBackground
import com.kaon.music.core.designsystem.theme.KaonPrimary
import com.kaon.music.core.designsystem.theme.KaonSurfaceElevated
import com.kaon.music.core.designsystem.theme.KaonTextPrimary
import com.kaon.music.core.designsystem.theme.KaonTextSecondary
import com.kaon.music.core.designsystem.theme.KaonTextTertiary

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    tracks: List<Track>,
    activeTrackId: Long?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onTrackClick: (Track, Int) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onFavoriteToggle: (Long) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onRemoveFromPlaylist: (Long) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onRenamePlaylist: (String) -> Unit,
    onDeletePlaylist: () -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    bottomPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val totalDurationMs = remember(tracks) { tracks.sumOf { it.durationMs } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KaonBackground)
            .padding(bottom = bottomPadding.calculateBottomPadding())
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = KaonTextPrimary
                )
            }
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = KaonTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Playlist Options",
                        tint = KaonTextPrimary
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename Playlist") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            showRenameDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Playlist", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            showDeleteDialog = true
                        }
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Header Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFEC4899))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = KaonTextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val infoText = buildString {
                        append("${tracks.size} ${if (tracks.size == 1) "track" else "tracks"}")
                        if (totalDurationMs > 0) {
                            append(" • ${formatDuration(totalDurationMs)}")
                        }
                    }
                    Text(
                        text = infoText,
                        style = MaterialTheme.typography.titleSmall,
                        color = KaonTextSecondary
                    )

                    if (tracks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = onPlayAll,
                                colors = ButtonDefaults.buttonColors(containerColor = KaonPrimary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play")
                            }

                            OutlinedButton(
                                onClick = onShuffleAll,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = null,
                                    tint = KaonPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Shuffle", color = KaonTextPrimary)
                            }
                        }
                    }
                }
            }

            // Track Items or Empty State
            if (tracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp, start = 32.dp, end = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Playlist is Empty",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = KaonTextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap the options menu (⋮) on any song in your library and select 'Add to Playlist'.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KaonTextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = tracks,
                    key = { index, track -> "pl_${playlist.id}_${track.id}_$index" }
                ) { index, track ->
                    PlaylistTrackRow(
                        track = track,
                        position = index + 1,
                        isPlaying = isPlaying,
                        isCurrent = track.id == activeTrackId,
                        canMoveUp = index > 0,
                        canMoveDown = index < tracks.size - 1,
                        onClick = { onTrackClick(track, index) },
                        onFavoriteToggle = { onFavoriteToggle(track.id) },
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                        onRemoveFromPlaylist = { onRemoveFromPlaylist(track.id) },
                        onAddToPlaylist = { onAddToPlaylist(track) },
                        onMoveUp = {
                            if (index > 0) {
                                val mutable = tracks.map { it.id }.toMutableList()
                                val temp = mutable[index]
                                mutable[index] = mutable[index - 1]
                                mutable[index - 1] = temp
                                onReorder(mutable)
                            }
                        },
                        onMoveDown = {
                            if (index < tracks.size - 1) {
                                val mutable = tracks.map { it.id }.toMutableList()
                                val temp = mutable[index]
                                mutable[index] = mutable[index + 1]
                                mutable[index + 1] = temp
                                onReorder(mutable)
                            }
                        }
                    )
                }
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog) {
        var newName by remember { mutableStateOf(playlist.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { if (it.length <= 50) newName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onRenamePlaylist(newName.trim())
                            showRenameDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KaonPrimary)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = KaonTextSecondary)
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Playlist?") },
            text = { Text("Are you sure you want to delete '${playlist.name}'? Your songs will remain in your library.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDeletePlaylist()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = KaonTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun PlaylistTrackRow(
    track: Track,
    position: Int,
    isPlaying: Boolean,
    isCurrent: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onRemoveFromPlaylist: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    var showTrackMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$position",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (isCurrent) KaonPrimary else KaonTextTertiary,
            modifier = Modifier.width(28.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.displayTitle,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isCurrent) KaonPrimary else KaonTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${track.displayArtist} • ${track.displayAlbum}",
                style = MaterialTheme.typography.bodyMedium,
                color = KaonTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = formatDuration(track.durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = KaonTextTertiary
        )

        Box {
            IconButton(onClick = { showTrackMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Track options",
                    tint = KaonTextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = showTrackMenu,
                onDismissRequest = { showTrackMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(if (track.isFavorite) "Remove from Favorites" else "Add to Favorites") },
                    onClick = {
                        showTrackMenu = false
                        onFavoriteToggle()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Play next") },
                    onClick = {
                        showTrackMenu = false
                        onPlayNext()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to queue") },
                    onClick = {
                        showTrackMenu = false
                        onAddToQueue()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to other playlist") },
                    onClick = {
                        showTrackMenu = false
                        onAddToPlaylist()
                    }
                )
                if (canMoveUp) {
                    DropdownMenuItem(
                        text = { Text("Move up") },
                        leadingIcon = { Icon(Icons.Default.KeyboardArrowUp, contentDescription = null) },
                        onClick = {
                            showTrackMenu = false
                            onMoveUp()
                        }
                    )
                }
                if (canMoveDown) {
                    DropdownMenuItem(
                        text = { Text("Move down") },
                        leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                        onClick = {
                            showTrackMenu = false
                            onMoveDown()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Remove from playlist", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showTrackMenu = false
                        onRemoveFromPlaylist()
                    }
                )
            }
        }
    }
}
