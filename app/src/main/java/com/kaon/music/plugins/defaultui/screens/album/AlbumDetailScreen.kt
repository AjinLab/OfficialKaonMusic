package com.kaon.music.plugins.defaultui.screens.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaon.music.core.playback.PlayerController
import com.kaon.music.media.artwork.Artwork
import com.kaon.music.media.artwork.ArtworkLoader
import com.kaon.music.media.artwork.ArtworkRepository
import com.kaon.music.media.artwork.ArtworkRequest
import com.kaon.music.media.library.LibraryController
import com.kaon.music.media.model.Song
import com.kaon.music.plugins.defaultui.components.ArtworkImage
import com.kaon.music.media.artwork.ArtworkSize
import com.kaon.music.plugins.defaultui.components.SongContextSheet
import com.kaon.music.plugins.defaultui.screens.library.SongListItem
import com.kaon.music.plugins.defaultui.util.FormatUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: Long,
    libraryController: LibraryController,
    playerController: PlayerController,
    artworkLoader: ArtworkLoader,
    artworkRepository: ArtworkRepository,
    onNavigateBack: () -> Unit,
    onNavigateToArtist: (Long) -> Unit = {},
    onNavigateToAlbum: (Long) -> Unit = {}
) {
    val albumDetail by libraryController.albumDetail(albumId).collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()
    val playbackState by playerController.playbackState.collectAsState()
    var activeSongForMenu by remember { mutableStateOf<Song?>(null) }

    val initialArtwork = remember(albumId) {
        val request = ArtworkRequest(albumId, com.kaon.music.media.artwork.ArtworkSizes.Player)
        artworkRepository.getCachedArtwork(request)?.let { Artwork.BitmapSource(it) } ?: Artwork.None
    }
    val artwork by androidx.compose.runtime.produceState<Artwork>(initialValue = initialArtwork, key1 = albumId) {
        if (initialArtwork is Artwork.None) {
            val request = ArtworkRequest(albumId, com.kaon.music.media.artwork.ArtworkSizes.Player)
            val bitmap = artworkRepository.load(request)
            value = if (bitmap != null) Artwork.BitmapSource(bitmap) else Artwork.None
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "ALBUM",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        Text(
                            text = albumDetail?.album?.title ?: "",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
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
                    Spacer(modifier = Modifier.size(48.dp)) // Balance the title
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        albumDetail?.let { detail ->
            LazyColumn(
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
                        ArtworkImage(
                            artwork = artwork,
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .aspectRatio(1f),
                            cornerRadius = 16,
                            size = com.kaon.music.media.artwork.ArtworkSizes.Player
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = detail.album.title,
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = detail.album.artistName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                )
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Play Button
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .clickable {
                                            playerController.setQueue(detail.songs, 0)
                                            playerController.play(0)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = "Play Album",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                // Shuffle Button
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            val shuffled = detail.songs.shuffled()
                                            playerController.setQueue(shuffled, 0)
                                            playerController.setShuffle(true)
                                            playerController.play(0)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shuffle,
                                        contentDescription = "Shuffle Album",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Metadata: Release year / Genre / Track count & duration
                        val year = detail.album.year ?: ""
                        val songCount = "${detail.songs.size} songs"
                        val duration = FormatUtils.formatDuration(detail.totalDuration)
                        val genre = detail.songs.firstOrNull()?.genre ?: ""
                        
                        val metaText = listOfNotNull(
                            year.takeIf { it.isNotBlank() },
                            genre.takeIf { it.isNotBlank() },
                            songCount,
                            duration
                        ).joinToString(" • ")
                        
                        Text(
                            text = metaText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                itemsIndexed(detail.songs, key = { _, song -> song.id }) { index, song ->
                    SongListItem(
                        song = song,
                        index = index,
                        onClick = {
                            playerController.setQueue(detail.songs, index)
                            playerController.play(index)
                        },
                        onFavoriteClick = {
                            coroutineScope.launch {
                                libraryController.toggleFavorite(song.id)
                            }
                        },
                        onMenuClick = {
                            activeSongForMenu = song
                        },
                        artworkRepository = artworkRepository
                    )
                }
            }
        }
    }

    if (activeSongForMenu != null) {
        SongContextSheet(
            song = activeSongForMenu!!,
            playbackState = playbackState,
            playerController = playerController,
            libraryController = libraryController,
            onDismiss = { activeSongForMenu = null },
            onGoToAlbum = { id ->
                if (id == albumId) {
                    activeSongForMenu = null
                } else {
                    onNavigateToAlbum(id)
                    activeSongForMenu = null
                }
            },
            onGoToArtist = { id ->
                onNavigateToArtist(id)
                activeSongForMenu = null
            }
        )
    }
}
