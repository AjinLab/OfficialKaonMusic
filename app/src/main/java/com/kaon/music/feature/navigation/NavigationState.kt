package com.kaon.music.feature.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList

/**
 * The one navigation system (Phase 6A).
 *
 * A typed destination stack, saved as compact strings. Navigation Compose was considered and
 * rejected: Kaon has three root tabs, four detail destinations and one settings screen — a stack of
 * data objects and a back pop cover every required behaviour without a destination/argument DSL, a
 * second state-owner, and a new dependency. This decision is recorded in ARCHITECTURE.md §5.7.
 *
 * What this replaces: the previous arrangement had *two* root mechanisms (`currentScreen` plus an
 * `isSettingsOpen` boolean) and stored detail navigation *inside* `LibraryViewModel` state, which
 * meant there was no back stack at all — system back always jumped to Home, and cross-tab detail
 * navigation mutated a ViewModel as a side effect of navigating.
 *
 * Destination arguments are stable typed identifiers (`albumId`, `playlistId`, …). No domain object
 * ever enters navigation state — a `Track` or `Playlist` in saved state would be a process-death
 * serialization trap and a recomposition hazard (Phase 6A §4).
 */
sealed interface Destination {

    // Root tabs. Switching tabs replaces the stack with a single entry (predictable tab semantics).
    data object Home : Destination
    data object Search : Destination
    data object Library : Destination

    // Details. Rendered over whatever root the user came from; back returns there.
    data class AlbumDetail(val albumId: Long) : Destination
    data class ArtistDetail(val artistId: Long, val artistName: String) : Destination
    data class PlaylistDetail(val playlistId: Long) : Destination
    data object LikedSongs : Destination

    // Settings is one destination; its internal subpages stay in SettingsViewModel, which already
    // survives process death via SavedStateHandle. Two owners for one screen's internal state would
    // be a second navigation mechanism.
    data object Settings : Destination

    /**
     * Encoded form for saved state: `"ALBUM|17"`, `"ARTIST|5|Artist Name"`, `"HOME"`.
     *
     * The artist name rides along because artist detail is keyed by the normalized *name* in the
     * database (Phase 4 deliberately did not redesign artist identity), and the name is what the
     * detail queries need after process death. It is a query key, not a display-only passenger.
     */
    fun encode(): String = when (this) {
        is Home -> "HOME"
        is Search -> "SEARCH"
        is Library -> "LIBRARY"
        is AlbumDetail -> "ALBUM|$albumId"
        is ArtistDetail -> "ARTIST|$artistId|${artistName.replace('|', ' ')}"
        is PlaylistDetail -> "PLAYLIST|$playlistId"
        is LikedSongs -> "LIKED"
        is Settings -> "SETTINGS"
    }

    companion object {
        fun decode(raw: String): Destination? {
            val parts = raw.split('|')
            return when (parts.getOrNull(0)) {
                "HOME" -> Home
                "SEARCH" -> Search
                "LIBRARY" -> Library
                "ALBUM" -> parts.getOrNull(1)?.toLongOrNull()?.let { AlbumDetail(it) }
                "ARTIST" -> parts.getOrNull(1)?.toLongOrNull()?.let { id ->
                    ArtistDetail(artistId = id, artistName = parts.getOrElse(2) { "" })
                }
                "PLAYLIST" -> parts.getOrNull(1)?.toLongOrNull()?.let { PlaylistDetail(it) }
                "LIKED" -> LikedSongs
                "SETTINGS" -> Settings
                else -> null
            }
        }
    }
}

/**
 * The navigation stack owner.
 *
 * - [push] opens a detail; duplicates are not created: pushing the destination already on top is a
 *   no-op (spec §5: repeated selection has intentional behaviour, not accidental stack growth).
 * - [pop] is system back; at a root destination the handler disables itself and the system exits.
 * - [switchTab] replaces the whole stack with the tab's root — switching tabs must not leave a
 *   hidden detail stack that back would then walk through.
 */
class NavigationState constructor(
    initialStack: List<Destination>
) {
    private val _stack = mutableStateOf(initialStack)
    val stack: List<Destination> get() = _stack.value

    val current: Destination get() = _stack.value.last()

    val isAtRoot: Boolean get() = _stack.value.size == 1

    fun push(destination: Destination) {
        if (_stack.value.lastOrNull() == destination) return
        _stack.value = _stack.value + destination
    }

    fun pop() {
        if (_stack.value.size > 1) {
            _stack.value = _stack.value.dropLast(1)
        }
    }

    fun switchTab(tab: Destination) {
        _stack.value = listOf(tab)
    }

    /** Pop everything above the given destination, leaving it current. Used by tab roots. */
    fun popTo(destination: Destination) {
        val index = _stack.value.indexOf(destination)
        if (index >= 0) {
            _stack.value = _stack.value.take(index + 1)
        }
    }

    companion object {
        val Saver: Saver<NavigationState, List<String>> = Saver(
            save = { state -> state.stack.map { it.encode() } },
            restore = { encoded ->
                val destinations = encoded.mapNotNull { Destination.decode(it) }
                NavigationState(destinations.ifEmpty { listOf(Destination.Home) })
            }
        )
    }
}

/**
 * Remembers the navigation stack across configuration change and process death.
 *
 * Saved payload is one short string per stack entry — identifiers only, never object graphs
 * (Phase 6A §8: persist identifiers and small primitives).
 */
@Composable
fun rememberNavigationState(): NavigationState =
    rememberSaveable(saver = NavigationState.Saver) {
        NavigationState(listOf(Destination.Home))
    }

/** The root tab a stack entry belongs to, for bottom-bar highlighting on detail screens. */
fun Destination.rootTab(): Destination = when (this) {
    is Destination.AlbumDetail,
    is Destination.ArtistDetail,
    is Destination.PlaylistDetail,
    is Destination.LikedSongs -> Destination.Library
    else -> this
}
