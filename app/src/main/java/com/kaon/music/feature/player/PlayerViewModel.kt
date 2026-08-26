package com.kaon.music.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaon.music.core.data.repository.PlaylistRepository
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
    private val trackRepository: TrackRepository,
    private val playlistRepository: PlaylistRepository? = null
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = playbackFacade.playbackState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PlaybackState()
        )

    private val _isFullPlayerExpanded = MutableStateFlow(false)
    val isFullPlayerExpanded: StateFlow<Boolean> = _isFullPlayerExpanded.asStateFlow()

    private val _isQueueSheetVisible = MutableStateFlow(false)
    val isQueueSheetVisible: StateFlow<Boolean> = _isQueueSheetVisible.asStateFlow()

    fun expandFullPlayer() {
        _isFullPlayerExpanded.value = true
    }

    fun collapseFullPlayer() {
        _isFullPlayerExpanded.value = false
        _isQueueSheetVisible.value = false
    }

    fun toggleQueueSheet() {
        _isQueueSheetVisible.value = !_isQueueSheetVisible.value
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

    fun removeQueueItem(index: Int) {
        playbackFacade.removeQueueItem(index)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        playbackFacade.moveQueueItem(fromIndex, toIndex)
    }

    fun clearQueue() {
        playbackFacade.clearQueue()
        collapseFullPlayer()
    }

    fun toggleFavorite(trackId: Long) {
        viewModelScope.launch {
            trackRepository.toggleFavorite(trackId)
        }
    }

    fun saveQueueAsPlaylist(name: String, onComplete: () -> Unit = {}) {
        val repo = playlistRepository ?: return
        val currentQueue = playbackFacade.playbackState.value.queue
        if (currentQueue.isEmpty()) return
        viewModelScope.launch {
            val playlistId = repo.createPlaylist(name)
            repo.addTracksToPlaylist(playlistId, currentQueue.map { it.id })
            onComplete()
        }
    }
}
