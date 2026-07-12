package com.kaon.music.plugins.defaultui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
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

import com.kaon.music.plugins.defaultui.motion.MotionTokens
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut

@Composable
fun DefaultNavHost(
    navController: NavHostController,
    libraryController: LibraryController,
    playerController: PlayerController,
    artworkLoader: ArtworkLoader,
    artworkRepository: ArtworkRepository,
    modifier: Modifier = Modifier
) {
    val navigator = remember(navController) { Navigator(navController) }

    NavHost(
        navController = navController,
        startDestination = Destination.Library.route,
        modifier = modifier,
        enterTransition = {
            if (isTopLevelRoute(initialState.destination.route) && isTopLevelRoute(targetState.destination.route)) {
                fadeIn(animationSpec = tween(MotionTokens.Normal)) + scaleIn(initialScale = 0.98f, animationSpec = tween(MotionTokens.Normal))
            } else {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(MotionTokens.Slow))
            }
        },
        exitTransition = {
            if (isTopLevelRoute(initialState.destination.route) && isTopLevelRoute(targetState.destination.route)) {
                fadeOut(animationSpec = tween(MotionTokens.Normal)) + scaleOut(targetScale = 1.02f, animationSpec = tween(MotionTokens.Normal))
            } else {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(MotionTokens.Slow))
            }
        },
        popEnterTransition = {
            if (isTopLevelRoute(initialState.destination.route) && isTopLevelRoute(targetState.destination.route)) {
                fadeIn(animationSpec = tween(MotionTokens.Normal)) + scaleIn(initialScale = 0.98f, animationSpec = tween(MotionTokens.Normal))
            } else {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(MotionTokens.Slow))
            }
        },
        popExitTransition = {
            if (isTopLevelRoute(initialState.destination.route) && isTopLevelRoute(targetState.destination.route)) {
                fadeOut(animationSpec = tween(MotionTokens.Normal)) + scaleOut(targetScale = 1.02f, animationSpec = tween(MotionTokens.Normal))
            } else {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(MotionTokens.Slow))
            }
        }
    ) {
        composable(Destination.Library.route) {
            LibraryScreen(
                libraryController = libraryController,
                playerController = playerController,
                artworkLoader = artworkLoader,
                artworkRepository = artworkRepository,
                onNavigateToAlbum = { id -> navigator.navigate(Destination.Album(id)) },
                onNavigateToArtist = { id -> navigator.navigate(Destination.Artist(id)) }
            )
        }

        composable(
            route = Destination.Album.pattern,
            arguments = listOf(navArgument("albumId") { type = NavType.LongType }),
            deepLinks = listOf(
                navDeepLink { uriPattern = "kaon://album/{albumId}" },
                navDeepLink { uriPattern = "https://kaon.app/album/{albumId}" }
            )
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getLong("albumId") ?: return@composable
            AlbumDetailScreen(
                albumId = albumId,
                libraryController = libraryController,
                playerController = playerController,
                artworkLoader = artworkLoader,
                artworkRepository = artworkRepository,
                onNavigateBack = { navigator.popBackStack() },
                onNavigateToArtist = { id -> navigator.navigate(Destination.Artist(id)) },
                onNavigateToAlbum = { id -> navigator.navigate(Destination.Album(id)) }
            )
        }

        composable(
            route = Destination.Artist.pattern,
            arguments = listOf(navArgument("artistId") { type = NavType.LongType }),
            deepLinks = listOf(
                navDeepLink { uriPattern = "kaon://artist/{artistId}" },
                navDeepLink { uriPattern = "https://kaon.app/artist/{artistId}" }
            )
        ) { backStackEntry ->
            val artistId = backStackEntry.arguments?.getLong("artistId") ?: return@composable
            ArtistDetailScreen(
                artistId = artistId,
                libraryController = libraryController,
                playerController = playerController,
                artworkLoader = artworkLoader,
                artworkRepository = artworkRepository,
                onNavigateToAlbum = { id -> navigator.navigate(Destination.Album(id)) },
                onNavigateBack = { navigator.popBackStack() }
            )
        }

        composable(
            route = Destination.Search.pattern,
            arguments = listOf(navArgument("query") { type = NavType.StringType; nullable = true; defaultValue = null }),
            deepLinks = listOf(
                navDeepLink { uriPattern = "kaon://search?q={query}" },
                navDeepLink { uriPattern = "https://kaon.app/search?q={query}" }
            )
        ) { backStackEntry ->
            val queryParam = backStackEntry.arguments?.getString("query")
            SearchScreen(
                libraryController = libraryController,
                playerController = playerController,
                artworkRepository = artworkRepository,
                onNavigateToAlbum = { id -> navigator.navigate(Destination.Album(id)) },
                onNavigateToArtist = { id -> navigator.navigate(Destination.Artist(id)) },
                initialQuery = queryParam ?: ""
            )
        }
        
        composable(Destination.Music.route) {
            MusicScreen(
                libraryController = libraryController,
                onNavigateToLikedSongs = { navigator.navigate(Destination.LikedSongs) },
                onNavigateToPlaylist = { id -> navigator.navigate(Destination.Playlist(id)) }
            )
        }

        composable(
            route = Destination.Playlist.pattern,
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
            deepLinks = listOf(
                navDeepLink { uriPattern = "kaon://playlist/{playlistId}" },
                navDeepLink { uriPattern = "https://kaon.app/playlist/{playlistId}" }
            )
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: return@composable
            PlaylistDetailScreen(
                playlistId = playlistId,
                libraryController = libraryController,
                playerController = playerController,
                artworkRepository = artworkRepository,
                onNavigateBack = { navigator.popBackStack() }
            )
        }

        composable(
            route = Destination.LikedSongs.route,
            deepLinks = listOf(
                navDeepLink { uriPattern = "kaon://liked-songs" },
                navDeepLink { uriPattern = "https://kaon.app/liked-songs" }
            )
        ) {
            LikedSongsScreen(
                libraryController = libraryController,
                playerController = playerController,
                artworkRepository = artworkRepository,
                onNavigateBack = { navigator.popBackStack() }
            )
        }
        
        composable(
            route = Destination.Settings.route,
            deepLinks = listOf(
                navDeepLink { uriPattern = "kaon://settings" },
                navDeepLink { uriPattern = "https://kaon.app/settings" }
            )
        ) {
            SettingsScreen(
                libraryController = libraryController
            )
        }
    }
}
