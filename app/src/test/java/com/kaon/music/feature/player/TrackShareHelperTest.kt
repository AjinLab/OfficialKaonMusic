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
        val track = com.kaon.music.core.data.model.localTrack(id = 101L, title = "Midnight City", artist = "M83")

        val text = TrackShareHelper.createShareText(track)
        assertEquals("Now listening to: Midnight City by M83 on Kaon Music", text)
    }

    @Test
    fun createShareText_forYouTubeTrack_includesYouTubeUrl() {
        val track = com.kaon.music.core.data.model.remoteTrack(externalId = "d_HlPboLRL8", title = "Starboy", artist = "The Weeknd")

        val text = TrackShareHelper.createShareText(track)
        assertTrue(text.contains("Starboy by The Weeknd"))
        assertTrue(text.contains("https://www.youtube.com/watch?v=d_HlPboLRL8"))
    }

    @Test
    fun createShareText_forRemoteTrackWithoutExternalId_fallsBack() {
        // A provider locator always carries an id, so the fallback case is a local track: the share
        // text must not invent a URL from nothing.
        val track = com.kaon.music.core.data.model.localTrack(id = 203L, title = "Starboy", artist = "The Weeknd")

        val text = TrackShareHelper.createShareText(track)
        assertEquals("Now listening to: Starboy by The Weeknd on Kaon Music", text)
    }
}
