package com.kaon.music.plugins.defaultui.screens.playlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.kaon.music.plugins.defaultui.components.KaonCollectionHeader
import com.kaon.music.plugins.defaultui.components.KaonEmptyState
import com.kaon.music.plugins.defaultui.components.KaonPlaybackActionRow
import com.kaon.music.plugins.defaultui.screens.library.SongListItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    libraryController: LibraryController,
    playerController: PlayerController,
    artworkRepository: ArtworkRepository,
    onNavigateBack: () -> Unit
) {
    val playlist by remember(playlistId) {
        libraryController.observePlaylist(playlistId)
    }.collectAsState(initial = null)

    var hasLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(playlist) {
        if (playlist != null) {
            hasLoaded = true
        } else if (hasLoaded) {
            onNavigateBack()
        }
    }

    val playlistName = playlist?.name ?: ""
    val songs by libraryController.playlistSongs(playlistId).collectAsState(initial = emptyList())
    val listState = rememberLazyListState()

    LaunchedEffect(listState, songs) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex >= 0 && songs.isNotEmpty()) {
                    for (i in 1..8) {
                        val preloadIndex = lastVisibleIndex + i
                        // Songs start after header item (index 0) and playback action row (index 1)
                        val songPreloadIndex = preloadIndex - 2
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
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 86.dp)
        ) {
            item {
                KaonCollectionHeader(
                    title = playlistName,
                    subtitle = "${songs.size} songs"
                )
            }

            item {
                KaonPlaybackActionRow(
                    onPlay = {
                        if (songs.isNotEmpty()) {
                            playerController.setQueue(songs, 0)
                            playerController.play(0)
                        }
                    },
                    onShuffle = {
                        if (songs.isNotEmpty()) {
                            val shuffled = songs.shuffled()
                            playerController.setQueue(shuffled, 0)
                            playerController.setShuffle(true)
                            playerController.play(0)
                        }
                    },
                    enabled = songs.isNotEmpty()
                )
            }

            if (songs.isEmpty()) {
                item {
                    KaonEmptyState(
                        title = "No songs in this playlist",
                        message = "Add songs from a song menu to build this playlist.",
                        icon = Icons.Rounded.MusicOff,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                }
            } else {
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    SongListItem(
                        song = song,
                        index = index,
                        onClick = {
                            playerController.setQueue(songs, index)
                            playerController.play(index)
                        },
                        artworkRepository = artworkRepository,
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        libraryController.removeSongFromPlaylist(playlistId, song.id)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RemoveCircleOutline,
                                    contentDescription = "Remove ${song.title} from playlist",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.72f)
                                )
                            }
                        },
                    )
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
