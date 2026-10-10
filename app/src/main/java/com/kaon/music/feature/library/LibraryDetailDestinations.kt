package com.kaon.music.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.kaon.music.core.designsystem.theme.KaonBackground
import com.kaon.music.core.designsystem.theme.KaonPrimary
import com.kaon.music.core.designsystem.theme.KaonTextPrimary
import com.kaon.music.core.designsystem.theme.KaonTextSecondary
import com.kaon.music.core.data.model.Track
import com.kaon.music.feature.library.album.AlbumDetailScreen
import com.kaon.music.feature.library.artist.ArtistDetailScreen
import com.kaon.music.feature.library.playlist.LikedSongsScreen
import com.kaon.music.feature.library.playlist.PlaylistDetailScreen

/**
 * Detail destinations, opened by navigation id (Phase 6A §4).
 *
 * The navigation stack carries identifiers; these composables report the id to the
 * [LibraryViewModel] (which resolves the data) and render the detail once it arrives. Back is owned
 * by the navigation stack — no per-screen `BackHandler` — and the add-to-playlist sheet comes from
 * the root's single instance via [onAddToPlaylist].
 *
 * A destination id that no longer resolves (the album/playlist was deleted mid-visit) reports
 * [onMissing] so the stack can pop instead of showing a spinner forever.
 */

@Composable
fun AlbumDetailDestination(
    viewModel: LibraryViewModel,
    albumId: Long,
    activeTrackId: Long?,
    isPlaying: Boolean,
    bottomPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenArtist: (Long, String) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(albumId) { viewModel.openAlbum(albumId) }

    val album = uiState.selectedAlbum?.takeIf { it.albumId == albumId }
    if (album == null) {
        DetailPlaceholder(modifier = modifier, onMissing = onBack)
        return
    }

    AlbumDetailScreen(
        album = album,
        tracks = uiState.albumTracks,
        activeTrackId = activeTrackId,
        isPlaying = isPlaying,
        onBack = onBack,
        onTrackClick = { track -> viewModel.playTrack(track, uiState.albumTracks) },
        onPlayAlbum = { viewModel.playAlbum(album, shuffle = false) },
        onShuffleAlbum = { viewModel.playAlbum(album, shuffle = true) },
        onFavoriteToggle = viewModel::toggleFavorite,
        onPlayNext = viewModel::playNext,
        onAddToQueue = viewModel::addToQueue,
        onAddToPlaylist = onAddToPlaylist,
        bottomPadding = bottomPadding,
        modifier = modifier
    )
}

@Composable
fun ArtistDetailDestination(
    viewModel: LibraryViewModel,
    artistId: Long,
    artistName: String,
    activeTrackId: Long?,
    isPlaying: Boolean,
    bottomPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(artistId, artistName) { viewModel.openArtist(artistId, artistName) }

    val artist = uiState.selectedArtist?.takeIf { it.artistId == artistId }
    if (artist == null) {
        DetailPlaceholder(modifier = modifier, onMissing = onBack)
        return
    }

    ArtistDetailScreen(
        artist = artist,
        albums = uiState.artistAlbums,
        tracks = uiState.artistTracks,
        activeTrackId = activeTrackId,
        isPlaying = isPlaying,
        onBack = onBack,
        // The screen hands back the Album it rendered; navigation only needs the id.
        onAlbumClick = { album -> onOpenAlbum(album.albumId) },
        onTrackClick = { track -> viewModel.playTrack(track, uiState.artistTracks) },
        onPlayAll = { viewModel.playArtist(artist) },
        onFavoriteToggle = viewModel::toggleFavorite,
        onPlayNext = viewModel::playNext,
        onAddToQueue = viewModel::addToQueue,
        onAddToPlaylist = onAddToPlaylist,
        bottomPadding = bottomPadding,
        modifier = modifier
    )
}

