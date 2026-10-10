package com.kaon.music.ui.default

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.kaon.music.app.KaonApplication
import com.kaon.music.app.di.KaonViewModelFactory
import com.kaon.music.core.designsystem.theme.KaonTheme
import com.kaon.music.core.kernel.PluginMetadata
import com.kaon.music.feature.home.HomeScreen
import com.kaon.music.feature.home.HomeViewModel
import com.kaon.music.feature.library.AddToPlaylistDialogs
import com.kaon.music.feature.library.AddToPlaylistUiState
import com.kaon.music.feature.library.AlbumDetailDestination
import com.kaon.music.feature.library.ArtistDetailDestination
import com.kaon.music.feature.library.LikedSongsDestination
import com.kaon.music.feature.library.LibraryScreen
import com.kaon.music.feature.library.LibraryViewModel
import com.kaon.music.feature.library.PlaylistDetailDestination
import com.kaon.music.feature.navigation.AppScreen
import com.kaon.music.feature.navigation.BottomNavigationBar
import com.kaon.music.feature.navigation.Destination
import com.kaon.music.feature.navigation.rememberNavigationState
import com.kaon.music.feature.navigation.rootTab
import com.kaon.music.feature.player.FullPlayerOverlay
import com.kaon.music.feature.player.MiniPlayer
import com.kaon.music.feature.player.PlayerViewModel
import com.kaon.music.feature.search.SearchScreen
import com.kaon.music.feature.search.SearchViewModel
import com.kaon.music.feature.settings.SettingsScreen
import com.kaon.music.feature.settings.SettingsViewModel
import com.kaon.music.ui.plugin.KaonUiPlugin
import com.kaon.music.ui.plugin.UiControllers

/**
 * Default UI plugin providing the standard rich Compose UI, bottom navigation tabs,
 * mini player, full player overlay, and detail screens.
 */
class DefaultUiPlugin : KaonUiPlugin {

    override val metadata: PluginMetadata = PluginMetadata(
        id = "com.kaon.music.ui.default",
        name = "Default UI",
        version = "1.0.0",
        description = "Full featured default user interface with tabs and player overlays"
    )

