package com.kaon.music.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.data.repository.TrackRepository
import com.kaon.music.core.playback.PlaybackFacade
import com.kaon.music.core.playback.model.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

data class LibraryUiState(
    val tracks: List<Track> = emptyList(),
    val isSyncing: Boolean = false,
    val searchQuery: String = "",
    val activeTrackId: Long? = null,
    val isPlaying: Boolean = false
)

class LibraryViewModel(
    private val trackRepository: TrackRepository,
    private val playbackFacade: PlaybackFacade
) : ViewModel() {

    private val _isSyncing = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<LibraryUiState> = combine(
        trackRepository.observeAllTracks(),
        playbackFacade.playbackState,
        _isSyncing,
        _searchQuery
    ) { tracks, playback, isSyncing, query ->
        val filtered = if (query.isBlank()) {
            tracks
        } else {
            val q = query.lowercase().trim()
            tracks.filter {
                it.title.lowercase().contains(q) ||
                        it.artist.lowercase().contains(q) ||
                        it.album.lowercase().contains(q)
            }
        }

        LibraryUiState(
            tracks = filtered,
            isSyncing = isSyncing,
            searchQuery = query,
            activeTrackId = playback.currentTrack?.id,
            isPlaying = playback.isPlaying
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState()
    )

    init {
        triggerSync()
    }

    fun triggerSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                trackRepository.syncLibrary()
            } catch (e: Exception) {
                Timber.tag("LibraryViewModel").e(e, "Sync failed")
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun playTrack(track: Track) {
        val currentTracks = uiState.value.tracks
        playbackFacade.playTrack(track, currentTracks)
    }

    fun toggleFavorite(trackId: Long) {
        viewModelScope.launch {
            trackRepository.toggleFavorite(trackId)
        }
    }
}
