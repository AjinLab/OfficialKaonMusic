package com.kaon.music.plugins.defaultui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kaon.music.core.kernel.Kernel
import com.kaon.music.core.kernel.get
import com.kaon.music.core.playback.PlayerController
import com.kaon.music.core.plugin.api.UiPlugin
import com.kaon.music.media.artwork.ArtworkLoader
import com.kaon.music.media.artwork.ArtworkRepository
import com.kaon.music.media.library.LibraryController
import com.kaon.music.plugins.defaultui.navigation.BottomNavBar
import com.kaon.music.plugins.defaultui.navigation.DefaultNavHost
import com.kaon.music.plugins.defaultui.screens.player.MiniPlayer
import com.kaon.music.plugins.defaultui.screens.player.NowPlayingSheet
import com.kaon.music.plugins.defaultui.theme.KaonTheme
import androidx.compose.runtime.collectAsState

class DefaultUi : UiPlugin {
    override val id: String = "com.kaon.music.ui.default"

    override fun initialize() {}
    override fun destroy() {}

    @Composable
    override fun MainScreen(kernel: Kernel) {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        val playerController = kernel.get<PlayerController>()
        val libraryController = kernel.get<LibraryController>()
        val artworkLoader = kernel.get<ArtworkLoader>()
        val artworkRepository = kernel.get<ArtworkRepository>()

        val currentSong by playerController.currentSong.collectAsState()
        var showNowPlaying by remember { mutableStateOf(false) }

        KaonTheme(artworkColors = currentSong?.artworkColors) {
            Scaffold(
                bottomBar = {
                    Column {
                        MiniPlayer(
                            playerController = playerController,
                            onExpand = { showNowPlaying = true },
                            isVisible = !showNowPlaying
                        )
                        BottomNavBar(
                            currentRoute = currentRoute,
                            onNavigate = { route ->
                                navController.navigate(route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            ) { paddingValues ->
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    DefaultNavHost(
                        navController = navController,
                        libraryController = libraryController,
                        playerController = playerController,
                        artworkLoader = artworkLoader,
                        artworkRepository = artworkRepository
                    )
                }
            }

            if (showNowPlaying) {
                NowPlayingSheet(
                    controller = playerController,
                    artworkRepository = artworkRepository,
                    libraryController = libraryController,
                    onDismiss = { showNowPlaying = false },
                    onGoToAlbum = { id ->
                        navController.navigate("album/$id")
                        showNowPlaying = false
                    },
                    onGoToArtist = { id ->
                        navController.navigate("artist/$id")
                        showNowPlaying = false
                    }
                )
            }
        }
    }
}
