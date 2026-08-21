package com.kaon.music.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kaon.music.core.designsystem.theme.KaonTheme
import com.kaon.music.feature.library.LibraryScreen
import com.kaon.music.feature.library.LibraryViewModel
import com.kaon.music.feature.player.FullPlayerOverlay
import com.kaon.music.feature.player.MiniPlayer
import com.kaon.music.feature.player.PlayerViewModel

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }

        if (audioGranted) {
            val app = application as KaonApplication
            app.container.syncEngine.let {
                // Trigger sync on permission grant
                (application as KaonApplication).container.trackRepository
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestRequiredPermissions()

        val app = application as KaonApplication
        val container = app.container

        setContent {
            KaonTheme {
                val libraryViewModel = remember {
                    LibraryViewModel(
                        trackRepository = container.trackRepository,
                        playbackFacade = container.playbackFacade
                    )
                }

                val playerViewModel = remember {
                    PlayerViewModel(
                        playbackFacade = container.playbackFacade,
                        trackRepository = container.trackRepository
                    )
                }

                MainScreenContent(
                    libraryViewModel = libraryViewModel,
                    playerViewModel = playerViewModel
                )
            }
        }
    }

    private fun requestRequiredPermissions() {
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
        }
    }
}

@Composable
private fun MainScreenContent(
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel
) {
    val playbackState by playerViewModel.playbackState.collectAsState()
    val isFullPlayerExpanded by playerViewModel.isFullPlayerExpanded.collectAsState()

    val miniPlayerBottomPadding = if (playbackState.currentTrack != null) 76.dp else 0.dp

    Box(modifier = Modifier.fillMaxSize()) {
        // Main Library View
        LibraryScreen(
            viewModel = libraryViewModel,
            bottomPadding = PaddingValues(bottom = miniPlayerBottomPadding)
        )

        // Docked Mini-Player (App Overlay)
        if (playbackState.currentTrack != null) {
            playbackState.currentTrack?.let { track ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                ) {
                    MiniPlayer(
                        track = track,
                        isPlaying = playbackState.isPlaying,
                        progress = playbackState.progress,
                        onClick = playerViewModel::expandFullPlayer,
                        onTogglePlayPause = playerViewModel::togglePlayPause,
                        onSkipNext = playerViewModel::skipNext
                    )
                }
            }
        }

        // Full-Screen Player Overlay (App Overlay)
        FullPlayerOverlay(
            isExpanded = isFullPlayerExpanded,
            playbackState = playbackState,
            onCollapse = playerViewModel::collapseFullPlayer,
            onTogglePlayPause = playerViewModel::togglePlayPause,
            onSkipNext = playerViewModel::skipNext,
            onSkipPrevious = playerViewModel::skipPrevious,
            onSeekTo = playerViewModel::seekTo,
            onToggleShuffle = playerViewModel::toggleShuffle,
            onCycleRepeatMode = playerViewModel::cycleRepeatMode,
            onToggleFavorite = playerViewModel::toggleFavorite
        )
    }
}
