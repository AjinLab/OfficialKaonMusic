package com.kaon.music.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaon.music.core.data.model.AudioSpec
import com.kaon.music.core.data.model.MediaLocator
import com.kaon.music.core.data.model.Playlist
import com.kaon.music.core.data.model.TrackId
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.designsystem.theme.KaonTheme
import com.kaon.music.feature.library.playlist.AddToPlaylistBottomSheet
import com.kaon.music.feature.library.playlist.PlaylistDetailScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistUiComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * Presents a fixed list as [LazyPagingItems].
     *
     * The playlist detail list is paged now, so the screen takes a presenter rather than a list; a
     * single-emission `PagingData` flow is the smallest way to keep these UI assertions about layout
     * rather than about paging.
     */
    @Composable
    private fun pagingItemsOf(tracks: List<Track>): LazyPagingItems<Track> =
        kotlinx.coroutines.flow.flowOf(PagingData.from(tracks)).collectAsLazyPagingItems()

    private val sampleTrack1 = Track(
        id = TrackId(1L),
        title = "Bohemian Rhapsody",
        artist = "Queen",
        album = "A Night at the Opera",
        albumId = 10L,
        durationMs = 354_000L,
        locator = MediaLocator.LocalFile(mediaStoreId = 101L, relativePath = "Music/bohemian.mp3"),
        audio = AudioSpec(mimeType = "audio/mpeg", sizeBytes = 8_000_000L),
        isFavorite = false
    )

    private val sampleTrack2 = Track(
        id = TrackId(2L),
        title = "Under Pressure",
        artist = "Queen",
        album = "Hot Space",
        albumId = 20L,
        durationMs = 248_000L,
        locator = MediaLocator.LocalFile(mediaStoreId = 102L, relativePath = "Music/pressure.mp3"),
        audio = AudioSpec(mimeType = "audio/mpeg", sizeBytes = 6_000_000L),
        isFavorite = true
    )

    private val samplePlaylist = Playlist(
        id = 1L,
        name = "Rock Classics",
        createdAt = 1000L,
        updatedAt = 1000L,
        trackCount = 2
    )

    @Test
    fun testPlaylistDetailScreenRendersHeaderAndTracks() {
        var playAllClicked = false
        var shuffleClicked = false

        composeTestRule.setContent {
            KaonTheme {
                PlaylistDetailScreen(
                    playlist = samplePlaylist,
                    tracks = pagingItemsOf(listOf(sampleTrack1, sampleTrack2)),
                    trackCount = 2,
                    totalDurationMs = sampleTrack1.durationMs + sampleTrack2.durationMs,
                    activeTrackId = 1L,
                    isPlaying = true,
                    onBack = {},
                    onTrackClick = { _, _ -> },
                    onPlayAll = { playAllClicked = true },
                    onShuffleAll = { shuffleClicked = true },
                    onFavoriteToggle = {},
                    onPlayNext = {},
                    onAddToQueue = {},
                    onRemoveFromPlaylist = {},
                    onMoveTrack = { _, _ -> },
                    onRenamePlaylist = {},
                    onDeletePlaylist = {},
                    onAddToPlaylist = {},
                    bottomPadding = PaddingValues(0.dp)
                )
            }
        }

        // Header elements
        composeTestRule.onAllNodesWithText("Rock Classics")[0].assertIsDisplayed()
        composeTestRule.onNodeWithText("Play").assertIsDisplayed()
        composeTestRule.onNodeWithText("Shuffle").assertIsDisplayed()

        // Track items
        composeTestRule.onNodeWithText("Bohemian Rhapsody").assertIsDisplayed()
        composeTestRule.onNodeWithText("Under Pressure").assertIsDisplayed()

        // Click Play All
        composeTestRule.onNodeWithText("Play").performClick()
        assertTrue(playAllClicked)

        // Click Shuffle
        composeTestRule.onNodeWithText("Shuffle").performClick()
        assertTrue(shuffleClicked)
    }

    @Test
    fun testAddToPlaylistBottomSheetRendersPlaylistsAndNewPlaylistAction() {
        var selectedPlaylist: Playlist? = null
        var createNewClicked = false

        composeTestRule.setContent {
            KaonTheme {
                AddToPlaylistBottomSheet(
                    track = sampleTrack1,
                    playlists = listOf(samplePlaylist),
                    onDismiss = {},
                    onSelectPlaylist = { selectedPlaylist = it },
                    onCreateNewPlaylist = { createNewClicked = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Add to Playlist").assertIsDisplayed()
        composeTestRule.onNodeWithText("New Playlist").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rock Classics").assertIsDisplayed()

        // Tap playlist to add
        composeTestRule.onNodeWithText("Rock Classics").performClick()
        assertEquals(samplePlaylist, selectedPlaylist)
    }

    @Test
    fun testPlaylistDetailEmptyStateRendersGuidance() {
        val emptyPlaylist = Playlist(id = 2L, name = "Empty Vibes", createdAt = 1000L, updatedAt = 1000L, trackCount = 0)

        composeTestRule.setContent {
            KaonTheme {
                PlaylistDetailScreen(
                    playlist = emptyPlaylist,
                    tracks = pagingItemsOf(emptyList()),
                    trackCount = 0,
                    totalDurationMs = 0L,
                    activeTrackId = null,
                    isPlaying = false,
                    onBack = {},
                    onTrackClick = { _, _ -> },
                    onPlayAll = {},
                    onShuffleAll = {},
                    onFavoriteToggle = {},
                    onPlayNext = {},
                    onAddToQueue = {},
                    onRemoveFromPlaylist = {},
                    onMoveTrack = { _, _ -> },
                    onRenamePlaylist = {},
                    onDeletePlaylist = {},
                    onAddToPlaylist = {},
                    bottomPadding = PaddingValues(0.dp)
                )
            }
        }

        composeTestRule.onAllNodesWithText("Empty Vibes")[0].assertIsDisplayed()
        composeTestRule.onNodeWithText("Playlist is Empty").assertIsDisplayed()
    }
}
