package com.kaon.music.plugins.defaultui.navigation

import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination

sealed interface Destination {
    val route: String

    interface TopLevel : Destination

    data object Library : Destination, TopLevel {
        override val route = "library"
    }

    data object Music : Destination, TopLevel {
        override val route = "music"
    }

    data object Search : Destination, TopLevel {
        override val route = "search"
        fun createRoute(query: String? = null): String {
            return if (query != null) "search?q=$query" else "search"
        }
        const val pattern = "search?q={query}"
    }

    data object Settings : Destination, TopLevel {
        override val route = "settings"
    }

    data object LikedSongs : Destination {
        override val route = "liked-songs"
    }

    data class Album(val id: Long) : Destination {
        override val route = "album/$id"
        companion object {
            const val pattern = "album/{albumId}"
        }
    }

    data class Artist(val id: Long) : Destination {
        override val route = "artist/$id"
        companion object {
            const val pattern = "artist/{artistId}"
        }
    }

    data class Playlist(val id: Long) : Destination {
        override val route = "playlist/$id"
        companion object {
            const val pattern = "playlist/{playlistId}"
        }
    }
}

class Navigator(private val navController: NavHostController) {
    fun navigate(destination: Destination) {
        navController.navigate(destination.route) {
            if (destination is Destination.TopLevel) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    fun navigateToSearch(query: String) {
        navController.navigate(Destination.Search.createRoute(query)) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun popBackStack() {
        navController.popBackStack()
    }

    fun navigateUp() {
        navController.navigateUp()
    }
}

fun isTopLevelRoute(route: String?): Boolean {
    if (route == null) return false
    // Handle parameterized routes like search?q={query}
    val cleanRoute = route.substringBefore('?')
    return cleanRoute == Destination.Library.route ||
            cleanRoute == Destination.Music.route ||
            cleanRoute == Destination.Search.route ||
            cleanRoute == Destination.Settings.route
}
