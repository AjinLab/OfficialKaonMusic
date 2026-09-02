package com.kaon.music.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaon.music.core.data.model.LyricsResult
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.data.model.TrackMetadata
import com.kaon.music.core.data.repository.MetadataRepository
import com.kaon.music.core.data.repository.PlaylistRepository
import com.kaon.music.core.data.repository.TrackRepository
import com.kaon.music.core.playback.PlaybackFacade
import com.kaon.music.core.playback.model.PlaybackState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val playbackFacade: PlaybackFacade,
    private val trackRepository: TrackRepository,
    private val playlistRepository: PlaylistRepository? = null,
    private val metadataRepository: MetadataRepository? = null
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

    private val _isLyricsSheetVisible = MutableStateFlow(false)
    val isLyricsSheetVisible: StateFlow<Boolean> = _isLyricsSheetVisible.asStateFlow()

    private val _lyricsState = MutableStateFlow<LyricsResult?>(null)
    val lyricsState: StateFlow<LyricsResult?> = _lyricsState.asStateFlow()

    private val _trackMetadataState = MutableStateFlow<TrackMetadata?>(null)
    val trackMetadataState: StateFlow<TrackMetadata?> = _trackMetadataState.asStateFlow()

    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()

    private var lyricsFetchJob: Job? = null

    init {
        viewModelScope.launch {
            playbackFacade.playbackState
                .map { it.currentTrack }
                .distinctUntilChanged()
                .collect { track ->
                    if (track != null) {
                        fetchLyricsAndMetadata(track)
                    } else {
                        // Losing the current track (queue emptied, last item removed) leaves the
                        // full player with nothing to show, so collapse instead of keeping a
                        // stale expanded flag that reappears with the next track.
                        collapseFullPlayer()
                        lyricsFetchJob?.cancel()
                        _lyricsState.value = null
                        _trackMetadataState.value = null
                        _isLoadingLyrics.value = false
                    }
                }
        }
    }

    fun fetchLyricsAndMetadata(track: Track) {
        val metaRepo = metadataRepository ?: return
        lyricsFetchJob?.cancel()
        lyricsFetchJob = viewModelScope.launch {
            _isLoadingLyrics.value = true
            try {
                val lyrics = metaRepo.getLyrics(track)
                _lyricsState.value = lyrics
                val metadata = metaRepo.getTrackMetadata(track)
                _trackMetadataState.value = metadata
            } catch (e: Exception) {
                _lyricsState.value = null
                _trackMetadataState.value = null
            } finally {
                _isLoadingLyrics.value = false
            }
        }
    }

    fun expandFullPlayer() {
        _isFullPlayerExpanded.value = true
    }

    fun collapseFullPlayer() {
        _isFullPlayerExpanded.value = false
        _isQueueSheetVisible.value = false
        _isLyricsSheetVisible.value = false
    }

    fun toggleQueueSheet() {
        _isQueueSheetVisible.value = !_isQueueSheetVisible.value
        if (_isQueueSheetVisible.value) {
            _isLyricsSheetVisible.value = false
        }
    }

    fun toggleLyricsSheet() {
        _isLyricsSheetVisible.value = !_isLyricsSheetVisible.value
        if (_isLyricsSheetVisible.value) {
            _isQueueSheetVisible.value = false
            playbackState.value.currentTrack?.let { track ->
                if (_lyricsState.value == null && !_isLoadingLyrics.value) {
                    fetchLyricsAndMetadata(track)
                }
            }
        }
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
