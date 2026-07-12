package com.kaon.music

import com.kaon.music.core.playback.PlaybackState
import com.kaon.music.media.state.PlaybackEvent
import com.kaon.music.media.state.PlaybackStateReducer
import com.kaon.music.media.state.PlaybackStatus
import com.kaon.music.media.state.PlaybackError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateReducerTest {

    private val reducer = PlaybackStateReducer()

    @Test
    fun testStatusChanged() {
        val initial = PlaybackState(isPlaying = false)
        
        val state1 = reducer.reduce(initial, PlaybackEvent.StatusChanged(PlaybackStatus.Playing))
        assertTrue(state1.isPlaying)
        assertEquals(PlaybackStatus.Playing, state1.status)

        val state2 = reducer.reduce(state1, PlaybackEvent.StatusChanged(PlaybackStatus.Paused))
        assertFalse(state2.isPlaying)
        assertEquals(PlaybackStatus.Paused, state2.status)
    }

    @Test
    fun testMetadataLoaded() {
        val initial = PlaybackState(title = "Old Title", artist = "Old Artist")
        
        val state = reducer.reduce(
            initial,
            PlaybackEvent.MetadataLoaded(
                title = "New Title",
                artist = "New Artist",
                album = "New Album",
                audioInfo = null
            )
        )
        
        assertEquals("New Title", state.title)
        assertEquals("New Artist", state.artist)
        assertEquals("New Album", state.album)
    }

    @Test
    fun testPositionChanged() {
        val initial = PlaybackState(currentPosition = 0L)
        val state = reducer.reduce(initial, PlaybackEvent.PositionChanged(5000L))
        assertEquals(5000L, state.currentPosition)
    }

    @Test
    fun testMediaItemTransitionedResetsProgress() {
        val initial = PlaybackState(currentPosition = 5000L, duration = 120000L)
        val state = reducer.reduce(initial, PlaybackEvent.MediaItemTransitioned(42L))
        assertEquals(0L, state.currentPosition)
        assertEquals(0L, state.duration)
    }
}
