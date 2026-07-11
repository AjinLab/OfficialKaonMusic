package com.kaon.music.plugins.defaultui.screens.playlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kaon.music.core.playback.PlayerController
import com.kaon.music.media.artwork.ArtworkRepository
import com.kaon.music.media.artwork.ArtworkSizes
import com.kaon.music.media.artwork.ArtworkRequest
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.flow.distinctUntilChanged

import com.kaon.music.media.library.LibraryController
import com.kaon.music.plugins.defaultui.screens.library.SongListItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    playlistName: String,
    libraryController: LibraryController,
    playerController: PlayerController,
    artworkRepository: ArtworkRepository,
    onNavigateBack: () -> Unit
) {
    val songs by libraryController.playlistSongs(playlistId).collectAsState(initial = emptyList())
    val listState = rememberLazyListState()

    LaunchedEffect(listState, songs) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex >= 0 && songs.isNotEmpty()) {
                    for (i in 1..8) {
                        val preloadIndex = lastVisibleIndex + i
                        // Songs start after header item (index 0)
                        val songPreloadIndex = preloadIndex - 1
                        if (songPreloadIndex >= 0 && songPreloadIndex < songs.size) {
                            val songToPreload = songs[songPreloadIndex]
                            artworkRepository.preload(
                                ArtworkRequest(songToPreload.albumId, ArtworkSizes.Thumbnail, songToPreload)
                            )
                        }
                    }
                }
            }
    }
    val coroutineScope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = playlistName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete Playlist",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = playlistName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${songs.size} songs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = {
                                if (songs.isNotEmpty()) {
                                    playerController.setQueue(songs, 0)
                                    playerController.play(0)
                                }
                            },
                            enabled = songs.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play")
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        OutlinedButton(
                            onClick = {
                                if (songs.isNotEmpty()) {
                                    val shuffled = songs.shuffled()
                                    playerController.setQueue(shuffled, 0)
                                    playerController.setShuffle(true)
                                    playerController.play(0)
                                }
                            },
                            enabled = songs.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Shuffle")
                        }
                    }
                }
            }

            if (songs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No songs in this playlist",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    // Wrap SongListItem to have remove button instead of favorite/menu if wanted,
                    // but we can just use normal SongListItem or add remove icon
                    Box(modifier = Modifier.fillMaxWidth()) {
                        SongListItem(
                            song = song,
                            index = index,
                            onClick = {
                                playerController.setQueue(songs, index)
                                playerController.play(index)
                            },
                            artworkRepository = artworkRepository
                        )
                        // Add overlay remove button at the end
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    libraryController.removeSongFromPlaylist(playlistId, song.id)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 96.dp) // Adjust to place next to menu/favorite
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RemoveCircleOutline,
                                contentDescription = "Remove from Playlist",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Playlist") },
            text = { Text("Are you sure you want to delete the playlist \"$playlistName\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            libraryController.deletePlaylist(playlistId)
                            showDeleteConfirm = false
                            onNavigateBack()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
