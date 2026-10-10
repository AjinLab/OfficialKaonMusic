package com.kaon.music.feature.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one navigation mechanism's rules (Phase 6A §5): push/pop, tab replacement, duplicate
 * suppression, and process-death round-trip through the string encoding.
 */
class NavigationStateTest {

    @Test
    fun `starts at Home`() {
        val state = NavigationState(listOf(Destination.Home))
        assertEquals(Destination.Home, state.current)
        assertTrue(state.isAtRoot)
    }

    @Test
    fun `push opens a detail and pop returns to the origin`() {
        val state = NavigationState(listOf(Destination.Home))
        state.push(Destination.AlbumDetail(albumId = 7L))
        assertEquals(Destination.AlbumDetail(7L), state.current)
        assertFalse(state.isAtRoot)

        state.pop()
        assertEquals(Destination.Home, state.current)
    }

    @Test
    fun `back from a detail returns to where the user came from, not Home`() {
        // The regression this pins: the old mechanism had no stack, so back from Search always
        // jumped to Home.
        val state = NavigationState(listOf(Destination.Search))
        state.push(Destination.AlbumDetail(albumId = 3L))
        state.pop()
        assertEquals(Destination.Search, state.current)
    }

    @Test
    fun `pushing the current destination is a no-op`() {
        val state = NavigationState(listOf(Destination.Library))
        state.push(Destination.AlbumDetail(albumId = 1L))
        state.push(Destination.AlbumDetail(albumId = 1L))
        state.push(Destination.AlbumDetail(albumId = 1L))

        assertEquals(2, state.stack.size)
    }

    @Test
    fun `pop at a root does nothing — the system exits instead`() {
        val state = NavigationState(listOf(Destination.Library))
        state.pop()
        assertEquals(1, state.stack.size)
        assertEquals(Destination.Library, state.current)
    }

    @Test
    fun `switching tabs replaces the stack`() {
        val state = NavigationState(listOf(Destination.Home))
        state.push(Destination.AlbumDetail(albumId = 1L))
        state.push(Destination.Settings)

        state.switchTab(Destination.Search)

        assertEquals(listOf(Destination.Search), state.stack)
    }

    @Test
    fun `stack survives process death through the string encoding`() {
        val state = NavigationState(
            listOf(
                Destination.Library,
                Destination.ArtistDetail(artistId = 5L, artistName = "Artist Name"),
                Destination.PlaylistDetail(playlistId = 9L)
            )
        )

        // save/restore are SaverScope extensions; the scope is trivial for this Saver.
        val saved: List<String> = with(NavigationState.Saver) { androidx.compose.runtime.saveable.SaverScope { true }.save(state!!)!! }
        val restored = NavigationState.Saver.restore(saved)

        assertEquals(
            listOf(
                Destination.Library,
                Destination.ArtistDetail(artistId = 5L, artistName = "Artist Name"),
                Destination.PlaylistDetail(playlistId = 9L)
            ),
            restored?.stack
        )
    }

    @Test
    fun `the pipe separator is sanitized out of names on encode`() {
        // '|' is the field separator; a name containing it is folded to spaces, round-trip stable.
        val encoded = Destination.ArtistDetail(5L, "AC|DC").encode()
        val decoded = Destination.decode(encoded)
        assertEquals(Destination.ArtistDetail(5L, "AC DC"), decoded)
    }

    @Test
    fun `an empty or corrupt save restores to Home rather than crashing`() {
        assertEquals(listOf(Destination.Home), NavigationState.Saver.restore(emptyList())?.stack)
        assertNull(Destination.decode("GARBAGE"))
        assertNull(Destination.decode("ALBUM|notanumber"))
    }

    @Test
    fun `detail destinations highlight their owning tab`() {
        assertEquals(Destination.Library, Destination.AlbumDetail(1L).rootTab())
        assertEquals(Destination.Library, Destination.LikedSongs.rootTab())
        assertEquals(Destination.Home, Destination.Home.rootTab())
        assertEquals(Destination.Search, Destination.Search.rootTab())
    }
}
