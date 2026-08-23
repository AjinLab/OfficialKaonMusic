package com.kaon.music.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaon.music.core.data.model.Playlist
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
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val sampleTrack1 = Track(
        id = 1L,
        mediaStoreId = 101L,
        title = "Bohemian Rhapsody",
        artist = "Queen",
        album = "A Night at the Opera",
        albumId = 10L,
        durationMs = 354000L,
        sizeBytes = 8000000L,
        dateModified = 1000L,
        dateAdded = 1000L,
        isMissing = false,
        isFavorite = false
    )

    private val sampleTrack2 = Track(
        id = 2L,
        mediaStoreId = 102L,
        title = "Under Pressure",
        artist = "Queen",
        album = "Hot Space",
        albumId = 20L,
        durationMs = 248000L,
        sizeBytes = 6000000L,
        dateModified = 1000L,
        dateAdded = 1000L,
        isMissing = false,
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
                    tracks = listOf(sampleTrack1, sampleTrack2),
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
                    onReorder = {},
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
                    tracks = emptyList(),
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
                    onReorder = {},
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
