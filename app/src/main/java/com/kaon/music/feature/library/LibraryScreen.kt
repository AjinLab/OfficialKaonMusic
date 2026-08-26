package com.kaon.music.feature.library

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.sp
import com.kaon.music.core.artwork.SizeBucket
import com.kaon.music.core.data.model.Album
import com.kaon.music.core.data.model.Artist
import com.kaon.music.core.data.model.Playlist
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.designsystem.component.ArtworkImage
import com.kaon.music.core.designsystem.component.EmptyStateView
import com.kaon.music.core.designsystem.component.TrackItem
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.MusicNote
import com.kaon.music.core.designsystem.theme.KaonBackground
import com.kaon.music.core.designsystem.theme.KaonPrimary
import com.kaon.music.core.designsystem.theme.KaonSurfaceElevated
import com.kaon.music.core.designsystem.theme.KaonTextPrimary
import com.kaon.music.core.designsystem.theme.KaonTextSecondary
import com.kaon.music.core.designsystem.theme.KaonTextTertiary
import com.kaon.music.feature.library.album.AlbumDetailScreen
import com.kaon.music.feature.library.artist.ArtistDetailScreen
import com.kaon.music.feature.library.artist.ArtistListItem
import com.kaon.music.feature.library.playlist.AddToPlaylistBottomSheet
import com.kaon.music.feature.library.playlist.LikedSongsScreen
import com.kaon.music.feature.library.playlist.PlaylistDetailScreen

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    bottomPadding: PaddingValues,
    onNavigateToSearch: () -> Unit = {},
    onRequestPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var trackForAddToPlaylist by remember { mutableStateOf<Track?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var trackToAppendAfterCreate by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissUserMessage()
        }
    }

    // Detail Screen Overlays
    if (uiState.selectedPlaylist != null) {
        val playlist = uiState.selectedPlaylist!!
        PlaylistDetailScreen(
            playlist = playlist,
            tracks = uiState.playlistTracks,
            activeTrackId = uiState.activeTrackId,
            isPlaying = uiState.isPlaying,
            onBack = { viewModel.selectPlaylist(null) },
            onTrackClick = { track, index -> viewModel.playPlaylist(playlist, shuffle = false, startIndex = index) },
            onPlayAll = { viewModel.playPlaylist(playlist, shuffle = false) },
            onShuffleAll = { viewModel.playPlaylist(playlist, shuffle = true) },
            onFavoriteToggle = viewModel::toggleFavorite,
            onPlayNext = viewModel::playNext,
            onAddToQueue = viewModel::addToQueue,
            onRemoveFromPlaylist = { trackId -> viewModel.removeTrackFromPlaylist(playlist.id, trackId) },
            onReorder = { orderedIds -> viewModel.reorderPlaylistTracks(playlist.id, orderedIds) },
            onRenamePlaylist = { newName -> viewModel.renamePlaylist(playlist.id, newName) },
            onDeletePlaylist = { viewModel.deletePlaylist(playlist.id) },
            onAddToPlaylist = { track -> trackForAddToPlaylist = track },
            bottomPadding = bottomPadding,
            modifier = modifier
        )
        return
    }

    if (uiState.isLikedSongsSelected) {
        LikedSongsScreen(
            tracks = uiState.favoriteTracks,
            activeTrackId = uiState.activeTrackId,
            isPlaying = uiState.isPlaying,
            onBack = viewModel::clearLikedSongs,
            onTrackClick = { track -> viewModel.playTrack(track, uiState.favoriteTracks) },
            onPlayAll = { viewModel.playLikedSongs(shuffle = false) },
            onShuffleAll = { viewModel.playLikedSongs(shuffle = true) },
            onFavoriteToggle = viewModel::toggleFavorite,
            onPlayNext = viewModel::playNext,
            onAddToQueue = viewModel::addToQueue,
            onAddToPlaylist = { track -> trackForAddToPlaylist = track },
            bottomPadding = bottomPadding,
            modifier = modifier
        )
        return
    }

    if (uiState.selectedAlbum != null) {
        val album = uiState.selectedAlbum!!
        AlbumDetailScreen(
            album = album,
            tracks = uiState.albumTracks,
            activeTrackId = uiState.activeTrackId,
            isPlaying = uiState.isPlaying,
            onBack = viewModel::clearSelectedAlbum,
            onTrackClick = { track -> viewModel.playTrack(track, uiState.albumTracks) },
            onPlayAlbum = { viewModel.playAlbum(album, shuffle = false) },
            onShuffleAlbum = { viewModel.playAlbum(album, shuffle = true) },
            onFavoriteToggle = viewModel::toggleFavorite,
            onPlayNext = viewModel::playNext,
            onAddToQueue = viewModel::addToQueue,
            onAddToPlaylist = { track -> trackForAddToPlaylist = track },
            bottomPadding = bottomPadding,
            modifier = modifier
        )
        return
    }

    if (uiState.selectedArtist != null) {
        val artist = uiState.selectedArtist!!
        ArtistDetailScreen(
            artist = artist,
            albums = uiState.artistAlbums,
            tracks = uiState.artistTracks,
            activeTrackId = uiState.activeTrackId,
            isPlaying = uiState.isPlaying,
            onBack = viewModel::clearSelectedArtist,
            onAlbumClick = viewModel::selectAlbum,
            onTrackClick = { track -> viewModel.playTrack(track, uiState.artistTracks) },
            onPlayAll = { viewModel.playArtist(artist) },
            onFavoriteToggle = viewModel::toggleFavorite,
            onPlayNext = viewModel::playNext,
            onAddToQueue = viewModel::addToQueue,
            onAddToPlaylist = { track -> trackForAddToPlaylist = track },
            bottomPadding = bottomPadding,
            modifier = modifier
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KaonBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomPadding.calculateBottomPadding())
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateToSearch) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = KaonTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = "Your Library",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    color = KaonTextPrimary
                )

                IconButton(onClick = { viewModel.triggerSync() }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add / Sync",
                        tint = KaonTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // 6-Tab Filter Row (Tracks, Albums, Artists, Favorites, Recent, Playlists)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    LibraryChip(
                        text = "Tracks",
                        isSelected = uiState.selectedFilter == LibraryFilter.TRACKS,
                        onClick = { viewModel.selectFilter(LibraryFilter.TRACKS) }
                    )
                }
                item {
                    LibraryChip(
                        text = "Albums",
                        isSelected = uiState.selectedFilter == LibraryFilter.ALBUMS,
                        onClick = { viewModel.selectFilter(LibraryFilter.ALBUMS) }
                    )
                }
                item {
                    LibraryChip(
                        text = "Artists",
                        isSelected = uiState.selectedFilter == LibraryFilter.ARTISTS,
                        onClick = { viewModel.selectFilter(LibraryFilter.ARTISTS) }
                    )
                }
                item {
                    LibraryChip(
                        text = "Favorites",
                        isSelected = uiState.selectedFilter == LibraryFilter.FAVORITES,
                        onClick = { viewModel.selectFilter(LibraryFilter.FAVORITES) }
                    )
                }
                item {
                    LibraryChip(
                        text = "Recent",
                        isSelected = uiState.selectedFilter == LibraryFilter.RECENT,
                        onClick = { viewModel.selectFilter(LibraryFilter.RECENT) }
                    )
                }
                item {
                    LibraryChip(
                        text = "Playlists",
                        isSelected = uiState.selectedFilter == LibraryFilter.PLAYLISTS,
                        onClick = { viewModel.selectFilter(LibraryFilter.PLAYLISTS) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Content List
            if (!uiState.hasPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = KaonTextTertiary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Storage Access Required",
                            style = MaterialTheme.typography.titleMedium,
                            color = KaonTextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Kaon Music needs audio permission to scan and play your local music files.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KaonTextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onRequestPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = KaonPrimary)
                        ) {
                            Text("Grant Access")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    when (uiState.selectedFilter) {
                        LibraryFilter.TRACKS -> {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${uiState.tracks.size} tracks",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = KaonTextSecondary
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SortFilterPill(
                                            text = "A–Z",
                                            isSelected = uiState.trackSortOrder == TrackSortOrder.TITLE_ASC,
                                            onClick = { viewModel.setTrackSortOrder(TrackSortOrder.TITLE_ASC) }
                                        )
                                        SortFilterPill(
                                            text = "Added",
                                            isSelected = uiState.trackSortOrder == TrackSortOrder.RECENTLY_ADDED,
                                            onClick = { viewModel.setTrackSortOrder(TrackSortOrder.RECENTLY_ADDED) }
                                        )
                                        SortFilterPill(
                                            text = "Most Played",
                                            isSelected = uiState.trackSortOrder == TrackSortOrder.MOST_PLAYED,
                                            onClick = { viewModel.setTrackSortOrder(TrackSortOrder.MOST_PLAYED) }
                                        )
                                    }
                                }
                            }

                            if (uiState.tracks.isEmpty()) {
                                item {
                                    EmptyStateView(
                                        icon = Icons.Default.MusicNote,
                                        title = "No Tracks Found",
                                        message = "Scan your device storage to discover your local music collection.",
                                        actionLabel = "Rescan Library",
                                        onActionClick = { viewModel.triggerSync() }
                                    )
                                }
                            } else {
                                items(uiState.tracks, key = { "track_${it.id}" }) { track ->
                                    TrackItem(
                                        track = track,
                                        isPlaying = uiState.isPlaying,
                                        isCurrent = track.id == uiState.activeTrackId,
                                        onClick = { viewModel.playTrack(track) },
                                        onFavoriteToggle = { viewModel.toggleFavorite(track.id) },
                                        onPlayNext = { viewModel.playNext(track) },
                                        onAddToQueue = { viewModel.addToQueue(track) },
                                        onAddToPlaylist = { trackForAddToPlaylist = track }
                                    )
                                }
                            }
                        }

                        LibraryFilter.ALBUMS -> {
                            if (uiState.albums.isEmpty()) {
                                item {
                                    EmptyStateView(
                                        icon = Icons.Default.Album,
                                        title = "No Albums Found",
                                        message = "Albums will appear here once audio tracks with album metadata are scanned.",
                                        actionLabel = "Rescan Library",
                                        onActionClick = { viewModel.triggerSync() }
                                    )
                                }
                            } else {
                                items(uiState.albums, key = { "album_${it.albumId}" }) { album ->
                                    AlbumRowItem(
                                        album = album,
                                        onClick = { viewModel.selectAlbum(album) }
                                    )
                                }
                            }
                        }

                        LibraryFilter.ARTISTS -> {
                            if (uiState.artists.isEmpty()) {
                                item {
                                    EmptyStateView(
                                        icon = Icons.Default.Person,
                                        title = "No Artists Found",
                                        message = "Artists will appear here once audio tracks with artist metadata are scanned.",
                                        actionLabel = "Rescan Library",
                                        onActionClick = { viewModel.triggerSync() }
                                    )
                                }
                            } else {
                                items(uiState.artists, key = { "artist_${it.artistId}_${it.name}" }) { artist ->
                                    ArtistRowItem(
                                        artist = artist,
                                        onClick = { viewModel.selectArtist(artist) }
                                    )
                                }
                            }
                        }

                        LibraryFilter.FAVORITES -> {
                            if (uiState.favoriteTracks.isEmpty()) {
                                item {
                                    EmptyStateView(
                                        icon = Icons.Filled.Favorite,
                                        title = "No Favorites Yet",
                                        message = "Tap the heart icon on any track to add it to your favorites.",
                                        actionLabel = "Browse Tracks",
                                        onActionClick = { viewModel.selectFilter(LibraryFilter.TRACKS) }
                                    )
                                }
                            } else {
                                item {
                                    LikedSongsRowItem(
                                        songCount = uiState.favoriteTracks.size,
                                        onClick = viewModel::selectLikedSongs
                                    )
                                }

                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Favorite Tracks (${uiState.favoriteTracks.size})",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = KaonTextPrimary
                                        )

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { viewModel.playLikedSongs(shuffle = false) },
                                                colors = ButtonDefaults.buttonColors(containerColor = KaonPrimary),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                            ) {
                                                Text("Play All", style = MaterialTheme.typography.labelMedium)
                                            }
                                            Button(
                                                onClick = { viewModel.playLikedSongs(shuffle = true) },
                                                colors = ButtonDefaults.buttonColors(containerColor = KaonSurfaceElevated),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                            ) {
                                                Text("Shuffle", color = KaonTextPrimary, style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }

                                items(uiState.favoriteTracks, key = { "fav_${it.id}" }) { track ->
                                    TrackItem(
                                        track = track,
                                        isPlaying = uiState.isPlaying,
                                        isCurrent = track.id == uiState.activeTrackId,
                                        onClick = { viewModel.playTrack(track) },
                                        onFavoriteToggle = { viewModel.toggleFavorite(track.id) },
                                        onPlayNext = { viewModel.playNext(track) },
                                        onAddToQueue = { viewModel.addToQueue(track) },
                                        onAddToPlaylist = { trackForAddToPlaylist = track }
                                    )
                                }
                            }
                        }

                        LibraryFilter.RECENT -> {
                            if (uiState.recentTracks.isEmpty() && uiState.recentlyAddedTracks.isEmpty()) {
                                item {
                                    EmptyStateView(
                                        icon = Icons.Default.History,
                                        title = "No Recent Activity",
                                        message = "Listening history and newly discovered tracks will appear here.",
                                        actionLabel = "Browse Tracks",
                                        onActionClick = { viewModel.selectFilter(LibraryFilter.TRACKS) }
                                    )
                                }
                            } else {
                                // Section 1: Recently Played
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Recently Played",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = KaonTextPrimary
                                            )
                                            Text(
                                                text = "${uiState.recentTracks.size} tracks",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = KaonTextSecondary
                                            )
                                        }

                                        if (uiState.recentTracks.isNotEmpty()) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = { viewModel.playRecentlyPlayed(shuffle = false) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = KaonPrimary),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Play All", style = MaterialTheme.typography.labelMedium)
                                                }
                                                Button(
                                                    onClick = { viewModel.playRecentlyPlayed(shuffle = true) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = KaonSurfaceElevated),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Shuffle", color = KaonTextPrimary, style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                        }
                                    }
                                }

                                if (uiState.recentTracks.isEmpty()) {
                                    item {
                                        Text(
                                            text = "No listening history yet • Tracks you play will appear here.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = KaonTextTertiary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                } else {
                                    items(uiState.recentTracks, key = { "recent_play_${it.id}" }) { track ->
                                        TrackItem(
                                            track = track,
                                            isPlaying = uiState.isPlaying,
                                            isCurrent = track.id == uiState.activeTrackId,
                                            onClick = { viewModel.playTrack(track, uiState.recentTracks) },
                                            onFavoriteToggle = { viewModel.toggleFavorite(track.id) },
                                            onPlayNext = { viewModel.playNext(track) },
                                            onAddToQueue = { viewModel.addToQueue(track) },
                                            onAddToPlaylist = { trackForAddToPlaylist = track }
                                        )
                                    }
                                }

                                // Section 2: Recently Added
                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Recently Added",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = KaonTextPrimary
                                            )
                                            Text(
                                                text = "${uiState.recentlyAddedTracks.size} tracks",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = KaonTextSecondary
                                            )
                                        }

                                        if (uiState.recentlyAddedTracks.isNotEmpty()) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = { viewModel.playRecentlyAdded(shuffle = false) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = KaonPrimary),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Play All", style = MaterialTheme.typography.labelMedium)
                                                }
                                                Button(
                                                    onClick = { viewModel.playRecentlyAdded(shuffle = true) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = KaonSurfaceElevated),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Shuffle", color = KaonTextPrimary, style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                        }
                                    }
                                }

                                if (uiState.recentlyAddedTracks.isEmpty()) {
                                    item {
                                        Text(
                                            text = "No newly added tracks discovered.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = KaonTextTertiary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                } else {
                                    items(uiState.recentlyAddedTracks, key = { "recent_add_${it.id}" }) { track ->
                                        TrackItem(
                                            track = track,
                                            isPlaying = uiState.isPlaying,
                                            isCurrent = track.id == uiState.activeTrackId,
                                            onClick = { viewModel.playTrack(track, uiState.recentlyAddedTracks) },
                                            onFavoriteToggle = { viewModel.toggleFavorite(track.id) },
                                            onPlayNext = { viewModel.playNext(track) },
                                            onAddToQueue = { viewModel.addToQueue(track) },
                                            onAddToPlaylist = { trackForAddToPlaylist = track }
                                        )
                                    }
                                }
                            }
                        }

                        LibraryFilter.PLAYLISTS -> {
                            if (uiState.playlists.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 64.dp, start = 32.dp, end = 32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                                contentDescription = null,
                                                tint = KaonTextTertiary,
                                                modifier = Modifier.size(64.dp)
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "No Playlists Yet",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = KaonTextPrimary
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Create custom playlists to organize your favorite music.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = KaonTextSecondary,
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(20.dp))
                                            Button(
                                                onClick = {
                                                    trackToAppendAfterCreate = null
                                                    showCreatePlaylistDialog = true
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = KaonPrimary)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Create Playlist")
                                            }
                                        }
                                    }
                                }
                            } else {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${uiState.playlists.size} playlists",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = KaonTextSecondary
                                        )

                                        Button(
                                            onClick = {
                                                trackToAppendAfterCreate = null
                                                showCreatePlaylistDialog = true
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = KaonSurfaceElevated),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, tint = KaonPrimary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("New Playlist", color = KaonTextPrimary, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }

                                items(uiState.playlists, key = { "playlist_${it.id}" }) { playlist ->
                                    PlaylistRowItem(
                                        playlist = playlist,
                                        onClick = { viewModel.selectPlaylist(playlist) },
                                        onPlay = { viewModel.playPlaylist(playlist) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomPadding.calculateBottomPadding() + 8.dp)
        )
    }

    // Add to Playlist Bottom Sheet
    if (trackForAddToPlaylist != null) {
        AddToPlaylistBottomSheet(
            track = trackForAddToPlaylist!!,
            playlists = uiState.playlists,
            onDismiss = { trackForAddToPlaylist = null },
            onSelectPlaylist = { playlist ->
                val track = trackForAddToPlaylist!!
                viewModel.addTrackToPlaylist(playlist.id, track.id, track.displayTitle, playlist.name)
                trackForAddToPlaylist = null
            },
            onCreateNewPlaylist = {
                val track = trackForAddToPlaylist!!
                trackToAppendAfterCreate = track.id
                trackForAddToPlaylist = null
                showCreatePlaylistDialog = true
            }
        )
    }

    // Create Playlist Dialog
    if (showCreatePlaylistDialog) {
        var playlistNameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = playlistNameInput,
                    onValueChange = { if (it.length <= 50) playlistNameInput = it },
                    label = { Text("Playlist Name") },
                    placeholder = { Text("My Awesome Playlist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = playlistNameInput.trim()
                        if (name.isNotBlank()) {
                            viewModel.createPlaylist(name, trackToAppendAfterCreate)
                            showCreatePlaylistDialog = false
                            trackToAppendAfterCreate = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KaonPrimary)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreatePlaylistDialog = false
                    trackToAppendAfterCreate = null
                }) {
                    Text("Cancel", color = KaonTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun PlaylistRowItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onPlay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = KaonTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Playlist • ${playlist.trackCount} ${if (playlist.trackCount == 1) "track" else "tracks"}",
                style = MaterialTheme.typography.bodyMedium,
                color = KaonTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onPlay) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play Playlist",
                tint = KaonPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun LibraryChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) KaonPrimary else KaonSurfaceElevated
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = if (isSelected) Color(0xFF141418) else KaonTextPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun LikedSongsRowItem(
    songCount: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFFF43F5E))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Liked Songs",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = KaonTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Playlist • $songCount ${if (songCount == 1) "song" else "songs"}",
                style = MaterialTheme.typography.bodyMedium,
                color = KaonTextSecondary
            )
        }
    }
}

@Composable
private fun ArtistRowItem(
    artist: Artist,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = KaonSurfaceElevated
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = KaonPrimary,
                modifier = Modifier.padding(14.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.displayName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = KaonTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Artist",
                style = MaterialTheme.typography.bodyMedium,
                color = KaonTextSecondary
            )
        }
    }
}

@Composable
private fun AlbumRowItem(
    album: Album,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkImage(
            albumId = album.albumId,
            sizeBucket = SizeBucket.THUMBNAIL,
            modifier = Modifier.size(56.dp),
            cornerRadius = 8.dp
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.displayTitle,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = KaonTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Album • ${album.displayArtist}",
                style = MaterialTheme.typography.bodyMedium,
                color = KaonTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SortFilterPill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) KaonPrimary.copy(alpha = 0.2f) else KaonSurfaceElevated,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, KaonPrimary) else null
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
            color = if (isSelected) KaonPrimary else KaonTextSecondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