@Composable
fun PlaylistDetailDestination(
    viewModel: LibraryViewModel,
    playlistId: Long,
    activeTrackId: Long?,
    isPlaying: Boolean,
    bottomPadding: PaddingValues,
    onBack: () -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playlistStats by viewModel.playlistStats.collectAsStateWithLifecycle()

    LaunchedEffect(playlistId) { viewModel.openPlaylist(playlistId) }

    val playlist = uiState.selectedPlaylist?.takeIf { it.id == playlistId }
    if (playlist == null) {
        DetailPlaceholder(modifier = modifier, onMissing = onBack)
        return
    }

    PlaylistDetailScreen(
        playlist = playlist,
        tracks = viewModel.pagedPlaylistTracks.collectAsLazyPagingItems(),
        trackCount = playlistStats.trackCount,
        totalDurationMs = playlistStats.totalDurationMs,
        activeTrackId = activeTrackId,
        isPlaying = isPlaying,
        onBack = onBack,
        onTrackClick = { track, index ->
            viewModel.playPlaylist(playlist, shuffle = false, startIndex = index)
        },
        onPlayAll = { viewModel.playPlaylist(playlist, shuffle = false) },
        onShuffleAll = { viewModel.playPlaylist(playlist, shuffle = true) },
        onFavoriteToggle = viewModel::toggleFavorite,
        onPlayNext = viewModel::playNext,
        onAddToQueue = viewModel::addToQueue,
        onRemoveFromPlaylist = { trackId -> viewModel.removeTrackFromPlaylist(playlist.id, trackId) },
        onMoveTrack = { trackId, delta -> viewModel.movePlaylistTrack(playlist.id, trackId, delta) },
        onRenamePlaylist = { newName -> viewModel.renamePlaylist(playlist.id, newName) },
        onDeletePlaylist = {
            viewModel.deletePlaylist(playlist.id)
            onBack()
        },
        onAddToPlaylist = onAddToPlaylist,
        bottomPadding = bottomPadding,
        modifier = modifier
    )
}

@Composable
fun LikedSongsDestination(
    viewModel: LibraryViewModel,
    favoriteCount: Int,
    activeTrackId: Long?,
    isPlaying: Boolean,
    bottomPadding: PaddingValues,
    onBack: () -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagedFavorites = viewModel.pagedFavoriteTracks.collectAsLazyPagingItems()

    LikedSongsScreen(
        tracks = pagedFavorites,
        trackCount = favoriteCount,
        activeTrackId = activeTrackId,
        isPlaying = isPlaying,
        onBack = onBack,
        // No context queue argument: the ViewModel builds the favorites queue from the database
        // in the same SQL order, because the visible list is paged.
        onTrackClick = { track -> viewModel.playTrack(track) },
        onPlayAll = { viewModel.playLikedSongs(shuffle = false) },
        onShuffleAll = { viewModel.playLikedSongs(shuffle = true) },
        onFavoriteToggle = viewModel::toggleFavorite,
        onPlayNext = viewModel::playNext,
        onAddToQueue = viewModel::addToQueue,
        onAddToPlaylist = onAddToPlaylist,
        bottomPadding = bottomPadding,
        modifier = modifier
    )
}

/**
 * Shown while a detail's data loads. Distinguishes "loading" from "empty" (Phase 6A §12): a detail
 * that is being resolved must not flash an empty state.
 */
@Composable
private fun DetailPlaceholder(modifier: Modifier, onMissing: () -> Unit) {
    // A destination id that fails to resolve pops itself: the effect runs once per placeholder and
    // DetailPlaceholder only renders while the ViewModel has not produced data for this id.
    var gaveUp by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2_000)
        gaveUp = true
    }
    LaunchedEffect(gaveUp) {
        if (gaveUp) onMissing()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KaonBackground),
        contentAlignment = Alignment.Center
    ) {
        if (!gaveUp) {
            CircularProgressIndicator(color = KaonPrimary, modifier = Modifier.size(32.dp))
        } else {
            Text(
                text = "This item is no longer available",
                style = MaterialTheme.typography.bodyMedium,
                color = KaonTextSecondary
            )
        }
    }
}
