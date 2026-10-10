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
import com.kaon.music.core.model.ErrorMapper
import com.kaon.music.core.model.LibraryController
import com.kaon.music.core.model.PlayerController
import com.kaon.music.core.designsystem.theme.GenreClassicalBrush
import com.kaon.music.core.designsystem.theme.GenreElectronicBrush
import com.kaon.music.core.designsystem.theme.GenreHipHopBrush
import com.kaon.music.core.designsystem.theme.GenreIndieBrush
import com.kaon.music.core.designsystem.theme.GenreJazzBrush
import com.kaon.music.core.designsystem.theme.GenrePopBrush
import com.kaon.music.core.designsystem.theme.GenreRnBBrush
import com.kaon.music.core.designsystem.theme.GenreRockBrush
import com.kaon.music.core.network.NetworkMonitor

import com.kaon.music.feature.search.component.GenreItem
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
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
    /**
     * The online search *failed*. Deliberately separate from the empty lists: a network failure must
     * never render as "no results" (Phase 6A §12).
     */
    val onlineSearchFailed: Boolean = false,
    val isOnline: Boolean = true,
    val genres: List<GenreItem> = emptyList(),
    val activeTrackId: Long? = null,
    val isPlaying: Boolean = false
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val libraryController: LibraryController,
    private val playerController: PlayerController,
    private val networkConnectivityMonitor: NetworkMonitor? = null,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = libraryController.observePlaylists()
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
    private val _onlineSearchFailed = MutableStateFlow(false)

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

    /**
     * Debounced, normalized query driving the local (database) searches.
     *
     * The online collectors below debounce separately because their tolerance differs: a network
     * search is worth waiting longer for than an indexed local query.
     */
    private val localQuery: Flow<String> = _searchQuery
        .debounce { if (it.isBlank()) 0L else LOCAL_SEARCH_DEBOUNCE_MS }
        .map { it.trim() }
        .distinctUntilChanged()

    /**
     * Local matches, resolved by SQL.
     *
     * Previously this ViewModel observed the entire track, album, and artist tables and filtered them
     * in Kotlin on every query change — the single largest full-library materialisation in the app.
     * Each of these is now a bounded database query that returns only what the section renders.
     * `flatMapLatest` cancels the in-flight query when the term changes.
     */
    private val localTracks: Flow<List<Track>> = combine(localQuery, _selectedFilter) { q, f -> q to f }
        .flatMapLatest { (query, filter) ->
            if (query.isBlank() || filter !in LOCAL_TRACK_FILTERS) {
                flowOf(emptyList())
            } else {
                libraryController.searchTracks(query, limit = LOCAL_SECTION_LIMIT)
            }
        }

    private val localAlbums: Flow<List<Album>> = combine(localQuery, _selectedFilter) { q, f -> q to f }
        .flatMapLatest { (query, filter) ->
            if (query.isBlank() || filter !in LOCAL_ALBUM_FILTERS) {
                flowOf(emptyList())
            } else {
                libraryController.searchAlbums(query, limit = LOCAL_SECTION_LIMIT)
            }
        }

    private val localArtists: Flow<List<Artist>> = combine(localQuery, _selectedFilter) { q, f -> q to f }
        .flatMapLatest { (query, filter) ->
            if (query.isBlank() || filter !in LOCAL_ARTIST_FILTERS) {
                flowOf(emptyList())
            } else {
                libraryController.searchArtists(query, limit = LOCAL_SECTION_LIMIT)
            }
        }


    private data class LocalResults(
        val tracks: List<Track>,
        val albums: List<Album>,
        val artists: List<Artist>
    )

    private val localResults: Flow<LocalResults> =
        combine(localTracks, localAlbums, localArtists) { tracks, albums, artists ->
            LocalResults(tracks, albums, artists)
        }

    private data class OnlineResults(
        val topResult: TopResultItem?,
        val tracks: List<Track>,
        val albums: List<Album>,
        val artists: List<Artist>,
        val playlists: List<OnlinePlaylist>,
        val isSearching: Boolean,
        val failed: Boolean
    )

    private val onlineResults: Flow<OnlineResults> = combine(
        _topResult,
        _onlineTracks,
        _onlineAlbums,
        combine(_onlineArtists, _onlinePlaylists, _isSearchingOnline) { artists, playlists, searching ->
            Triple(artists, playlists, searching)
        }
    ) { top, tracks, albums, (artists, playlists, searching) ->
        OnlineResults(top, tracks, albums, artists, playlists, searching, _onlineSearchFailed.value)
    }

    private data class QueryMeta(
        val query: String,
        val filter: SearchFilterType,
        val suggestions: List<String>,
        val isOnline: Boolean
    )

    private val queryMeta: Flow<QueryMeta> = combine(
        _searchQuery,
        _selectedFilter,
        _suggestions,
        isOnlineFlow
    ) { query, filter, suggestions, isOnline ->
        QueryMeta(query, filter, suggestions, isOnline)
    }

    private val searchContent = combine(
        queryMeta,
        localResults,
        onlineResults
    ) { meta, local, online ->
        SearchUiState(
            searchQuery = meta.query,
            selectedFilter = meta.filter,
            suggestions = meta.suggestions,
            topResult = if (meta.isOnline &&
                (meta.filter == SearchFilterType.ALL || meta.filter == SearchFilterType.SONGS)
            ) {
                online.topResult
            } else {
                null
            },
            matchingTracks = local.tracks,
            matchingAlbums = local.albums,
            matchingArtists = local.artists,
            onlineTracks = if (meta.isOnline) online.tracks else emptyList(),
            onlineAlbums = if (meta.isOnline) online.albums else emptyList(),
            onlineArtists = if (meta.isOnline) online.artists else emptyList(),
            onlinePlaylists = if (meta.isOnline) online.playlists else emptyList(),
            isSearchingOnline = online.isSearching && meta.isOnline,
            onlineSearchFailed = online.failed && meta.isOnline,
            isOnline = meta.isOnline,
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
        playerController.nowPlaying
            .map { it.currentTrack?.id?.value to it.isPlaying }
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
            _onlineSearchFailed.value = false
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
            _onlineSearchFailed.value = false
            _topResult.value = null
            _onlineTracks.value = emptyList()
            _onlineAlbums.value = emptyList()
            _onlineArtists.value = emptyList()
            _onlinePlaylists.value = emptyList()
            _isSearchingOnline.value = false
            return
        }

        onlineSearchJob?.cancel()
        _onlineSearchFailed.value = false
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
                            _onlineTracks.value = songs.distinctBy { it.stableKey }
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
                                id = it.externalId.orEmpty(),
                                title = it.title,
                                subtitle = "Song • ${it.artist}",
                                type = com.kaon.music.core.data.model.TopResultType.SONG,
                                thumbnailUrl = (it.artwork as? com.kaon.music.core.data.model.ArtworkRef.RemoteUrl)?.url,
                                track = it
                            )
                        }
                        _onlineTracks.value = songs.distinctBy { it.stableKey }
                        _onlineAlbums.value = emptyList()
                        _onlineArtists.value = emptyList()
                        _onlinePlaylists.value = emptyList()
                    }
                    SearchFilterType.VIDEOS -> {
                        val res = YouTube.search(q, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()
                        val videos = res?.items?.filterIsInstance<SongItem>()?.map { YtItemMapper.songItemToTrack(it) }.orEmpty()
                        _topResult.value = null
                        _onlineTracks.value = videos.distinctBy { it.stableKey }
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                // The query changed or the screen went away: cancellation is not a failure (§11).
                throw e
            } catch (e: Exception) {
                val error = ErrorMapper.fromThrowable(e)
                Timber.w(e, "Online search failed for query: $q (filter=$filter) as ${error.kind}")
                _onlineSearchFailed.value = true
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
        playerController.playTrack(track, activeQueue)
    }

    fun playNext(track: Track) {
        playerController.playNext(track)
    }

    fun addToQueue(track: Track) {
        playerController.enqueue(track)
    }

    /**
     * Favorites a track, persisting provider media first.
     *
     * Takes the whole [Track] rather than an id because a YouTube search result has no id yet; the
     * repository upserts it and favorites the resulting row (spec §23).
     */
    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            libraryController.toggleFavorite(track)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch {
            libraryController.addTrack(playlistId, track)
        }
    }

    fun createPlaylistAndAddTrack(name: String, track: Track) {
        viewModelScope.launch {
            val plId = libraryController.createPlaylist(name)
            libraryController.addTrack(plId, track)
        }
    }


    private companion object {
        const val KEY_QUERY = "search.query"

        /** Local (indexed SQL) search settles faster than the network search, so it debounces less. */
        const val LOCAL_SEARCH_DEBOUNCE_MS = 200L

        /** Each local section is a capped list inside a scrolling page, not a full result set. */
        const val LOCAL_SECTION_LIMIT = 50

        val LOCAL_TRACK_FILTERS = setOf(
            SearchFilterType.ALL,
            SearchFilterType.SONGS,
            SearchFilterType.VIDEOS
        )
        val LOCAL_ALBUM_FILTERS = setOf(SearchFilterType.ALL, SearchFilterType.ALBUMS)
        val LOCAL_ARTIST_FILTERS = setOf(SearchFilterType.ALL, SearchFilterType.ARTISTS)
    }
}
