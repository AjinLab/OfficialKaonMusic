package com.kaon.music.media.state

import com.kaon.music.core.playback.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PlaybackStateStore {

    private val _state = MutableStateFlow(
        PlaybackState()
    )

    val state: StateFlow<PlaybackState> =
        _state.asStateFlow()

    fun update(
        transform: (PlaybackState) -> PlaybackState
    ) {
        _state.update(transform)
    }

    fun current(): PlaybackState =
        _state.value
}