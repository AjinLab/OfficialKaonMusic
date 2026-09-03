package com.kaon.music.feature.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaon.music.core.data.model.LyricsResult
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.data.model.TrackMetadata
import com.kaon.music.core.data.repository.MetadataRepository
import com.kaon.music.core.data.repository.PlaylistRepository
import com.kaon.music.core.data.repository.TrackRepository
import com.kaon.music.core.playback.PlaybackFacade
import com.kaon.music.core.playback.model.NowPlaying
import com.kaon.music.core.playback.model.PlaybackProgress
import com.kaon.music.core.playback.model.PlaybackQueue
import kotlinx.coroutines.Job
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
 * ARCHITECTURE.md §3.2: exposes [nowPlaying], [queue], and [progress] as three separate flows. The
 * player is the only screen permitted to observe [progress]; screens that only need to know which
 * track is active observe [nowPlaying].
 */
class PlayerViewModel(
    private val playbackFacade: PlaybackFacade,
    private val trackRepository: TrackRepository,
    private val playlistRepository: PlaylistRepository? = null,
    private val metadataRepository: MetadataRepository? = null,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    val nowPlaying: StateFlow<NowPlaying> = playbackFacade.nowPlaying
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = NowPlaying()
        )

    val queue: StateFlow<PlaybackQueue> = playbackFacade.queue
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PlaybackQueue()
        )

    /**
     * Ticks every 500 ms during playback. Read only where progress is drawn — never fold this into a
     * screen-level state object.
     */
    val progress: StateFlow<PlaybackProgress> = playbackFacade.progress
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PlaybackProgress()
        )

    /**
     * Survives configuration change and process death via [SavedStateHandle] (ARCHITECTURE.md §5.5).
     * Previously a plain MutableStateFlow, so rotating with the full player open collapsed it.
     */
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
            playbackFacade.nowPlaying
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
        val currentQueue = playbackFacade.queue.value.tracks
        if (currentQueue.isEmpty()) return
        viewModelScope.launch {
            val playlistId = repo.createPlaylist(name)
            repo.addTracksToPlaylist(playlistId, currentQueue.map { it.id })
            onComplete()
        }
    }

    private companion object {
        const val KEY_FULL_PLAYER_EXPANDED = "player.fullExpanded"
        const val KEY_QUEUE_SHEET_VISIBLE = "player.queueSheetVisible"
        const val KEY_LYRICS_SHEET_VISIBLE = "player.lyricsSheetVisible"
    }
}
