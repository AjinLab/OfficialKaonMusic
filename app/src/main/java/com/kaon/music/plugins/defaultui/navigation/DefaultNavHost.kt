package com.kaon.music.plugins.defaultui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kaon.music.core.playback.PlayerController
import com.kaon.music.media.artwork.ArtworkLoader
import com.kaon.music.media.artwork.ArtworkRepository
import com.kaon.music.media.library.LibraryController
import com.kaon.music.plugins.defaultui.screens.album.AlbumDetailScreen
import com.kaon.music.plugins.defaultui.screens.artist.ArtistDetailScreen
import com.kaon.music.plugins.defaultui.screens.library.LibraryScreen
import com.kaon.music.plugins.defaultui.screens.liked.LikedSongsScreen
import com.kaon.music.plugins.defaultui.screens.music.MusicScreen
import com.kaon.music.plugins.defaultui.screens.playlist.PlaylistDetailScreen
import com.kaon.music.plugins.defaultui.screens.search.SearchScreen
import com.kaon.music.plugins.defaultui.screens.settings.SettingsScreen

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween

@Composable
fun DefaultNavHost(
    navController: NavHostController,
    libraryController: LibraryController,
    playerController: PlayerController,
    artworkLoader: ArtworkLoader,
    artworkRepository: ArtworkRepository,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = "library",
        modifier = modifier,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(350)) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(350)) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(350)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(350)) }
    ) {
        composable("library") {
            LibraryScreen(
                libraryController = libraryController,
                playerController = playerController,
                artworkLoader = artworkLoader,
                artworkRepository = artworkRepository,
                onNavigateToAlbum = { id -> navController.navigate("album/$id") },
                onNavigateToArtist = { id -> navController.navigate("artist/$id") }
            )
        }

        composable(
            route = "album/{albumId}",
            arguments = listOf(navArgument("albumId") { type = NavType.LongType })
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getLong("albumId") ?: return@composable
            AlbumDetailScreen(
                albumId = albumId,
                libraryController = libraryController,
                playerController = playerController,
                artworkLoader = artworkLoader,
                artworkRepository = artworkRepository,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToArtist = { id -> navController.navigate("artist/$id") },
                onNavigateToAlbum = { id -> navController.navigate("album/$id") }
            )
        }

        composable(
            route = "artist/{artistId}",
            arguments = listOf(navArgument("artistId") { type = NavType.LongType })
        ) { backStackEntry ->
            val artistId = backStackEntry.arguments?.getLong("artistId") ?: return@composable
            ArtistDetailScreen(
                artistId = artistId,
                libraryController = libraryController,
                playerController = playerController,
                artworkLoader = artworkLoader,
                artworkRepository = artworkRepository,
                onNavigateToAlbum = { id -> navController.navigate("album/$id") },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("search") {
            SearchScreen(
                libraryController = libraryController,
                playerController = playerController,
                artworkRepository = artworkRepository,
                onNavigateToAlbum = { id -> navController.navigate("album/$id") },
                onNavigateToArtist = { id -> navController.navigate("artist/$id") }
            )
        }
        
        composable("music") {
            MusicScreen(
                libraryController = libraryController,
                onNavigateToLikedSongs = { navController.navigate("liked-songs") },
                onNavigateToPlaylist = { id, name -> navController.navigate("playlist/$id/$name") }
            )
        }

        composable(
            route = "playlist/{playlistId}/{playlistName}",
            arguments = listOf(
                navArgument("playlistId") { type = NavType.LongType },
                navArgument("playlistName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: return@composable
            val playlistName = backStackEntry.arguments?.getString("playlistName") ?: ""
            PlaylistDetailScreen(
                playlistId = playlistId,
                playlistName = playlistName,
                libraryController = libraryController,
                playerController = playerController,
                artworkRepository = artworkRepository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("liked-songs") {
            LikedSongsScreen(
                libraryController = libraryController,
                playerController = playerController,
                artworkRepository = artworkRepository,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable("settings") {
            SettingsScreen(
                libraryController = libraryController
            )
        }
    }
}
