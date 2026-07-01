package com.kaon.music.plugins.defaultui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kaon.music.core.kernel.Kernel
import com.kaon.music.plugins.defaultui.components.KaonBottomNavBar
import com.kaon.music.plugins.defaultui.components.NavTab
import com.kaon.music.plugins.defaultui.screens.account.AccountScreen
import com.kaon.music.plugins.defaultui.screens.album.AlbumScreen
import com.kaon.music.plugins.defaultui.screens.artist.ArtistScreen
import com.kaon.music.plugins.defaultui.screens.discover.DiscoverScreen
import com.kaon.music.plugins.defaultui.screens.library.LibraryScreen
import com.kaon.music.plugins.defaultui.screens.search.SearchScreen
import com.kaon.music.plugins.defaultui.viewmodels.AlbumViewModel
import com.kaon.music.plugins.defaultui.viewmodels.ArtistViewModel
import com.kaon.music.plugins.defaultui.viewmodels.LibraryViewModel
import com.kaon.music.plugins.defaultui.viewmodels.PlaybackViewModel
import com.kaon.music.plugins.defaultui.viewmodels.SearchViewModel

@Composable
fun MainScreen(kernel: Kernel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory(kernel))
    val playbackViewModel: PlaybackViewModel = viewModel(factory = PlaybackViewModel.Factory(kernel))

    LaunchedEffect(Unit) {
        libraryViewModel.refresh()
    }

    // Track selected bottom nav tab
    var selectedTab by rememberSaveable { mutableStateOf(NavTab.DISCOVER) }

    // Determine if the player screen is showing
    val isPlayerScreen = currentRoute == "player"

    Scaffold(
        contentWindowInsets = WindowInsets(0), // edge-to-edge: content handles its own insets
        bottomBar = {
            if (!isPlayerScreen) {
                Column {
                    MiniPlayer(
                        viewModel = playbackViewModel,
                        onClick = {
                            navController.navigate("player")
                        }
                    )
                    KaonBottomNavBar(
                        currentTab = selectedTab,
                        onTabSelected = { tab ->
                            if (tab != selectedTab) {
                                selectedTab = tab
                                navController.navigate(tab.route) {
                                    // Pop to the start destination to avoid building a stack
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "discover",
            modifier = Modifier.padding(innerPadding)
        ) {
            // Bottom nav destinations
            composable("discover") {
                DiscoverScreen()
            }
            composable("music") {
                LibraryScreen(
                    viewModel = libraryViewModel,
                    onAlbumClick = { albumId -> navController.navigate("album/$albumId") },
                    onArtistClick = { artistId -> navController.navigate("artist/$artistId") },
                    onSearchClick = { navController.navigate("search") }
                )
            }
            composable("search") {
                val searchViewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory(kernel))
                SearchScreen(
                    viewModel = searchViewModel,
                    onNavigateUp = { navController.navigateUp() },
                    onAlbumClick = { albumId -> navController.navigate("album/$albumId") },
                    onArtistClick = { artistId -> navController.navigate("artist/$artistId") }
                )
            }
            composable("account") {
                AccountScreen()
            }

            // Detail destinations
            composable(
                route = "album/{albumId}",
                arguments = listOf(navArgument("albumId") { type = NavType.LongType })
            ) {
                val albumViewModel: AlbumViewModel = viewModel(
                    factory = AlbumViewModel.Factory(kernel)
                )
                AlbumScreen(
                    viewModel = albumViewModel,
                    onNavigateUp = { navController.navigateUp() }
                )
            }
            composable(
                route = "artist/{artistId}",
                arguments = listOf(navArgument("artistId") { type = NavType.LongType })
            ) {
                val artistViewModel: ArtistViewModel = viewModel(
                    factory = ArtistViewModel.Factory(kernel)
                )
                ArtistScreen(
                    viewModel = artistViewModel,
                    onAlbumClick = { albumId -> navController.navigate("album/$albumId") },
                    onNavigateUp = { navController.navigateUp() }
                )
            }

            // Player
            composable("player") {
                PlayerScreen(
                    viewModel = playbackViewModel,
                    onNavigateUp = { navController.navigateUp() }
                )
            }

            // Keep legacy "library" route working
            composable("library") {
                LibraryScreen(
                    viewModel = libraryViewModel,
                    onAlbumClick = { albumId -> navController.navigate("album/$albumId") },
                    onArtistClick = { artistId -> navController.navigate("artist/$artistId") },
                    onSearchClick = { navController.navigate("search") }
                )
            }
        }
    }
}
