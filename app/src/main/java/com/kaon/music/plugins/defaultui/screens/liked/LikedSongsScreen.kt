package com.kaon.music.plugins.defaultui.screens.liked

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FavoriteBorder
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikedSongsScreen(
    libraryController: LibraryController,
    playerController: PlayerController,
    artworkRepository: ArtworkRepository,
    onNavigateBack: () -> Unit
) {
    val songs by libraryController.favorites().collectAsState(initial = emptyList())
    val listState = rememberLazyListState()

    LaunchedEffect(listState, songs) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex >= 0 && songs.isNotEmpty()) {
                    for (i in 1..8) {
                        val preloadIndex = lastVisibleIndex + i
                        // The items list starts after header/buttons item (index 0)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Liked Songs",
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 86.dp
            )
        ) {
            item {
                KaonCollectionHeader(
                    title = "Liked Songs",
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
                            playerController.setQueue(songs.shuffled(), 0)
                            playerController.play(0)
                        }
                    },
                    enabled = songs.isNotEmpty()
                )
            }

            if (songs.isEmpty()) {
                item {
                    KaonEmptyState(
                        title = "No liked songs yet",
                        message = "Tap the heart on songs you want to find quickly later.",
                        icon = Icons.Rounded.FavoriteBorder,
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
                        artworkRepository = artworkRepository
                    )
                }
            }
        }
    }
}
