package com.kaon.music.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaon.music.core.data.model.Album
import com.kaon.music.core.data.model.Artist
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.data.repository.TrackRepository
import com.kaon.music.core.playback.PlaybackFacade
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val yourMixTracks: List<Track> = emptyList(),
    val heavyRotationTracks: List<Track> = emptyList(),
    val recentlyAddedTracks: List<Track> = emptyList(),
    val favoriteTracks: List<Track> = emptyList(),
    val recentTracks: List<Track> = emptyList(),
    val recentAlbums: List<Album> = emptyList(),
    val recentArtists: List<Artist> = emptyList(),
    val allTracks: List<Track> = emptyList(),
    val activeTrackId: Long? = null,
    val isPlaying: Boolean = false,
    val selectedAlbum: Album? = null,
    val selectedArtist: Artist? = null
)

class HomeViewModel(
    private val trackRepository: TrackRepository,
    private val playbackFacade: PlaybackFacade
) : ViewModel() {

    private val _selectedAlbum = MutableStateFlow<Album?>(null)
    val selectedAlbum: StateFlow<Album?> = _selectedAlbum.asStateFlow()

    private val _selectedArtist = MutableStateFlow<Artist?>(null)
    val selectedArtist: StateFlow<Artist?> = _selectedArtist.asStateFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        trackRepository.observeRecentlyPlayedTracks(limit = 15),
        trackRepository.observeMostPlayedTracks(limit = 50),
        trackRepository.observeRecentlyAddedTracks(limit = 15),
        trackRepository.observeFavoriteTracks(),
        trackRepository.observeAllAlbums(),
        trackRepository.observeAllArtists(),
        trackRepository.observeAllTracks(),
        playbackFacade.playbackState
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val recentTracks = args[0] as List<Track>
        @Suppress("UNCHECKED_CAST")
        val mostPlayedTracks = args[1] as List<Track>
        @Suppress("UNCHECKED_CAST")
        val recentlyAddedTracks = args[2] as List<Track>
        @Suppress("UNCHECKED_CAST")
        val favoriteTracks = args[3] as List<Track>
        @Suppress("UNCHECKED_CAST")
        val allAlbums = args[4] as List<Album>
        @Suppress("UNCHECKED_CAST")
        val allArtists = args[5] as List<Artist>
        @Suppress("UNCHECKED_CAST")
        val allTracks = args[6] as List<Track>
        val playback = args[7] as com.kaon.music.core.playback.model.PlaybackState

        // Build "Your Mix": Top most-played tracks (shuffled) or fall back to 50 library tracks (shuffled)
        val yourMix = if (mostPlayedTracks.isNotEmpty()) {
            mostPlayedTracks.shuffled()
        } else {
            allTracks.shuffled().take(50)
        }

        HomeUiState(
            yourMixTracks = yourMix,
            heavyRotationTracks = mostPlayedTracks.take(15),
            recentlyAddedTracks = recentlyAddedTracks,
            favoriteTracks = favoriteTracks,
            recentTracks = recentTracks,
            recentAlbums = allAlbums.take(6),
            recentArtists = allArtists.take(6),
            allTracks = allTracks,
            activeTrackId = playback.currentTrack?.id,
            isPlaying = playback.isPlaying,
            selectedAlbum = _selectedAlbum.value,
            selectedArtist = _selectedArtist.value
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun playTrack(track: Track, queue: List<Track>? = null) {
        val activeQueue = queue ?: uiState.value.allTracks
        playbackFacade.playTrack(track, activeQueue)
    }

    fun playYourMix() {
        val tracks = uiState.value.yourMixTracks
        if (tracks.isNotEmpty()) {
            playbackFacade.playQueue(tracks, startIndex = 0)
        }
    }

    fun playHeavyRotation() {
        val tracks = uiState.value.heavyRotationTracks
        if (tracks.isNotEmpty()) {
            playbackFacade.playQueue(tracks.shuffled(), startIndex = 0)
        }
    }

    fun playRecentlyAdded() {
        val tracks = uiState.value.recentlyAddedTracks
        if (tracks.isNotEmpty()) {
            playbackFacade.playQueue(tracks, startIndex = 0)
        }
    }

    fun playFavorites() {
        val tracks = uiState.value.favoriteTracks
        if (tracks.isNotEmpty()) {
            playbackFacade.playQueue(tracks.shuffled(), startIndex = 0)
        }
    }

    fun selectAlbum(album: Album) {
        _selectedAlbum.value = album
    }

    fun clearSelectedAlbum() {
        _selectedAlbum.value = null
    }

    fun selectArtist(artist: Artist) {
        _selectedArtist.value = artist
    }

    fun clearSelectedArtist() {
        _selectedArtist.value = null
    }
}
