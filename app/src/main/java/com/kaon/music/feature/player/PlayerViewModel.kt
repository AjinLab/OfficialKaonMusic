package com.kaon.music.feature.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaon.music.core.data.model.LyricsResult
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.data.model.TrackMetadata
import com.kaon.music.core.model.KaonError
import com.kaon.music.core.model.LibraryController
import com.kaon.music.core.model.PlayerController
import com.kaon.music.core.playback.PlaybackEvent
import com.kaon.music.core.playback.model.NowPlaying
import com.kaon.music.core.playback.model.PlaybackProgress
import com.kaon.music.core.playback.model.PlaybackQueue
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Player screen state holder.
 *
 * ARCHITECTURE.md §3.2 and capability controller pattern: interacts with the playback engine
 * and media catalog through [PlayerController] and [LibraryController], never touching repositories
 * or platform media sessions directly.
 */
class PlayerViewModel(
    private val playerController: PlayerController,
    private val libraryController: LibraryController? = null,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    val nowPlaying: StateFlow<NowPlaying> = playerController.nowPlaying
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = NowPlaying()
        )

    val queue: StateFlow<PlaybackQueue> = playerController.queue
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PlaybackQueue()
        )

    val progress: StateFlow<PlaybackProgress> = playerController.progress

    val playbackEvents: Flow<KaonError> =
        playerController.oneShotEvents.map { event ->
            when (event) {
                is PlaybackEvent.TrackUnplayable -> KaonError.PlaybackFailed(trackTitle = event.trackTitle)
            }
        }

    val isFullPlayerExpanded: StateFlow<Boolean> =
        savedStateHandle.getStateFlow(KEY_FULL_PLAYER_EXPANDED, false)

    val isQueueSheetVisible: StateFlow<Boolean> =
        savedStateHandle.getStateFlow(KEY_QUEUE_SHEET_VISIBLE, false)

    val isLyricsSheetVisible: StateFlow<Boolean> =
        savedStateHandle.getStateFlow(KEY_LYRICS_SHEET_VISIBLE, false)

    private val _lyricsState = MutableStateFlow<LyricsResult?>(null)
    val lyricsState: StateFlow<LyricsResult?> = _lyricsState.asStateFlow()

    private val _trackMetadataState = MutableStateFlow<TrackMetadata?>(null)
    val trackMetadataState: StateFlow<TrackMetadata?> = _trackMetadataState.asStateFlow()

    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()

    private var lyricsFetchJob: Job? = null

    init {
        viewModelScope.launch {
            playerController.nowPlaying
                .map { it.currentTrack }
                .distinctUntilChanged()
                .collect { track ->
                    if (track != null) {
                        fetchLyricsAndMetadata(track)
                    } else {
                        collapseFullPlayer()
                        lyricsFetchJob?.cancel()
                        _lyricsState.value = null
                        _trackMetadataState.value = null
                        _isLoadingLyrics.value = false
                    }
                }
        }

        viewModelScope.launch {
            playerController.expandPlayerRequests.collect {
                expandFullPlayer()
            }
        }
    }

    fun fetchLyricsAndMetadata(track: Track) {
        val libCtrl = libraryController ?: return
        lyricsFetchJob?.cancel()
        lyricsFetchJob = viewModelScope.launch {
            _isLoadingLyrics.value = true
            try {
                val lyrics = libCtrl.fetchLyricsAndMetadata(track)
                _lyricsState.value = lyrics
            } catch (e: Exception) {
                _lyricsState.value = null
                _trackMetadataState.value = null
            } finally {
                _isLoadingLyrics.value = false
            }
        }
    }

    fun expandFullPlayer() {
        savedStateHandle[KEY_FULL_PLAYER_EXPANDED] = true
    }

    fun collapseFullPlayer() {
        savedStateHandle[KEY_FULL_PLAYER_EXPANDED] = false
        savedStateHandle[KEY_QUEUE_SHEET_VISIBLE] = false
        savedStateHandle[KEY_LYRICS_SHEET_VISIBLE] = false
    }

    fun setQueueSheetVisible(visible: Boolean) {
        savedStateHandle[KEY_QUEUE_SHEET_VISIBLE] = visible
        if (visible) savedStateHandle[KEY_LYRICS_SHEET_VISIBLE] = false
    }

    fun setLyricsSheetVisible(visible: Boolean) {
        savedStateHandle[KEY_LYRICS_SHEET_VISIBLE] = visible
        if (visible) {
            savedStateHandle[KEY_QUEUE_SHEET_VISIBLE] = false
            nowPlaying.value.currentTrack?.let { track ->
                if (_lyricsState.value == null && !_isLoadingLyrics.value) {
                    fetchLyricsAndMetadata(track)
                }
            }
        }
    }

    fun toggleQueueSheet() = setQueueSheetVisible(!isQueueSheetVisible.value)

    fun toggleLyricsSheet() = setLyricsSheetVisible(!isLyricsSheetVisible.value)

    fun togglePlayPause() {
        playerController.togglePlayPause()
    }

    fun skipNext() {
        playerController.skipNext()
    }

    fun skipPrevious() {
        playerController.skipPrevious()
    }

    fun seekTo(positionMs: Long) {
        playerController.seekTo(positionMs)
    }

    fun toggleShuffle() {
        playerController.toggleShuffle()
    }

    fun cycleRepeatMode() {
        playerController.cycleRepeatMode()
    }

    fun removeQueueItem(index: Int) {
        playerController.removeQueueItem(index)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        playerController.moveQueueItem(fromIndex, toIndex)
    }

    fun clearQueue() {
        playerController.clearQueue()
        collapseFullPlayer()
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            libraryController?.toggleFavorite(track)
        }
    }

    fun saveQueueAsPlaylist(name: String, onComplete: () -> Unit = {}) {
        val libCtrl = libraryController ?: return
        val currentQueue = playerController.queue.value.tracks
        if (currentQueue.isEmpty()) return
        viewModelScope.launch {
            libCtrl.saveQueueAsPlaylist(name, currentQueue)
            onComplete()
        }
    }

    private companion object {
        const val KEY_FULL_PLAYER_EXPANDED = "player.fullExpanded"
        const val KEY_QUEUE_SHEET_VISIBLE = "player.queueSheetVisible"
        const val KEY_LYRICS_SHEET_VISIBLE = "player.lyricsSheetVisible"
    }
}
