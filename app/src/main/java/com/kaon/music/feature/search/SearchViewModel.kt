package com.kaon.music.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaon.music.core.data.model.Album
import com.kaon.music.core.data.model.Artist
import com.kaon.music.core.data.model.OnlinePlaylist
import com.kaon.music.core.data.model.Playlist
import com.kaon.music.core.data.model.TopResultItem
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.data.online.YtItemMapper
import com.kaon.music.core.data.repository.PlaylistRepository
import com.kaon.music.core.data.repository.TrackRepository
import com.kaon.music.core.designsystem.theme.GenreClassicalBrush
import com.kaon.music.core.designsystem.theme.GenreElectronicBrush
import com.kaon.music.core.designsystem.theme.GenreHipHopBrush
import com.kaon.music.core.designsystem.theme.GenreIndieBrush
import com.kaon.music.core.designsystem.theme.GenreJazzBrush
import com.kaon.music.core.designsystem.theme.GenrePopBrush
import com.kaon.music.core.designsystem.theme.GenreRnBBrush
import com.kaon.music.core.designsystem.theme.GenreRockBrush
import com.kaon.music.core.network.NetworkMonitor
import com.kaon.music.core.playback.PlaybackFacade
import com.kaon.music.feature.search.component.GenreItem
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

enum class SearchFilterType(val label: String) {
    ALL("All"),
    SONGS("Songs"),
    VIDEOS("Videos"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists")
}

data class SearchUiState(
    val searchQuery: String = "",
    val selectedFilter: SearchFilterType = SearchFilterType.ALL,
    val suggestions: List<String> = emptyList(),
    val topResult: TopResultItem? = null,
    val matchingTracks: List<Track> = emptyList(),
    val matchingAlbums: List<Album> = emptyList(),
    val matchingArtists: List<Artist> = emptyList(),
    val onlineTracks: List<Track> = emptyList(),
    val onlineAlbums: List<Album> = emptyList(),
    val onlineArtists: List<Artist> = emptyList(),
    val onlinePlaylists: List<OnlinePlaylist> = emptyList(),
    val isSearchingOnline: Boolean = false,
    val isOnline: Boolean = true,
    val genres: List<GenreItem> = emptyList(),
    val activeTrackId: Long? = null,
    val isPlaying: Boolean = false
)

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val trackRepository: TrackRepository,
    private val playbackFacade: PlaybackFacade,
    private val networkConnectivityMonitor: NetworkMonitor? = null,
    private val playlistRepository: PlaylistRepository? = null,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = (playlistRepository?.observeAllPlaylists() ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Restored so rotating mid-search does not discard the query and re-issue the network search.
    private val _searchQuery = MutableStateFlow(savedStateHandle.get<String>(KEY_QUERY) ?: "")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(SearchFilterType.ALL)
    val selectedFilter: StateFlow<SearchFilterType> = _selectedFilter.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _topResult = MutableStateFlow<TopResultItem?>(null)
    private val _onlineTracks = MutableStateFlow<List<Track>>(emptyList())
    private val _onlineAlbums = MutableStateFlow<List<Album>>(emptyList())
    private val _onlineArtists = MutableStateFlow<List<Artist>>(emptyList())
    private val _onlinePlaylists = MutableStateFlow<List<OnlinePlaylist>>(emptyList())
    private val _isSearchingOnline = MutableStateFlow(false)

    private var onlineSearchJob: Job? = null
    private var suggestionsJob: Job? = null

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

    private val isOnlineFlow = networkConnectivityMonitor?.isOnline ?: MutableStateFlow(true)
    private var currentIsOnline: Boolean = true

    init {
        viewModelScope.launch {
            isOnlineFlow.collect { online ->
                currentIsOnline = online
                if (!online) {
                    onlineSearchJob?.cancel()
                    suggestionsJob?.cancel()
                    _suggestions.value = emptyList()
                    _topResult.value = null
                    _onlineTracks.value = emptyList()
                    _onlineAlbums.value = emptyList()
                    _onlineArtists.value = emptyList()
                    _onlinePlaylists.value = emptyList()
                    _isSearchingOnline.value = false
                }
            }
        }

        // Live suggestions collector
        viewModelScope.launch {
            _searchQuery
                .debounce(200)
                .distinctUntilChanged()
                .collect { query ->
                    val q = query.trim()
                    if (q.isNotBlank() && currentIsOnline) {
                        fetchSearchSuggestions(q)
                    } else {
                        _suggestions.value = emptyList()
                    }
                }
        }

        // Search execution collector
        viewModelScope.launch {
            _searchQuery
                .debounce(350)
                .distinctUntilChanged()
                .collect { query ->
                    if (currentIsOnline) {
                        performOnlineSearch(query, _selectedFilter.value)
                    } else {
                        _topResult.value = null
                        _onlineTracks.value = emptyList()
                        _onlineAlbums.value = emptyList()
                        _onlineArtists.value = emptyList()
                        _onlinePlaylists.value = emptyList()
                        _isSearchingOnline.value = false
                    }
                }
        }
    }

    private val searchContent = combine(
        _searchQuery,
        _selectedFilter,
        _suggestions,
        _topResult,
        trackRepository.observeAllTracks(),
        trackRepository.observeAllAlbums(),
        trackRepository.observeAllArtists(),
        _onlineTracks,
        _onlineAlbums,
        _onlineArtists,
        _onlinePlaylists,
        _isSearchingOnline,
        isOnlineFlow
    ) { args ->
        val query = args[0] as String
        val filter = args[1] as SearchFilterType
        @Suppress("UNCHECKED_CAST")
        val suggestions = args[2] as List<String>
        val topResult = args[3] as? TopResultItem
        @Suppress("UNCHECKED_CAST")
        val allTracks = args[4] as List<Track>
        @Suppress("UNCHECKED_CAST")
        val allAlbums = args[5] as List<Album>
        @Suppress("UNCHECKED_CAST")
        val allArtists = args[6] as List<Artist>
        @Suppress("UNCHECKED_CAST")
        val onlineTracks = args[7] as List<Track>
        @Suppress("UNCHECKED_CAST")
        val onlineAlbums = args[8] as List<Album>
        @Suppress("UNCHECKED_CAST")
        val onlineArtists = args[9] as List<Artist>
        @Suppress("UNCHECKED_CAST")
        val onlinePlaylists = args[10] as List<OnlinePlaylist>
        val isSearchingOnline = args[11] as Boolean
        val isOnline = args[12] as Boolean

        val q = query.trim().lowercase()
        val (tracks, albums, artists) = if (q.isBlank()) {
            Triple(emptyList(), emptyList(), emptyList())
        } else {
            val matchedTracks = when (filter) {
                SearchFilterType.ALL, SearchFilterType.SONGS, SearchFilterType.VIDEOS -> allTracks.filter {
                    it.title.lowercase().contains(q) ||
                        it.artist.lowercase().contains(q) ||
                        it.album.lowercase().contains(q)
                }
                else -> emptyList()
            }
            val matchedAlbums = when (filter) {
                SearchFilterType.ALL, SearchFilterType.ALBUMS -> allAlbums.filter {
                    it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
                }
                else -> emptyList()
            }
            val matchedArtists = when (filter) {
                SearchFilterType.ALL, SearchFilterType.ARTISTS -> allArtists.filter {
                    it.name.lowercase().contains(q)
                }
                else -> emptyList()
            }
            Triple(matchedTracks, matchedAlbums, matchedArtists)
        }

        SearchUiState(
            searchQuery = query,
            selectedFilter = filter,
            suggestions = suggestions,
            topResult = if (isOnline && (filter == SearchFilterType.ALL || filter == SearchFilterType.SONGS)) topResult else null,
            matchingTracks = tracks,
            matchingAlbums = albums,
            matchingArtists = artists,
            onlineTracks = if (isOnline) onlineTracks else emptyList(),
            onlineAlbums = if (isOnline) onlineAlbums else emptyList(),
            onlineArtists = if (isOnline) onlineArtists else emptyList(),
            onlinePlaylists = if (isOnline) onlinePlaylists else emptyList(),
            isSearchingOnline = isSearchingOnline && isOnline,
            isOnline = isOnline,
            genres = defaultGenres
        )
    }.flowOn(computeDispatcher)

    /**
     * ARCHITECTURE.md §3.2: playback contributes only the active track id and play/pause flag, from
     * [PlaybackFacade.nowPlaying]. The 500 ms progress tick used to be a source in the combine above,
     * which re-filtered the entire library twice per second on the main dispatcher.
     */
    val uiState: StateFlow<SearchUiState> = combine(
        searchContent,
        playbackFacade.nowPlaying
            .map { it.currentTrack?.id to it.isPlaying }
            .distinctUntilChanged()
    ) { content, (activeTrackId, isPlaying) ->
        content.copy(activeTrackId = activeTrackId, isPlaying = isPlaying)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SearchUiState(genres = defaultGenres)
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        savedStateHandle[KEY_QUERY] = query
        if (query.isBlank()) {
            _suggestions.value = emptyList()
            _topResult.value = null
            _onlineTracks.value = emptyList()
            _onlineAlbums.value = emptyList()
            _onlineArtists.value = emptyList()
            _onlinePlaylists.value = emptyList()
            _isSearchingOnline.value = false
        }
    }

    fun onFilterSelected(filter: SearchFilterType) {
        _selectedFilter.value = filter
        if (_searchQuery.value.isNotBlank() && currentIsOnline) {
            performOnlineSearch(_searchQuery.value, filter)
        }
    }

    fun onSuggestionSelected(suggestion: String) {
        _searchQuery.value = suggestion
        _suggestions.value = emptyList()
        if (currentIsOnline) {
            performOnlineSearch(suggestion, _selectedFilter.value)
        }
    }

    private fun fetchSearchSuggestions(query: String) {
        suggestionsJob?.cancel()
        suggestionsJob = viewModelScope.launch {
            try {
                val res = YouTube.searchSuggestions(query).getOrNull()
                _suggestions.value = res?.queries.orEmpty().distinct().take(8)
            } catch (e: Exception) {
                _suggestions.value = emptyList()
            }
        }
    }

    private fun performOnlineSearch(query: String, filter: SearchFilterType) {
        val q = query.trim()
        if (q.isBlank()) {
            _topResult.value = null
            _onlineTracks.value = emptyList()
            _onlineAlbums.value = emptyList()
            _onlineArtists.value = emptyList()
            _onlinePlaylists.value = emptyList()
            _isSearchingOnline.value = false
            return
        }

        onlineSearchJob?.cancel()
        onlineSearchJob = viewModelScope.launch {
            _isSearchingOnline.value = true
            try {
                when (filter) {
                    SearchFilterType.ALL -> {
                        val searchResult = YouTube.searchSummary(q)
                        val summaryPage = searchResult.getOrNull()

                        if (summaryPage != null) {
                            val songs = mutableListOf<Track>()
                            val albums = mutableListOf<Album>()
                            val artists = mutableListOf<Artist>()
                            val playlists = mutableListOf<OnlinePlaylist>()
                            var topResultItem: TopResultItem? = null

                            // Metrolist extracts Top Result card from first section if titled accordingly
                            for (summary in summaryPage.summaries) {
                                if (topResultItem == null && (summary.title.equals("Top result", ignoreCase = true) || summary.items.isNotEmpty())) {
                                    topResultItem = summary.items.firstOrNull()?.let { YtItemMapper.ytItemToTopResult(it) }
                                }
                                for (item in summary.items) {
                                    when (item) {
                                        is SongItem -> songs.add(YtItemMapper.songItemToTrack(item))
                                        is AlbumItem -> albums.add(YtItemMapper.albumItemToAlbum(item))
                                        is ArtistItem -> artists.add(YtItemMapper.artistItemToArtist(item))
                                        is PlaylistItem -> playlists.add(YtItemMapper.playlistItemToOnlinePlaylist(item))
                                        else -> Unit
                                    }
                                }
                            }

                            _topResult.value = topResultItem
                            _onlineTracks.value = songs.distinctBy { it.youtubeVideoId }
                            _onlineAlbums.value = albums.distinctBy { it.albumId }
                            _onlineArtists.value = artists.distinctBy { it.name }
                            _onlinePlaylists.value = playlists.distinctBy { it.playlistId }
                        } else {
                            _topResult.value = null
                            _onlineTracks.value = emptyList()
                            _onlineAlbums.value = emptyList()
                            _onlineArtists.value = emptyList()
                            _onlinePlaylists.value = emptyList()
                        }
                    }
                    SearchFilterType.SONGS -> {
                        val res = YouTube.search(q, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                        val songs = res?.items?.filterIsInstance<SongItem>()?.map { YtItemMapper.songItemToTrack(it) }.orEmpty()
                        _topResult.value = songs.firstOrNull()?.let {
                            TopResultItem(
                                id = it.youtubeVideoId.orEmpty(),
                                title = it.title,
                                subtitle = "Song • ${it.artist}",
                                type = com.kaon.music.core.data.model.TopResultType.SONG,
                                thumbnailUri = it.contentUri,
                                track = it
                            )
                        }
                        _onlineTracks.value = songs.distinctBy { it.youtubeVideoId }
                        _onlineAlbums.value = emptyList()
                        _onlineArtists.value = emptyList()
                        _onlinePlaylists.value = emptyList()
                    }
                    SearchFilterType.VIDEOS -> {
                        val res = YouTube.search(q, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()
                        val videos = res?.items?.filterIsInstance<SongItem>()?.map { YtItemMapper.songItemToTrack(it) }.orEmpty()
                        _topResult.value = null
                        _onlineTracks.value = videos.distinctBy { it.youtubeVideoId }
                        _onlineAlbums.value = emptyList()
                        _onlineArtists.value = emptyList()
                        _onlinePlaylists.value = emptyList()
                    }
                    SearchFilterType.ALBUMS -> {
                        val res = YouTube.search(q, YouTube.SearchFilter.FILTER_ALBUM).getOrNull()
                        val albums = res?.items?.filterIsInstance<AlbumItem>()?.map { YtItemMapper.albumItemToAlbum(it) }.orEmpty()
                        _topResult.value = null
                        _onlineTracks.value = emptyList()
                        _onlineAlbums.value = albums.distinctBy { it.albumId }
                        _onlineArtists.value = emptyList()
                        _onlinePlaylists.value = emptyList()
                    }
                    SearchFilterType.ARTISTS -> {
                        val res = YouTube.search(q, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()
                        val artists = res?.items?.filterIsInstance<ArtistItem>()?.map { YtItemMapper.artistItemToArtist(it) }.orEmpty()
                        _topResult.value = null
                        _onlineTracks.value = emptyList()
                        _onlineAlbums.value = emptyList()
                        _onlineArtists.value = artists.distinctBy { it.name }
                        _onlinePlaylists.value = emptyList()
                    }
                    SearchFilterType.PLAYLISTS -> {
                        val res = YouTube.search(q, YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST).getOrNull()
                        val playlists = res?.items?.filterIsInstance<PlaylistItem>()?.map { YtItemMapper.playlistItemToOnlinePlaylist(it) }.orEmpty()
                        _topResult.value = null
                        _onlineTracks.value = emptyList()
                        _onlineAlbums.value = emptyList()
                        _onlineArtists.value = emptyList()
                        _onlinePlaylists.value = playlists.distinctBy { it.playlistId }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Online search failed for query: $q (filter=$filter)")
                _topResult.value = null
                _onlineTracks.value = emptyList()
                _onlineAlbums.value = emptyList()
                _onlineArtists.value = emptyList()
                _onlinePlaylists.value = emptyList()
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

    fun addTrackToPlaylist(playlistId: Long, track: Track) {
        val repo = playlistRepository ?: return
        viewModelScope.launch {
            repo.addTrackToPlaylist(playlistId, track.id)
        }
    }

    fun createPlaylistAndAddTrack(name: String, track: Track) {
        val repo = playlistRepository ?: return
        viewModelScope.launch {
            val plId = repo.createPlaylist(name)
            repo.addTrackToPlaylist(plId, track.id)
        }
    }

    private companion object {
        const val KEY_QUERY = "search.query"
    }
}
