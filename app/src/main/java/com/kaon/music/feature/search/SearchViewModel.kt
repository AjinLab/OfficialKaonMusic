package com.kaon.music.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaon.music.core.data.model.Album
import com.kaon.music.core.data.model.Artist
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.data.online.YtItemMapper
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
import com.kaon.music.core.playback.model.PlaybackState
import com.kaon.music.feature.search.component.GenreItem
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

data class SearchUiState(
    val searchQuery: String = "",
    val matchingTracks: List<Track> = emptyList(),
    val matchingAlbums: List<Album> = emptyList(),
    val matchingArtists: List<Artist> = emptyList(),
    val onlineTracks: List<Track> = emptyList(),
    val onlineAlbums: List<Album> = emptyList(),
    val onlineArtists: List<Artist> = emptyList(),
    val isSearchingOnline: Boolean = false,
    val genres: List<GenreItem> = emptyList(),
    val activeTrackId: Long? = null,
    val isPlaying: Boolean = false
)

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val trackRepository: TrackRepository,
    private val playbackFacade: PlaybackFacade
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _onlineTracks = MutableStateFlow<List<Track>>(emptyList())
    private val _onlineAlbums = MutableStateFlow<List<Album>>(emptyList())
    private val _onlineArtists = MutableStateFlow<List<Artist>>(emptyList())
    private val _isSearchingOnline = MutableStateFlow(false)

    private var onlineSearchJob: Job? = null

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

    init {
        viewModelScope.launch {
            _searchQuery
                .debounce(400)
                .distinctUntilChanged()
                .collect { query ->
                    performOnlineSearch(query)
                }
        }
    }

    val uiState: StateFlow<SearchUiState> = combine(
        _searchQuery,
        trackRepository.observeAllTracks(),
        trackRepository.observeAllAlbums(),
        trackRepository.observeAllArtists(),
        _onlineTracks,
        _onlineAlbums,
        _onlineArtists,
        _isSearchingOnline,
        playbackFacade.playbackState
    ) { args ->
        val query = args[0] as String
        @Suppress("UNCHECKED_CAST")
        val allTracks = args[1] as List<Track>
        @Suppress("UNCHECKED_CAST")
        val allAlbums = args[2] as List<Album>
        @Suppress("UNCHECKED_CAST")
        val allArtists = args[3] as List<Artist>
        @Suppress("UNCHECKED_CAST")
        val onlineTracks = args[4] as List<Track>
        @Suppress("UNCHECKED_CAST")
        val onlineAlbums = args[5] as List<Album>
        @Suppress("UNCHECKED_CAST")
        val onlineArtists = args[6] as List<Artist>
        val isSearchingOnline = args[7] as Boolean
        val playback = args[8] as PlaybackState

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
            onlineTracks = onlineTracks,
            onlineAlbums = onlineAlbums,
            onlineArtists = onlineArtists,
            isSearchingOnline = isSearchingOnline,
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
        if (query.isBlank()) {
            _onlineTracks.value = emptyList()
            _onlineAlbums.value = emptyList()
            _onlineArtists.value = emptyList()
            _isSearchingOnline.value = false
        }
    }

    private fun performOnlineSearch(query: String) {
        val q = query.trim()
        if (q.isBlank()) {
            _onlineTracks.value = emptyList()
            _onlineAlbums.value = emptyList()
            _onlineArtists.value = emptyList()
            _isSearchingOnline.value = false
            return
        }

        onlineSearchJob?.cancel()
        onlineSearchJob = viewModelScope.launch {
            _isSearchingOnline.value = true
            try {
                val searchResult = YouTube.searchSummary(q)
                val summaryPage = searchResult.getOrNull()

                if (summaryPage != null) {
                    val songs = mutableListOf<Track>()
                    val albums = mutableListOf<Album>()
                    val artists = mutableListOf<Artist>()

                    for (summary in summaryPage.summaries) {
                        for (item in summary.items) {
                            when (item) {
                                is SongItem -> songs.add(YtItemMapper.songItemToTrack(item))
                                is AlbumItem -> albums.add(YtItemMapper.albumItemToAlbum(item))
                                is ArtistItem -> artists.add(YtItemMapper.artistItemToArtist(item))
                                else -> Unit
                            }
                        }
                    }

                    _onlineTracks.value = songs.distinctBy { it.youtubeVideoId }
                    _onlineAlbums.value = albums.distinctBy { it.albumId }
                    _onlineArtists.value = artists.distinctBy { it.name }
                } else {
                    _onlineTracks.value = emptyList()
                    _onlineAlbums.value = emptyList()
                    _onlineArtists.value = emptyList()
                }
            } catch (e: Exception) {
                Timber.w(e, "Online search failed for query: $q")
                _onlineTracks.value = emptyList()
                _onlineAlbums.value = emptyList()
                _onlineArtists.value = emptyList()
            } finally {
                _isSearchingOnline.value = false
            }
        }
    }

    fun onGenreSelected(genre: GenreItem) {
        _searchQuery.value = genre.name
    }

    fun playTrack(track: Track, queue: List<Track>? = null) {
        val activeQueue = queue ?: (uiState.value.matchingTracks + uiState.value.onlineTracks)
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
