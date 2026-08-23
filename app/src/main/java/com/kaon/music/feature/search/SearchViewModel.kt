package com.kaon.music.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaon.music.core.data.model.Album
import com.kaon.music.core.data.model.Artist
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.data.repository.TrackRepository
import com.kaon.music.core.designsystem.theme.GenreClassicalBrush
import com.kaon.music.core.designsystem.theme.GenreElectronicBrush
import com.kaon.music.core.designsystem.theme.GenreHipHopBrush
import com.kaon.music.core.designsystem.theme.GenreIndieBrush
import com.kaon.music.core.designsystem.theme.GenreJazzBrush
import com.kaon.music.core.designsystem.theme.GenrePopBrush
import com.kaon.music.core.designsystem.theme.GenreRnBBrush
import com.kaon.music.core.designsystem.theme.GenreRockBrush
import com.kaon.music.core.playback.PlaybackFacade
import com.kaon.music.feature.search.component.GenreItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchUiState(
    val searchQuery: String = "",
    val matchingTracks: List<Track> = emptyList(),
    val matchingAlbums: List<Album> = emptyList(),
    val matchingArtists: List<Artist> = emptyList(),
    val genres: List<GenreItem> = emptyList(),
    val activeTrackId: Long? = null,
    val isPlaying: Boolean = false
)

class SearchViewModel(
    private val trackRepository: TrackRepository,
    private val playbackFacade: PlaybackFacade
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val defaultGenres = listOf(
        GenreItem("Pop", GenrePopBrush),
        GenreItem("Hip-Hop", GenreHipHopBrush),
        GenreItem("Rock", GenreRockBrush),
        GenreItem("Electronic", GenreElectronicBrush),
        GenreItem("R&B", GenreRnBBrush),
        GenreItem("Jazz", GenreJazzBrush),
        GenreItem("Indie", GenreIndieBrush),
        GenreItem("Classical", GenreClassicalBrush)
    )

    val uiState: StateFlow<SearchUiState> = combine(
        _searchQuery,
        trackRepository.observeAllTracks(),
        trackRepository.observeAllAlbums(),
        trackRepository.observeAllArtists(),
        playbackFacade.playbackState
    ) { query, allTracks, allAlbums, allArtists, playback ->
        val q = query.trim().lowercase()
        val (tracks, albums, artists) = if (q.isBlank()) {
            Triple(emptyList(), emptyList(), emptyList())
        } else {
            val matchedTracks = allTracks.filter {
                it.title.lowercase().contains(q) ||
                        it.artist.lowercase().contains(q) ||
                        it.album.lowercase().contains(q)
            }
            val matchedAlbums = allAlbums.filter {
                it.title.lowercase().contains(q) ||
                        it.artist.lowercase().contains(q)
            }
            val matchedArtists = allArtists.filter {
                it.name.lowercase().contains(q)
            }
            Triple(matchedTracks, matchedAlbums, matchedArtists)
        }

        SearchUiState(
            searchQuery = query,
            matchingTracks = tracks,
            matchingAlbums = albums,
            matchingArtists = artists,
            genres = defaultGenres,
            activeTrackId = playback.currentTrack?.id,
            isPlaying = playback.isPlaying
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SearchUiState(genres = defaultGenres)
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onGenreSelected(genre: GenreItem) {
        _searchQuery.value = genre.name
    }

    fun playTrack(track: Track, queue: List<Track>? = null) {
        val activeQueue = queue ?: uiState.value.matchingTracks
        playbackFacade.playTrack(track, activeQueue)
    }

    fun playNext(track: Track) {
        playbackFacade.playNext(track)
    }

    fun addToQueue(track: Track) {
        playbackFacade.enqueue(track)
    }

    fun toggleFavorite(trackId: Long) {
        viewModelScope.launch {
            trackRepository.toggleFavorite(trackId)
        }
    }
}
