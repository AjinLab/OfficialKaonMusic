package com.kaon.music.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaon.music.core.data.repository.TrackRepository
import com.kaon.music.core.playback.PlaybackFacade
import com.kaon.music.core.playback.model.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val playbackFacade: PlaybackFacade,
    private val trackRepository: TrackRepository
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = playbackFacade.playbackState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PlaybackState()
        )

    private val _isFullPlayerExpanded = MutableStateFlow(false)
    val isFullPlayerExpanded: StateFlow<Boolean> = _isFullPlayerExpanded.asStateFlow()

    fun expandFullPlayer() {
        _isFullPlayerExpanded.value = true
    }

    fun collapseFullPlayer() {
        _isFullPlayerExpanded.value = false
    }

    fun togglePlayPause() {
        playbackFacade.togglePlayPause()
    }

    fun skipNext() {
        playbackFacade.skipNext()
    }

    fun skipPrevious() {
        playbackFacade.skipPrevious()
    }

    fun seekTo(positionMs: Long) {
        playbackFacade.seekTo(positionMs)
    }

    fun toggleShuffle() {
        playbackFacade.toggleShuffle()
    }

    fun cycleRepeatMode() {
        playbackFacade.cycleRepeatMode()
    }

    fun toggleFavorite(trackId: Long) {
        viewModelScope.launch {
            trackRepository.toggleFavorite(trackId)
        }
    }
}
