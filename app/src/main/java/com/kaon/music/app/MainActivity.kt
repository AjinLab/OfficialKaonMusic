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
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaon.music.app.di.KaonViewModelFactory
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

    /**
     * ARCHITECTURE.md §5.5: ViewModels come from the Activity's ViewModelStore, so they survive
     * configuration change, are cleared exactly once, and expose a SavedStateHandle. Holding them as
     * Activity properties (rather than assigning fields from inside a `remember` block during
     * composition) is also what makes them available to [onCreate] and [onNewIntent] — the previous
     * arrangement read a still-null reference when handling the notification intent on a cold start.
     */
    private val viewModelFactory by lazy {
        KaonViewModelFactory((application as KaonApplication).container, this)
    }

    private val homeViewModel: HomeViewModel by viewModels { viewModelFactory }
    private val searchViewModel: SearchViewModel by viewModels { viewModelFactory }
    private val libraryViewModel: LibraryViewModel by viewModels { viewModelFactory }
    private val playerViewModel: PlayerViewModel by viewModels { viewModelFactory }
    private val settingsViewModel: SettingsViewModel by viewModels { viewModelFactory }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }

        libraryViewModel.setPermissionState(audioGranted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val userSettings by settingsViewModel.settings.collectAsStateWithLifecycle()

            KaonTheme(userSettings = userSettings) {
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
            playerViewModel.expandFullPlayer()
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
            libraryViewModel.setPermissionState(hasStoragePermission())
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
    // Saveable so the active tab survives rotation and process death (ARCHITECTURE.md §5.5).
    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    var isSettingsOpen by rememberSaveable { mutableStateOf(false) }

    // ARCHITECTURE.md §3.2: only NowPlaying and the queue emptiness flag are read here. Playback
    // progress is deliberately not observed at this level — it ticks every 500 ms, and reading it in
    // the root composable invalidated the whole tree including every screen below.
    val nowPlaying by playerViewModel.nowPlaying.collectAsStateWithLifecycle()
    val queue by playerViewModel.queue.collectAsStateWithLifecycle()
    val isFullPlayerExpanded by playerViewModel.isFullPlayerExpanded.collectAsStateWithLifecycle()
    val lyrics by playerViewModel.lyricsState.collectAsStateWithLifecycle()
    val isLoadingLyrics by playerViewModel.isLoadingLyrics.collectAsStateWithLifecycle()

    val currentTrack = nowPlaying.currentTrack
    val dockedTrack = currentTrack?.takeIf { queue.tracks.isNotEmpty() }

    // Root back handling: returning to Home from another tab, and closing settings. Without this,
    // back on the Search or Library tab exited the app.
    BackHandler(enabled = isSettingsOpen || currentScreen != AppScreen.HOME) {
        if (isSettingsOpen) {
            isSettingsOpen = false
        } else {
            currentScreen = AppScreen.HOME
        }
    }

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
            if (dockedTrack != null) {
                MiniPlayer(
                    track = dockedTrack,
                    isPlaying = nowPlaying.isPlaying,
                    progress = playerViewModel.progress,
                    isFavorite = dockedTrack.isFavorite,
                    onPlayPauseClick = playerViewModel::togglePlayPause,
                    onFavoriteClick = { playerViewModel.toggleFavorite(dockedTrack.id) },
                    onClick = playerViewModel::expandFullPlayer
                )
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
    }
}
