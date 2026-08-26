package com.kaon.music.feature.player

import android.content.Intent
import com.kaon.music.core.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackShareHelperTest {

    @Test
    fun createShareText_forLocalTrack_formatsTitleAndArtist() {
        val track = Track(
            id = 101L,
            mediaStoreId = 101L,
            title = "Midnight City",
            artist = "M83",
            album = "Hurry Up, We're Dreaming",
            albumId = 10L,
            durationMs = 243000L,
            sizeBytes = 4000000L,
            dateModified = 1000L,
            source = "LOCAL"
        )

        val text = TrackShareHelper.createShareText(track)
        assertEquals("Now listening to: Midnight City by M83 on Kaon Music", text)
    }

    @Test
    fun createShareText_forYouTubeTrack_includesYouTubeUrl() {
        val track = Track(
            id = 202L,
            mediaStoreId = 0L,
            title = "Starboy",
            artist = "The Weeknd",
            album = "Starboy",
            albumId = 0L,
            durationMs = 230000L,
            sizeBytes = 0L,
            dateModified = 1000L,
            source = "YOUTUBE",
            youtubeVideoId = "d_HlPboLRL8"
        )

        val text = TrackShareHelper.createShareText(track)
        assertTrue(text.contains("Starboy by The Weeknd"))
        assertTrue(text.contains("https://www.youtube.com/watch?v=d_HlPboLRL8"))
    }

    @Test
    fun createShareText_forYouTubeTrack_withoutVideoId_fallsBack() {
        val track = Track(
            id = 203L,
            mediaStoreId = 0L,
            title = "Starboy",
            artist = "The Weeknd",
            album = "Starboy",
            albumId = 0L,
            durationMs = 230000L,
            sizeBytes = 0L,
            dateModified = 1000L,
            source = "YOUTUBE",
            youtubeVideoId = null
        )

        val text = TrackShareHelper.createShareText(track)
        assertEquals("Now listening to: Starboy by The Weeknd on Kaon Music", text)
    }

    @Test
    fun createShareIntent_returnsNonNullIntent() {
        val track = Track(
            id = 303L,
            mediaStoreId = 303L,
            title = "Resonance",
            artist = "HOME",
            album = "Odyssey",
            albumId = 20L,
            durationMs = 212000L,
            sizeBytes = 3500000L,
            dateModified = 1000L,
            source = "LOCAL"
        )

        val intent = TrackShareHelper.createShareIntent(track)
        assertNotNull(intent)
    }
}
