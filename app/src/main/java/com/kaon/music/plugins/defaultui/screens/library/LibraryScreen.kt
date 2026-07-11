package com.kaon.music.plugins.defaultui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kaon.music.core.playback.PlayerController
import com.kaon.music.media.artwork.ArtworkLoader
import com.kaon.music.media.artwork.ArtworkRepository
import com.kaon.music.media.artwork.ArtworkRequest
import com.kaon.music.media.library.LibraryController
import com.kaon.music.media.model.Artist
import com.kaon.music.media.model.Song
import com.kaon.music.plugins.defaultui.components.SongContextSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    libraryController: LibraryController,
    playerController: PlayerController,
    artworkLoader: ArtworkLoader,
    artworkRepository: ArtworkRepository,
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateToArtist: (Long) -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Songs", "Albums", "Artists", "Folders")
    val playbackState by playerController.playbackState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    var activeSongForMenu by remember { mutableStateOf<Song?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Library", 
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
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.background,
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { 
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                                )
                            ) 
                        },
                        unselectedContentColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        selectedContentColor = MaterialTheme.colorScheme.primary
                    )
                }
            }

            AnimatedContent(
                targetState = selectedTabIndex,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { width -> width } + fadeIn(animationSpec = tween(300)) togetherWith
                                slideOutHorizontally { width -> -width } + fadeOut(animationSpec = tween(300))
                    } else {
                        slideInHorizontally { width -> -width } + fadeIn(animationSpec = tween(300)) togetherWith
                                slideOutHorizontally { width -> width } + fadeOut(animationSpec = tween(300))
                    }.using(
                        SizeTransform(clip = false)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = "LibraryTabTransition"
            ) { targetIndex ->
                when (targetIndex) {
                0 -> {
                    val songs by libraryController.songs.collectAsState(initial = null)
                    var showShimmer by remember { mutableStateOf(false) }
                    
                    LaunchedEffect(songs) {
                        if (songs == null) {
                            delay(200)
                            showShimmer = true
                        } else {
                            showShimmer = false
                        }
                    }

                    if (showShimmer) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (songs != null) {
                        if (songs!!.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No songs found. Rescan in settings.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                            }
                        } else {
                            val songsListState = rememberLazyListState()
                            LaunchedEffect(songsListState, songs) {
                                snapshotFlow { songsListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                                    .distinctUntilChanged()
                                    .collect { lastVisibleIndex ->
                                        val songList = songs
                                        if (lastVisibleIndex >= 0 && songList != null && songList.isNotEmpty()) {
                                            for (i in 1..8) {
                                                val preloadIndex = lastVisibleIndex + i
                                                if (preloadIndex < songList.size) {
                                                    val songToPreload = songList[preloadIndex]
                                                    artworkRepository.preload(
                                                        ArtworkRequest(songToPreload.albumId, com.kaon.music.media.artwork.ArtworkSizes.Thumbnail, songToPreload)
                                                    )
                                                }
                                            }
                                        }
                                    }
                            }
                            LazyColumn(state = songsListState, modifier = Modifier.fillMaxSize()) {
                                itemsIndexed(songs!!, key = { _, song -> song.id }) { index, song ->
                                    SongListItem(
                                        song = song,
                                        index = index,
                                        onClick = {
                                            playerController.setQueue(songs!!, index)
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
                }
                1 -> {
                    val albums by libraryController.albums.collectAsState(initial = emptyList())
                    if (albums.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No albums found", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        }
                    } else {
                        val albumsGridState = rememberLazyGridState()
                        LaunchedEffect(albumsGridState, albums) {
                            snapshotFlow { albumsGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                                .distinctUntilChanged()
                                .collect { lastVisibleIndex ->
                                    if (lastVisibleIndex >= 0 && albums.isNotEmpty()) {
                                        for (i in 1..6) {
                                            val preloadIndex = lastVisibleIndex + i
                                            if (preloadIndex < albums.size) {
                                                val albumToPreload = albums[preloadIndex]
                                                artworkRepository.preload(
                                                    ArtworkRequest(albumToPreload.id, com.kaon.music.media.artwork.ArtworkSizes.MiniPlayer)
                                                )
                                            }
                                        }
                                    }
                                }
                        }
                        LazyVerticalGrid(
                            state = albumsGridState,
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            items(albums, key = { it.id }) { album ->
                                AlbumGridItem(
                                    album = album,
                                    onClick = { onNavigateToAlbum(album.id) },
                                    artworkRepository = artworkRepository
                                )
                            }
                        }
                    }
                }
                2 -> {
                    val artists by libraryController.artists.collectAsState(initial = emptyList())
                    if (artists.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No artists found", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(artists, key = { it.id }) { artist ->
                                ArtistListItem(
                                    artist = artist,
                                    onClick = { onNavigateToArtist(artist.id) }
                                )
                            }
                        }
                    }
                }
                3 -> {
                    val allSongs by libraryController.songs.collectAsState(initial = emptyList())
                    var currentFolder by remember { mutableStateOf<String?>(null) }
                    
                    val (folders, songsInFolder) = remember(allSongs, currentFolder) {
                        getChildFoldersAndSongs(allSongs, currentFolder)
                    }
                    val folderSongCounts = remember(allSongs) {
                        buildFolderSongCounts(allSongs)
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        if (currentFolder != null) {
                            // Back Button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // Go up a directory
                                        val parent = java.io.File(currentFolder!!).parent
                                        // If parent is empty or parent has no other songs, go to root (null)
                                        val hasGrandparent = parent != null && allSongs.any { it.path.startsWith(parent + "/") }
                                        currentFolder = if (hasGrandparent) parent else null
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = "Back",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = java.io.File(currentFolder!!).name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            HorizontalDivider()
                        }

                        if (folders.isEmpty() && songsInFolder.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                                Text("No folders found", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize().weight(1f)) {
                                items(folders, key = { it }) { path ->
                                    val name = java.io.File(path).name
                                    val count = folderSongCounts[path] ?: 0
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { currentFolder = path }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(
                                                text = name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "$count songs",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }

                                itemsIndexed(songsInFolder, key = { _, s -> s.id }) { index, song ->
                                    SongListItem(
                                        song = song,
                                        index = index,
                                        onClick = {
                                            playerController.setQueue(songsInFolder, index)
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
                }
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
            onGoToAlbum = onNavigateToAlbum,
            onGoToArtist = onNavigateToArtist
        )
    }
}

@Composable
fun ArtistListItem(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${artist.albumCount} albums • ${artist.songCount} songs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

private fun getChildFoldersAndSongs(
    allSongs: List<Song>,
    currentPath: String?
): Pair<List<String>, List<Song>> {
    if (allSongs.isEmpty()) return Pair(emptyList(), emptyList())
    
    val parentDirs = HashSet<String>()
    for (song in allSongs) {
        parentDirs.add(parentPath(song.path))
    }
    
    if (currentPath == null) {
        val roots = mutableListOf<String>()
        for (dir in parentDirs.sortedBy { it.length }) {
            if (roots.none { root -> dir.startsWith("$root/") }) {
                roots.add(dir)
            }
        }
        return Pair(roots, emptyList())
    } else {
        val childFolders = parentDirs.asSequence().filter { dir ->
            dir.startsWith(currentPath + "/") && dir.substring(currentPath.length + 1).contains("/").not()
        }.sorted().toList()
        val songsInFolder = allSongs.filter { parentPath(it.path) == currentPath }
        return Pair(childFolders, songsInFolder.sortedBy { it.title })
    }
}

private fun parentPath(path: String): String {
    val lastSeparator = path.lastIndexOf('/')
    return when {
        lastSeparator > 0 -> path.substring(0, lastSeparator)
        lastSeparator == 0 -> "/"
        else -> "/"
    }
}

private fun buildFolderSongCounts(allSongs: List<Song>): Map<String, Int> {
    if (allSongs.isEmpty()) return emptyMap()

    val counts = HashMap<String, Int>()
    for (song in allSongs) {
        var folder = parentPath(song.path)
        while (folder.isNotEmpty()) {
            counts[folder] = (counts[folder] ?: 0) + 1
            val parent = parentPath(folder)
            if (parent == folder) break
            folder = parent
        }
    }
    return counts
}
