package com.kaon.music.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kaon.music.core.designsystem.theme.KaonTheme
import com.kaon.music.core.playback.KaonPlaybackService
import com.kaon.music.feature.home.HomeScreen
import com.kaon.music.feature.home.HomeViewModel
import com.kaon.music.feature.library.LibraryScreen
import com.kaon.music.feature.library.LibraryViewModel
import com.kaon.music.feature.navigation.AppScreen
import com.kaon.music.feature.navigation.BottomNavigationBar
import com.kaon.music.feature.player.FullPlayerOverlay
import com.kaon.music.feature.player.MiniPlayer
import com.kaon.music.feature.player.PlayerViewModel
import com.kaon.music.feature.search.SearchScreen
import com.kaon.music.feature.search.SearchViewModel
import com.kaon.music.feature.settings.SettingsScreen
import com.kaon.music.feature.settings.SettingsViewModel

class MainActivity : ComponentActivity() {

    private var libraryViewModelRef: LibraryViewModel? = null
    private var playerViewModelRef: PlayerViewModel? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }

        libraryViewModelRef?.setPermissionState(audioGranted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as KaonApplication
        val container = app.container

        setContent {
            val settingsViewModel = remember {
                SettingsViewModel(
                    settingsRepository = container.settingsRepository,
                    trackRepository = container.trackRepository,
                    historyRepository = container.historyRepository,
                    youtubeSessionManager = container.youtubeSessionManager
                )
            }
            val userSettings by settingsViewModel.settings.collectAsState()

            KaonTheme(userSettings = userSettings) {
                val homeViewModel = remember {
                    HomeViewModel(
                        trackRepository = container.trackRepository,
                        playbackFacade = container.playbackFacade
                    )
                }

                val searchViewModel = remember {
                    SearchViewModel(
                        trackRepository = container.trackRepository,
                        playbackFacade = container.playbackFacade,
                        playlistRepository = container.playlistRepository
                    )
                }

                val libraryViewModel = remember {
                    LibraryViewModel(
                        trackRepository = container.trackRepository,
                        playbackFacade = container.playbackFacade,
                        playlistRepository = container.playlistRepository
                    ).also {
                        libraryViewModelRef = it
                        it.setPermissionState(hasStoragePermission())
                    }
                }

                val playerViewModel = remember {
                    PlayerViewModel(
                        playbackFacade = container.playbackFacade,
                        trackRepository = container.trackRepository,
                        playlistRepository = container.playlistRepository,
                        metadataRepository = container.metadataRepository
                    ).also {
                        playerViewModelRef = it
                    }
                }

                MainScreenContent(
                    homeViewModel = homeViewModel,
                    searchViewModel = searchViewModel,
                    libraryViewModel = libraryViewModel,
                    playerViewModel = playerViewModel,
                    settingsViewModel = settingsViewModel,
                    onRequestPermission = ::requestRequiredPermissions
                )
            }
        }

        handleNotificationIntent(intent)
        requestRequiredPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(KaonPlaybackService.EXTRA_EXPAND_PLAYER, false) == true) {
            playerViewModelRef?.expandFullPlayer()
        }
    }

    private fun hasStoragePermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            libraryViewModelRef?.setPermissionState(true)
        }
    }
}

@Composable
private fun MainScreenContent(
    homeViewModel: HomeViewModel,
    searchViewModel: SearchViewModel,
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    settingsViewModel: SettingsViewModel,
    onRequestPermission: () -> Unit
) {
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    var isSettingsOpen by remember { mutableStateOf(false) }

    val playbackState by playerViewModel.playbackState.collectAsState()
    val isFullPlayerExpanded by playerViewModel.isFullPlayerExpanded.collectAsState()
    val lyrics by playerViewModel.lyricsState.collectAsState()
    val isLoadingLyrics by playerViewModel.isLoadingLyrics.collectAsState()

    val hasActiveQueue = playbackState.queue.isNotEmpty() && playbackState.currentTrack != null

    // The docked mini-player and bottom bar overlay the screen content, so screens are told
    // how much space to reserve. Measuring the overlay keeps that in sync with the mini-player
    // appearing/disappearing and with the system navigation bar inset it already applies.
    val density = LocalDensity.current
    var bottomOverlayHeightPx by remember { mutableIntStateOf(0) }
    val bottomPadding = remember(bottomOverlayHeightPx, density) {
        PaddingValues(bottom = with(density) { bottomOverlayHeightPx.toDp() } + 8.dp)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            if (isSettingsOpen) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onBack = { isSettingsOpen = false },
                    bottomPadding = bottomPadding
                )
            } else {
                // Active Screen Content
                when (currentScreen) {
                    AppScreen.HOME -> {
                        HomeScreen(
                            viewModel = homeViewModel,
                            bottomPadding = bottomPadding,
                            onAlbumClick = { album ->
                                libraryViewModel.selectAlbum(album)
                                currentScreen = AppScreen.LIBRARY
                            },
                            onArtistClick = { artist ->
                                libraryViewModel.selectArtist(artist)
                                currentScreen = AppScreen.LIBRARY
                            },
                            onSeeAllRecentlyPlayed = {
                                currentScreen = AppScreen.LIBRARY
                            },
                            onSettingsClick = { isSettingsOpen = true }
                        )
                    }

                    AppScreen.SEARCH -> {
                        SearchScreen(
                            viewModel = searchViewModel,
                            bottomPadding = bottomPadding,
                            onAlbumClick = { album ->
                                libraryViewModel.selectAlbum(album)
                                currentScreen = AppScreen.LIBRARY
                            },
                            onArtistClick = { artist ->
                                libraryViewModel.selectArtist(artist)
                                currentScreen = AppScreen.LIBRARY
                            }
                        )
                    }

                    AppScreen.LIBRARY -> {
                        LibraryScreen(
                            viewModel = libraryViewModel,
                            bottomPadding = bottomPadding,
                            onNavigateToSearch = { currentScreen = AppScreen.SEARCH },
                            onRequestPermission = onRequestPermission
                        )
                    }
                }
            }
        }

        // Docked Mini-Player + Bottom Navigation Bar Overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { bottomOverlayHeightPx = it.height }
        ) {
            if (hasActiveQueue) {
                playbackState.currentTrack?.let { track ->
                    MiniPlayer(
                        track = track,
                        isPlaying = playbackState.isPlaying,
                        progressFraction = playbackState.progress,
                        isFavorite = track.isFavorite,
                        onPlayPauseClick = playerViewModel::togglePlayPause,
                        onFavoriteClick = { playerViewModel.toggleFavorite(track.id) },
                        onClick = playerViewModel::expandFullPlayer
                    )
                }
            }

            BottomNavigationBar(
                currentScreen = currentScreen,
                onScreenSelected = {
                    isSettingsOpen = false
                    currentScreen = it
                }
            )
        }

        // Full-Screen Player Overlay (App Overlay)
        FullPlayerOverlay(
            isExpanded = isFullPlayerExpanded,
            playbackState = playbackState,
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
                playbackState.currentTrack?.let { playerViewModel.fetchLyricsAndMetadata(it) }
            }
        )
    }
}
