package com.kaon.music.plugins.defaultui.screens.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaon.music.core.playback.PlayerController
import com.kaon.music.media.artwork.ArtworkLoader
import com.kaon.music.media.artwork.ArtworkRepository
import com.kaon.music.media.artwork.ArtworkRequest
import com.kaon.music.media.library.LibraryController
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.flow.distinctUntilChanged
import com.kaon.music.plugins.defaultui.screens.library.AlbumGridItem
import com.kaon.music.plugins.defaultui.screens.library.SongListItem
import com.kaon.music.plugins.defaultui.util.FormatUtils
import com.kaon.music.media.artwork.Artwork
import com.kaon.music.plugins.defaultui.components.ArtworkImage
import com.kaon.music.plugins.defaultui.components.KaonPlaybackActionRow
import com.kaon.music.plugins.defaultui.components.KaonSectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistId: Long,
    libraryController: LibraryController,
    playerController: PlayerController,
    artworkLoader: ArtworkLoader,
    artworkRepository: ArtworkRepository,
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateBack: () -> Unit
) {
    val artistDetail by libraryController.artistDetail(artistId).collectAsState(initial = null)
    val listState = rememberLazyListState()

    LaunchedEffect(listState, artistDetail) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .distinctUntilChanged()
            .collect { visibleItems ->
                val detail = artistDetail ?: return@collect
                if (visibleItems.isNotEmpty() && detail.songs.isNotEmpty()) {
                    val lastVisibleKey = visibleItems.last().key
                    val lastVisibleSongIndex = detail.songs.indexOfFirst { it.id == lastVisibleKey }
                    if (lastVisibleSongIndex != -1) {
                        for (i in 1..8) {
                            val preloadIndex = lastVisibleSongIndex + i
                            if (preloadIndex < detail.songs.size) {
                                val songToPreload = detail.songs[preloadIndex]
                                artworkRepository.preload(
                                    ArtworkRequest(songToPreload.albumId, com.kaon.music.media.artwork.ArtworkSizes.Thumbnail, songToPreload)
                                )
                            }
                        }
                    }
                }
            }
    }

    var artwork by remember(artistId) { mutableStateOf<Artwork>(Artwork.None) }

    LaunchedEffect(artistDetail) {
        val firstSong = artistDetail?.songs?.firstOrNull()
        if (firstSong != null) {
            val result = artworkLoader.load(firstSong)
            artwork = result.artwork
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = artistDetail?.artist?.name ?: "Artist Details",
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
        artistDetail?.let { detail ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding() + 86.dp
                )
            ) {
                // Header details
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Large Avatar Circle Placeholder
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            if (artwork is Artwork.FileSource || artwork is Artwork.BitmapSource) {
                                ArtworkImage(
                                    artwork = artwork,
                                    modifier = Modifier.fillMaxSize(),
                                    cornerRadius = 60,
                                    size = com.kaon.music.media.artwork.ArtworkSizes.MiniPlayer
                                )
                            } else {
                                Text(
                                    text = detail.artist.name.take(1).uppercase(),
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = detail.artist.name,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val years = detail.albums.mapNotNull { it.year?.toIntOrNull() }.distinct().sorted()
                        val yearsActive = when {
                            years.isEmpty() -> ""
                            years.size == 1 -> " • Active: ${years.first()}"
                            else -> " • Active: ${years.first()} - ${years.last()}"
                        }

                        Text(
                            text = "${detail.albums.size} albums • ${detail.songs.size} songs • ${FormatUtils.formatDuration(detail.totalDuration)} total$yearsActive",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        KaonPlaybackActionRow(
                            onPlay = {
                                playerController.setQueue(detail.songs, 0)
                                playerController.play(0)
                            },
                            onShuffle = {
                                val shuffled = detail.songs.shuffled()
                                playerController.setQueue(shuffled, 0)
                                playerController.setShuffle(true)
                                playerController.play(0)
                            },
                            enabled = detail.songs.isNotEmpty(),
                            contentPadding = PaddingValues(0.dp)
                        )
                    }
                }

                // Albums horizontal list
                if (detail.albums.isNotEmpty()) {
                    item {
                        KaonSectionHeader(title = "Albums")
                        
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(detail.albums, key = { it.id }) { album ->
                                AlbumGridItem(
                                    album = album,
                                    onClick = { onNavigateToAlbum(album.id) },
                                    artworkRepository = artworkRepository,
                                    modifier = Modifier.width(160.dp)
                                )
                            }
                        }
                    }
                }

                // Songs list
                if (detail.songs.isNotEmpty()) {
                    item {
                        KaonSectionHeader(title = "Songs")
                    }

                    itemsIndexed(detail.songs, key = { _, song -> song.id }) { index, song ->
                        SongListItem(
                            song = song,
                            index = index,
                            onClick = {
                                playerController.setQueue(detail.songs, index)
                                playerController.play(index)
                            },
                            artworkRepository = artworkRepository
                        )
                    }
                }
            }
        }
    }
}
