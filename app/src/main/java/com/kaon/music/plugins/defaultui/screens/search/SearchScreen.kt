package com.kaon.music.plugins.defaultui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kaon.music.core.playback.PlayerController
import com.kaon.music.media.artwork.ArtworkRepository
import com.kaon.music.media.artwork.ArtworkSizes
import com.kaon.music.media.artwork.ArtworkRequest
import androidx.compose.foundation.lazy.rememberLazyListState
import com.kaon.music.media.library.LibraryController
import com.kaon.music.media.model.Song
import com.kaon.music.media.search.*
import com.kaon.music.plugins.defaultui.components.SongContextSheet
import com.kaon.music.plugins.defaultui.screens.library.SongListItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun SearchScreen(
    libraryController: LibraryController,
    playerController: PlayerController,
    artworkRepository: ArtworkRepository,
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateToArtist: (Long) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val queryFlow = remember { MutableStateFlow("") }
    var searchResults by remember { mutableStateOf<List<SearchItem>>(emptyList()) }
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val playbackState by playerController.playbackState.collectAsState()
    var activeSongForMenu by remember { mutableStateOf<Song?>(null) }

    LaunchedEffect(query) {
        queryFlow.value = query
    }

    LaunchedEffect(Unit) {
        queryFlow
            .debounce(300)
            .distinctUntilChanged()
            .mapLatest { q ->
                if (q.isNotBlank()) {
                    libraryController.searchAll(q).items
                } else {
                    emptyList()
                }
            }
            .collect { results ->
                searchResults = results
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Search", 
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Songs, Albums, Artists...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                shape = MaterialTheme.shapes.large
            )

            val songs = searchResults.filterIsInstance<SongResult>()
            val albums = searchResults.filterIsInstance<AlbumResult>()
            val artists = searchResults.filterIsInstance<ArtistResult>()
            val folders = searchResults.filterIsInstance<FolderResult>()

            val listState = rememberLazyListState()

            LaunchedEffect(listState, songs) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                    .distinctUntilChanged()
                    .collect { lastVisibleIndex ->
                        if (lastVisibleIndex >= 0 && songs.isNotEmpty()) {
                            for (i in 1..8) {
                                val preloadIndex = lastVisibleIndex + i
                                val songOffset = 1 // index of SearchCategoryHeader
                                val songPreloadIndex = preloadIndex - songOffset
                                if (songPreloadIndex >= 0 && songPreloadIndex < songs.size) {
                                    val songToPreload = songs[songPreloadIndex].song
                                    artworkRepository.preload(
                                        ArtworkRequest(songToPreload.albumId, ArtworkSizes.Thumbnail, songToPreload)
                                    )
                                }
                            }
                        }
                    }
            }

            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                // 1. Songs Section
                if (songs.isNotEmpty()) {
                    item {
                        SearchCategoryHeader("Songs")
                    }
                    items(songs, key = { "song_${it.song.id}" }) { item ->
                        SongListItem(
                            song = item.song,
                            index = -1,
                            onClick = {
                                playerController.playSong(item.song)
                            },
                            onFavoriteClick = {
                                coroutineScope.launch {
                                    libraryController.toggleFavorite(item.song.id)
                                }
                            },
                            onMenuClick = {
                                activeSongForMenu = item.song
                            },
                            artworkRepository = artworkRepository
                        )
                    }
                }

                // 2. Albums Section
                if (albums.isNotEmpty()) {
                    item {
                        SearchCategoryHeader("Albums")
                    }
                    items(albums, key = { "album_${it.album.id}" }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToAlbum(item.album.id) }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Album,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = item.album.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = item.album.artistName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 3. Artists Section
                if (artists.isNotEmpty()) {
                    item {
                        SearchCategoryHeader("Artists")
                    }
                    items(artists, key = { "artist_${it.artist.id}" }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToArtist(item.artist.id) }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = item.artist.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${item.artist.songCount} songs",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 4. Folders Section
                if (folders.isNotEmpty()) {
                    item {
                        SearchCategoryHeader("Folders")
                    }
                    items(folders, key = { "folder_${it.folder.path}" }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // For simplicity, just play the folder's songs
                                    coroutineScope.launch {
                                        val songsInFolder = libraryController.songs.mapLatest { allSongs ->
                                            allSongs.filter { parentPath(it.path) == item.folder.path }
                                        }.distinctUntilChanged().first()
                                        if (songsInFolder.isNotEmpty()) {
                                            playerController.setQueue(songsInFolder, 0)
                                            playerController.play(0)
                                        }
                                    }
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = java.io.File(item.folder.path).name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = item.folder.path,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val songForMenu = activeSongForMenu
    if (songForMenu != null) {
        SongContextSheet(
            song = songForMenu,
            playbackState = playbackState,
            playerController = playerController,
            libraryController = libraryController,
            onDismiss = { activeSongForMenu = null },
            onGoToAlbum = onNavigateToAlbum,
            onGoToArtist = onNavigateToArtist
        )
    }
}

@Composable
fun SearchCategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
    )
}

private fun parentPath(path: String): String {
    val lastSeparator = path.lastIndexOf('/')
    return when {
        lastSeparator > 0 -> path.substring(0, lastSeparator)
        lastSeparator == 0 -> "/"
        else -> "/"
    }
}
