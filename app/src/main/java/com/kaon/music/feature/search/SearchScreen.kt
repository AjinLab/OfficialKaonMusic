package com.kaon.music.feature.search

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaon.music.core.artwork.SizeBucket
import com.kaon.music.core.data.model.Album
import com.kaon.music.core.data.model.Artist
import com.kaon.music.core.data.model.TopResultType
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.designsystem.component.ArtworkImage
import com.kaon.music.core.designsystem.component.TrackItem
import com.kaon.music.core.designsystem.theme.KaonBackground
import com.kaon.music.core.designsystem.theme.KaonPrimary
import com.kaon.music.core.designsystem.theme.KaonSurfaceElevated
import com.kaon.music.core.designsystem.theme.KaonTextPrimary
import com.kaon.music.core.designsystem.theme.KaonTextSecondary
import com.kaon.music.core.designsystem.theme.KaonTextTertiary
import com.kaon.music.feature.library.playlist.AddToPlaylistBottomSheet
import com.kaon.music.feature.search.component.GenreCard

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    bottomPadding: PaddingValues,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val context = LocalContext.current

    val isVoiceSearchAvailable = remember {
        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        speechIntent.resolveActivity(context.packageManager) != null
    }

    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        if (!matches.isNullOrEmpty()) {
            viewModel.onSearchQueryChanged(matches[0])
        }
    }

    var trackForAddToPlaylist by remember { mutableStateOf<Track?>(null) }
    var trackToAppendAfterCreate by remember { mutableStateOf<Track?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KaonBackground)
            .padding(bottom = bottomPadding.calculateBottomPadding())
    ) {
        // Search Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Search",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                ),
                color = KaonTextPrimary
            )

            if (isVoiceSearchAvailable) {
                IconButton(onClick = {
                    try {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Search songs, albums, artists...")
                        }
                        voiceSearchLauncher.launch(intent)
                    } catch (e: Exception) {
                        timber.log.Timber.w(e, "Voice search activity unavailable")
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Search",
                        tint = KaonTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Search Text Field
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = viewModel::onSearchQueryChanged,
            placeholder = {
                Text(
                    text = "Search local & YouTube Music",
                    color = KaonTextTertiary,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = KaonTextSecondary
                )
            },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = KaonTextSecondary
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = KaonSurfaceElevated,
                unfocusedContainerColor = KaonSurfaceElevated,
                disabledContainerColor = KaonSurfaceElevated,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = KaonTextPrimary,
                unfocusedTextColor = KaonTextPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )

        // Metrolist Search Filter Tabs
        if (uiState.searchQuery.isNotBlank()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SearchFilterType.values(), key = { it.name }) { filter ->
                    val isSelected = filter == uiState.selectedFilter
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onFilterSelected(filter) },
                        label = {
                            Text(
                                text = filter.label,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = KaonSurfaceElevated,
                            labelColor = KaonTextSecondary,
                            selectedContainerColor = KaonPrimary,
                            selectedLabelColor = Color(0xFF141418)
                        ),
                        border = null,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }

        // Live Search Suggestions
        if (uiState.suggestions.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.suggestions, key = { it }) { suggestion ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = KaonSurfaceElevated.copy(alpha = 0.85f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.onSuggestionSelected(suggestion) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = KaonPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.bodySmall,
                                color = KaonTextPrimary
                            )
                        }
                    }
                }
            }
        }

        if (uiState.isSearchingOnline) {
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                color = KaonPrimary,
                trackColor = KaonSurfaceElevated
            )
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (uiState.searchQuery.isBlank()) {
            // Browse All Genres Grid
            Text(
                text = "Browse All",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = KaonTextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.genres, key = { it.name }) { genre ->
                    GenreCard(
                        genre = genre,
                        onClick = { viewModel.onGenreSelected(genre) }
                    )
                }
            }
        } else {
            // Live Search Results
            val hasLocalResults = uiState.matchingTracks.isNotEmpty() || uiState.matchingAlbums.isNotEmpty() || uiState.matchingArtists.isNotEmpty()
            val hasOnlineResults = uiState.topResult != null || uiState.onlineTracks.isNotEmpty() || uiState.onlineAlbums.isNotEmpty() || uiState.onlineArtists.isNotEmpty() || uiState.onlinePlaylists.isNotEmpty()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // Metrolist Top Result Hero Card
                if (uiState.topResult != null) {
                    val top = uiState.topResult!!
                    // Playlists have no detail destination yet, so that card stays non-clickable
                    // rather than offering a tap that does nothing.
                    val topResultAction: (() -> Unit)? = when (top.type) {
                        TopResultType.SONG, TopResultType.VIDEO -> top.track?.let { track -> { viewModel.playTrack(track) } }
                        TopResultType.ALBUM -> top.album?.let { album -> { onAlbumClick(album) } }
                        TopResultType.ARTIST -> top.artist?.let { artist -> { onArtistClick(artist) } }
                        TopResultType.PLAYLIST -> null
                    }
                    item {
                        Text(
                            text = "Top Result",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = KaonTextPrimary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = KaonSurfaceElevated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .then(
                                    if (topResultAction != null) {
                                        Modifier.clickable(onClick = topResultAction)
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (top.type == TopResultType.ARTIST) {
                                    ArtworkImage(
                                        artistName = top.title,
                                        isArtist = true,
                                        cornerRadius = 32.dp,
                                        modifier = Modifier.size(64.dp)
                                    )
                                } else {
                                    ArtworkImage(
                                        albumId = top.album?.albumId ?: 0L,
                                        album = top.album?.title,
                                        artist = top.album?.artist,
                                        artworkUri = top.thumbnailUri,
                                        sizeBucket = SizeBucket.THUMBNAIL,
                                        modifier = Modifier.size(64.dp),
                                        cornerRadius = 10.dp
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = top.title,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = KaonTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = top.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = KaonTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (top.track != null) {
                                    Surface(
                                        shape = CircleShape,
                                        color = KaonPrimary,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .clickable { viewModel.playTrack(top.track) }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Play",
                                                tint = Color(0xFF141418),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Local Artists row
                if (uiState.matchingArtists.isNotEmpty()) {
                    item {
                        Text(
                            text = "Artists",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = KaonTextPrimary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(uiState.matchingArtists, key = { "artist_${it.artistId}_${it.name}" }) { artist ->
                                Column(
                                    modifier = Modifier
                                        .width(90.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onArtistClick(artist) },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    ArtworkImage(
                                        artistName = artist.name,
                                        isArtist = true,
                                        cornerRadius = 36.dp,
                                        modifier = Modifier.size(72.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = artist.displayName,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = KaonTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Online Artists row (when filtered by ARTISTS or in ALL)
                if (uiState.onlineArtists.isNotEmpty() && uiState.selectedFilter == SearchFilterType.ARTISTS) {
                    item {
                        Text(
                            text = "YouTube Artists",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = KaonTextPrimary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(uiState.onlineArtists, key = { "yt_artist_${it.artistId}_${it.name}" }) { artist ->
                                Column(
                                    modifier = Modifier
                                        .width(90.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onArtistClick(artist) },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    ArtworkImage(
                                        artistName = artist.name,
                                        isArtist = true,
                                        cornerRadius = 36.dp,
                                        modifier = Modifier.size(72.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = artist.displayName,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = KaonTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Local Albums row
                if (uiState.matchingAlbums.isNotEmpty()) {
                    item {
                        Text(
                            text = "Albums",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = KaonTextPrimary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(uiState.matchingAlbums, key = { "album_${it.albumId}" }) { album ->
                                Column(
                                    modifier = Modifier
                                        .width(110.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onAlbumClick(album) }
                                ) {
                                    ArtworkImage(
                                        albumId = album.albumId,
                                        album = album.title,
                                        artist = album.artist,
                                        sizeBucket = SizeBucket.FULL,
                                        modifier = Modifier.size(110.dp),
                                        cornerRadius = 10.dp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = album.displayTitle,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = KaonTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Online Albums row (when filtered by ALBUMS)
                if (uiState.onlineAlbums.isNotEmpty()) {
                    item {
                        Text(
                            text = "YouTube Music Albums",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = KaonTextPrimary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(uiState.onlineAlbums, key = { "yt_album_${it.albumId}_${it.title}" }) { album ->
                                Column(
                                    modifier = Modifier
                                        .width(110.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onAlbumClick(album) }
                                ) {
                                    ArtworkImage(
                                        albumId = album.albumId,
                                        album = album.title,
                                        artist = album.artist,
                                        sizeBucket = SizeBucket.FULL,
                                        modifier = Modifier.size(110.dp),
                                        cornerRadius = 10.dp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = album.displayTitle,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = KaonTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = album.displayArtist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = KaonTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Online Playlists section
                if (uiState.onlinePlaylists.isNotEmpty()) {
                    item {
                        Text(
                            text = "Playlists",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = KaonTextPrimary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(uiState.onlinePlaylists, key = { "pl_${it.playlistId}" }) { playlist ->
                                Column(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = KaonSurfaceElevated,
                                        modifier = Modifier.size(130.dp)
                                    ) {
                                        if (playlist.thumbnailUri != null) {
                                            ArtworkImage(
                                                albumId = 0L,
                                                artworkUri = playlist.thumbnailUri,
                                                sizeBucket = SizeBucket.FULL,
                                                modifier = Modifier.fillMaxSize(),
                                                cornerRadius = 10.dp
                                            )
                                        } else {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                                    contentDescription = null,
                                                    tint = KaonPrimary,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = playlist.title,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = KaonTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = playlist.author,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = KaonTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Local Songs list
                if (uiState.matchingTracks.isNotEmpty()) {
                    item {
                        Text(
                            text = "Local Library Songs",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = KaonTextPrimary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }

                    itemsIndexed(
                        items = uiState.matchingTracks,
                        key = { _, track -> "local_${track.id}" }
                    ) { _, track ->
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

                // Offline Notice Banner
                if (!uiState.isOnline) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = KaonSurfaceElevated
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = KaonTextTertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Offline Mode • Showing local library results only",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KaonTextSecondary
                                )
                            }
                        }
                    }
                }

                // Online Songs list (YouTube Music)
                if (uiState.onlineTracks.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = null,
                                tint = KaonPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "YouTube Music Online",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = KaonTextPrimary
                            )
                        }
                    }

                    itemsIndexed(
                        items = uiState.onlineTracks,
                        key = { _, track -> "yt_${track.youtubeVideoId ?: track.id}" }
                    ) { _, track ->
                        TrackItem(
                            track = track,
                            isPlaying = uiState.isPlaying,
                            isCurrent = track.id == uiState.activeTrackId,
                            onClick = { viewModel.playTrack(track) },
                            onFavoriteToggle = { viewModel.toggleFavorite(track.id) },
                            onPlayNext = { viewModel.playNext(track) },
                            onAddToQueue = { viewModel.addToQueue(track) },
                            onAddToPlaylist = { trackForAddToPlaylist = track },
                            isOnline = uiState.isOnline
                        )
                    }
                }

                if (!hasLocalResults && !hasOnlineResults && !uiState.isSearchingOnline) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No results found for \"${uiState.searchQuery}\"",
                                style = MaterialTheme.typography.titleMedium,
                                color = KaonTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }

    if (trackForAddToPlaylist != null) {
        AddToPlaylistBottomSheet(
            track = trackForAddToPlaylist!!,
            playlists = playlists,
            onDismiss = { trackForAddToPlaylist = null },
            onSelectPlaylist = { playlist ->
                trackForAddToPlaylist?.let { viewModel.addTrackToPlaylist(playlist.id, it) }
                trackForAddToPlaylist = null
            },
            onCreateNewPlaylist = {
                // Dismiss the sheet before showing the dialog, otherwise both stay stacked.
                trackToAppendAfterCreate = trackForAddToPlaylist
                trackForAddToPlaylist = null
                newPlaylistName = ""
                showCreatePlaylistDialog = true
            }
        )
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreatePlaylistDialog = false
                trackToAppendAfterCreate = null
            },
            title = {
                Text(
                    text = "New Playlist",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = KaonTextPrimary
                )
            },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val track = trackToAppendAfterCreate
                        if (newPlaylistName.isNotBlank() && track != null) {
                            viewModel.createPlaylistAndAddTrack(newPlaylistName.trim(), track)
                            trackToAppendAfterCreate = null
                            showCreatePlaylistDialog = false
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
                    Text("Cancel")
                }
            },
            containerColor = KaonSurfaceElevated
        )
    }
}