    @Composable
    override fun Content(controllers: UiControllers, modifier: Modifier) {
        val context = LocalContext.current
        val app = context.applicationContext as KaonApplication
        val owner = context as SavedStateRegistryOwner
        val factory = remember(app, owner) { KaonViewModelFactory(app.container, owner) }

        val homeViewModel: HomeViewModel = viewModel(factory = factory)
        val searchViewModel: SearchViewModel = viewModel(factory = factory)
        val libraryViewModel: LibraryViewModel = viewModel(factory = factory)
        val playerViewModel: PlayerViewModel = viewModel(factory = factory)
        val settingsViewModel: SettingsViewModel = viewModel(factory = factory)

        val userSettings by settingsViewModel.settings.collectAsStateWithLifecycle()

        KaonTheme(userSettings = userSettings) {
            DefaultUiScaffold(
                homeViewModel = homeViewModel,
                searchViewModel = searchViewModel,
                libraryViewModel = libraryViewModel,
                playerViewModel = playerViewModel,
                settingsViewModel = settingsViewModel,
                onRequestPermission = controllers.onRequestPermission,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun DefaultUiScaffold(
    homeViewModel: HomeViewModel,
    searchViewModel: SearchViewModel,
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    settingsViewModel: SettingsViewModel,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navigationState = rememberNavigationState()
    val currentDestination = navigationState.current

    val nowPlaying by playerViewModel.nowPlaying.collectAsStateWithLifecycle()
    val queue by playerViewModel.queue.collectAsStateWithLifecycle()
    val isFullPlayerExpanded by playerViewModel.isFullPlayerExpanded.collectAsStateWithLifecycle()
    val lyrics by playerViewModel.lyricsState.collectAsStateWithLifecycle()
    val isLoadingLyrics by playerViewModel.isLoadingLyrics.collectAsStateWithLifecycle()

    val currentTrack = nowPlaying.currentTrack
    val dockedTrack = currentTrack?.takeIf { queue.tracks.isNotEmpty() }

    val appContext = LocalContext.current.applicationContext
    val userMessageBus = remember { (appContext as? KaonApplication)?.container?.userMessageBus }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(userMessageBus) {
        userMessageBus?.messages?.collect { message ->
            snackbarHostState.showSnackbar(message.text)
        }
    }

    LaunchedEffect(Unit) {
        playerViewModel.playbackEvents.collect { error ->
            userMessageBus?.post(error)
        }
    }

    BackHandler(enabled = isFullPlayerExpanded) {
        playerViewModel.collapseFullPlayer()
    }
    BackHandler(enabled = !isFullPlayerExpanded && !navigationState.isAtRoot) {
        navigationState.pop()
    }

    LaunchedEffect(currentDestination) {
        if (currentDestination in listOf(Destination.Home, Destination.Search, Destination.Library)) {
            libraryViewModel.clearDetailState()
        }
    }

    val density = LocalDensity.current
    var bottomOverlayHeightPx by remember { mutableIntStateOf(0) }
    val bottomPadding = remember(bottomOverlayHeightPx, density) {
        PaddingValues(bottom = with(density) { bottomOverlayHeightPx.toDp() } + 8.dp)
    }

    val addToPlaylistState = remember { AddToPlaylistUiState() }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            when (val destination = currentDestination) {
                is Destination.Home -> HomeScreen(
                    viewModel = homeViewModel,
                    bottomPadding = bottomPadding,
                    onAlbumClick = { album -> navigationState.push(Destination.AlbumDetail(album.albumId)) },
                    onArtistClick = { artist ->
                        navigationState.push(Destination.ArtistDetail(artist.artistId, artist.name))
                    },
                    onSeeAllRecentlyPlayed = { navigationState.push(Destination.Library) },
                    onSettingsClick = { navigationState.push(Destination.Settings) }
                )

                is Destination.Search -> SearchScreen(
                    viewModel = searchViewModel,
                    bottomPadding = bottomPadding,
                    onAlbumClick = { album -> navigationState.push(Destination.AlbumDetail(album.albumId)) },
                    onArtistClick = { artist ->
                        navigationState.push(Destination.ArtistDetail(artist.artistId, artist.name))
                    }
                )

                is Destination.Library -> LibraryScreen(
                    viewModel = libraryViewModel,
                    bottomPadding = bottomPadding,
                    onNavigateToSearch = { navigationState.push(Destination.Search) },
                    onRequestPermission = onRequestPermission,
                    onOpenAlbum = { id -> navigationState.push(Destination.AlbumDetail(id)) },
                    onOpenArtist = { id, name -> navigationState.push(Destination.ArtistDetail(id, name)) },
                    onOpenPlaylist = { id -> navigationState.push(Destination.PlaylistDetail(id)) },
                    onOpenLikedSongs = { navigationState.push(Destination.LikedSongs) },
                    onAddToPlaylist = { track -> addToPlaylistState.trackForAddToPlaylist = track },
                    onCreatePlaylistRequest = { addToPlaylistState.showCreatePlaylistDialog = true }
                )

                is Destination.Settings -> SettingsScreen(
                    viewModel = settingsViewModel,
                    onBack = { navigationState.pop() },
                    bottomPadding = bottomPadding
                )

                is Destination.AlbumDetail -> AlbumDetailDestination(
                    viewModel = libraryViewModel,
                    albumId = destination.albumId,
                    activeTrackId = nowPlaying.currentTrack?.id?.value,
                    isPlaying = nowPlaying.isPlaying,
                    bottomPadding = bottomPadding,
                    onBack = { navigationState.pop() },
                    onOpenArtist = { id, name -> navigationState.push(Destination.ArtistDetail(id, name)) },
                    onAddToPlaylist = { track -> addToPlaylistState.trackForAddToPlaylist = track }
                )

                is Destination.ArtistDetail -> ArtistDetailDestination(
                    viewModel = libraryViewModel,
                    artistId = destination.artistId,
                    artistName = destination.artistName,
                    activeTrackId = nowPlaying.currentTrack?.id?.value,
                    isPlaying = nowPlaying.isPlaying,
                    bottomPadding = bottomPadding,
                    onBack = { navigationState.pop() },
                    onOpenAlbum = { id -> navigationState.push(Destination.AlbumDetail(id)) },
                    onAddToPlaylist = { track -> addToPlaylistState.trackForAddToPlaylist = track }
                )

                is Destination.PlaylistDetail -> PlaylistDetailDestination(
                    viewModel = libraryViewModel,
                    playlistId = destination.playlistId,
                    activeTrackId = nowPlaying.currentTrack?.id?.value,
                    isPlaying = nowPlaying.isPlaying,
                    bottomPadding = bottomPadding,
                    onBack = { navigationState.pop() },
                    onAddToPlaylist = { track -> addToPlaylistState.trackForAddToPlaylist = track }
                )

                is Destination.LikedSongs -> {
                    val favoriteCount by libraryViewModel.uiState.collectAsStateWithLifecycle()
                    LikedSongsDestination(
                        viewModel = libraryViewModel,
                        favoriteCount = favoriteCount.favoriteCount,
                        activeTrackId = nowPlaying.currentTrack?.id?.value,
                        isPlaying = nowPlaying.isPlaying,
                        bottomPadding = bottomPadding,
                        onBack = { navigationState.pop() },
                        onAddToPlaylist = { track -> addToPlaylistState.trackForAddToPlaylist = track }
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { bottomOverlayHeightPx = it.height }
        ) {
            if (dockedTrack != null) {
                MiniPlayer(
                    track = dockedTrack,
                    isPlaying = nowPlaying.isPlaying,
                    progress = playerViewModel.progress,
                    isFavorite = dockedTrack.isFavorite,
                    onPlayPauseClick = playerViewModel::togglePlayPause,
                    onFavoriteClick = { playerViewModel.toggleFavorite(dockedTrack) },
                    onClick = playerViewModel::expandFullPlayer
                )
            }

            BottomNavigationBar(
                currentScreen = currentDestination.rootTab().toAppScreen(),
                onScreenSelected = { tab -> navigationState.switchTab(tab.toDestination()) }
            )
        }

        FullPlayerOverlay(
            isExpanded = isFullPlayerExpanded,
            nowPlaying = nowPlaying,
            queue = queue,
            progressFlow = playerViewModel.progress,
            onCollapse = playerViewModel::collapseFullPlayer,
            onPlayPauseClick = playerViewModel::togglePlayPause,
            onSkipNextClick = playerViewModel::skipNext,
            onSkipPreviousClick = playerViewModel::skipPrevious,
            onSeekTo = playerViewModel::seekTo,
            onToggleShuffle = playerViewModel::toggleShuffle,
            onToggleRepeat = playerViewModel::cycleRepeatMode,
            onToggleFavorite = playerViewModel::toggleFavorite,
            onRemoveQueueItem = playerViewModel::removeQueueItem,
            onMoveQueueItem = playerViewModel::moveQueueItem,
            onClearQueue = playerViewModel::clearQueue,
            onSaveQueueAsPlaylist = playerViewModel::saveQueueAsPlaylist,
            lyrics = lyrics,
            isLoadingLyrics = isLoadingLyrics,
            onRefreshLyrics = {
                nowPlaying.currentTrack?.let { playerViewModel.fetchLyricsAndMetadata(it) }
            }
        )

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        AddToPlaylistDialogs(
            viewModel = libraryViewModel,
            state = addToPlaylistState
        )
    }
}

private fun Destination.toAppScreen(): AppScreen = when (this) {
    is Destination.Search -> AppScreen.SEARCH
    is Destination.Library -> AppScreen.LIBRARY
    else -> AppScreen.HOME
}

private fun AppScreen.toDestination(): Destination = when (this) {
    AppScreen.SEARCH -> Destination.Search
    AppScreen.LIBRARY -> Destination.Library
    AppScreen.HOME -> Destination.Home
}
