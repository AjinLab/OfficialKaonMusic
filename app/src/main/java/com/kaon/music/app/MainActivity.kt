package com.kaon.music.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            KaonTheme {
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
                        playlistRepository = container.playlistRepository
                    ).also {
                        playerViewModelRef = it
                    }
                }

                MainScreenContent(
                    homeViewModel = homeViewModel,
                    searchViewModel = searchViewModel,
                    libraryViewModel = libraryViewModel,
                    playerViewModel = playerViewModel,
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
    onRequestPermission: () -> Unit
) {
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    val playbackState by playerViewModel.playbackState.collectAsState()
    val isFullPlayerExpanded by playerViewModel.isFullPlayerExpanded.collectAsState()

    val hasActiveQueue = playbackState.queue.isNotEmpty() && playbackState.currentTrack != null
    val bottomNavHeight = 56.dp
    val miniPlayerHeight = if (hasActiveQueue) 64.dp else 0.dp
    val totalBottomPadding = bottomNavHeight + miniPlayerHeight + 16.dp

    Box(modifier = Modifier.fillMaxSize()) {
        // Active Screen Content
        when (currentScreen) {
            AppScreen.HOME -> {
                HomeScreen(
                    viewModel = homeViewModel,
                    bottomPadding = PaddingValues(bottom = totalBottomPadding),
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
                    }
                )
            }

            AppScreen.SEARCH -> {
                SearchScreen(
                    viewModel = searchViewModel,
                    bottomPadding = PaddingValues(bottom = totalBottomPadding),
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
                    bottomPadding = PaddingValues(bottom = totalBottomPadding),
                    onNavigateToSearch = { currentScreen = AppScreen.SEARCH },
                    onRequestPermission = onRequestPermission
                )
            }
        }

        // Docked Mini-Player + Bottom Navigation Bar Overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
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
                onScreenSelected = { currentScreen = it }
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
            onSaveQueueAsPlaylist = playerViewModel::saveQueueAsPlaylist
        )
    }
}
