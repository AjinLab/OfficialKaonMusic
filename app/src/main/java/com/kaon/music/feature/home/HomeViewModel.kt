package com.kaon.music.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaon.music.core.data.model.Album
import com.kaon.music.core.data.model.Artist
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.data.repository.TrackRepository
import com.kaon.music.core.playback.PlaybackFacade
import com.kaon.music.feature.home.component.StationItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val recentTracks: List<Track> = emptyList(),
    val recentAlbums: List<Album> = emptyList(),
    val recentArtists: List<Artist> = emptyList(),
    val allTracks: List<Track> = emptyList(),
    val stations: List<StationItem> = emptyList(),
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

    private val defaultStations = listOf(
        StationItem("1", "Classic Rock Anthems", "The greatest hits from the 70s & 80s", "rock"),
        StationItem("2", "Electronic Pulse", "Underground techno and driving beats", "electronic"),
        StationItem("3", "Deep Focus", "Ambient soundscapes and concentration", "ambient"),
        StationItem("4", "Chill Vibes", "Lo-fi beats and relaxing melodies", "chill")
    )

    val uiState: StateFlow<HomeUiState> = combine(
        trackRepository.observeRecentlyPlayedTracks(limit = 10),
        trackRepository.observeAllAlbums(),
        trackRepository.observeAllArtists(),
        trackRepository.observeAllTracks(),
        playbackFacade.playbackState
    ) { recentTracks, allAlbums, allArtists, allTracks, playback ->
        // If recent tracks is empty, fallback to top available tracks/albums
        val displayedTracks = if (recentTracks.isNotEmpty()) recentTracks else allTracks.take(10)
        val displayedAlbums = allAlbums.take(6)
        val displayedArtists = allArtists.take(6)

        HomeUiState(
            recentTracks = displayedTracks,
            recentAlbums = displayedAlbums,
            recentArtists = displayedArtists,
            allTracks = allTracks,
            stations = defaultStations,
            activeTrackId = playback.currentTrack?.id,
            isPlaying = playback.isPlaying,
            selectedAlbum = _selectedAlbum.value,
            selectedArtist = _selectedArtist.value
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(stations = defaultStations)
    )

    fun playTrack(track: Track, queue: List<Track>? = null) {
        val activeQueue = queue ?: uiState.value.allTracks
        playbackFacade.playTrack(track, activeQueue)
    }

    fun playDiscoverWeekly() {
        val tracks = uiState.value.allTracks
        if (tracks.isNotEmpty()) {
            val shuffled = tracks.shuffled().take(25)
            playbackFacade.playQueue(shuffled, startIndex = 0)
        }
    }

    fun playChillMix() {
        val tracks = uiState.value.allTracks
        val filtered = tracks.filter {
            val lower = "${it.title} ${it.artist} ${it.album}".lowercase()
            lower.contains("chill") || lower.contains("lo-fi") || lower.contains("ambient") ||
                    lower.contains("acoustic") || lower.contains("piano") || lower.contains("calm")
        }
        val queue = if (filtered.isNotEmpty()) filtered else tracks.take(15)
        playbackFacade.playQueue(queue.shuffled(), startIndex = 0)
    }

    fun playWorkoutMix() {
        val tracks = uiState.value.allTracks
        val filtered = tracks.filter {
            val lower = "${it.title} ${it.artist} ${it.album}".lowercase()
            lower.contains("rock") || lower.contains("dance") || lower.contains("electronic") ||
                    lower.contains("hip") || lower.contains("pop") || lower.contains("workout")
        }
        val queue = if (filtered.isNotEmpty()) filtered else tracks.take(15)
        playbackFacade.playQueue(queue.shuffled(), startIndex = 0)
    }

    fun playStation(station: StationItem) {
        val tracks = uiState.value.allTracks
        val filter = station.genreFilter.lowercase()
        val filtered = tracks.filter {
            val lower = "${it.title} ${it.artist} ${it.album}".lowercase()
            lower.contains(filter)
        }
        val queue = if (filtered.isNotEmpty()) filtered else tracks.take(20)
        playbackFacade.playQueue(queue.shuffled(), startIndex = 0)
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
