package com.kaon.music.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.kaon.music.core.data.model.Album
import com.kaon.music.core.data.model.Artist
import com.kaon.music.core.data.model.Playlist
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.model.ErrorMapper
import com.kaon.music.core.model.LibraryController
import com.kaon.music.core.model.LibrarySort
import com.kaon.music.core.model.PlayerController
import com.kaon.music.core.model.PlaylistStats
import com.kaon.music.core.model.UserMessageBus
import com.kaon.music.core.policy.LibraryPolicy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

enum class LibraryFilter {
    TRACKS,
    ALBUMS,
    ARTISTS,
    FAVORITES,
    RECENT,
    PLAYLISTS
}

enum class TrackSortOrder {
    TITLE_ASC,
    RECENTLY_ADDED,
    MOST_PLAYED;

    /** The SQL ordering this UI sort maps to. Sorting is the database's job (ARCHITECTURE.md §6). */
    internal fun toLibrarySort(): LibrarySort = when (this) {
        TITLE_ASC -> LibrarySort.TITLE_ASC
        RECENTLY_ADDED -> LibrarySort.DATE_ADDED_DESC
        MOST_PLAYED -> LibrarySort.PLAY_COUNT_DESC
    }
}

/**
 * Non-paged library state.
 *
 * The large collections — tracks, albums, artists, favorites, playlist members — are no longer here.
 * They arrive as separate `Flow<PagingData<…>>` streams the screen collects with
 * `collectAsLazyPagingItems`, so no ViewModel holds a copy of the library. What remains is either
 * bounded by a `LIMIT` (the Recent rows), tiny (playlists, selection), or a count.
 */
data class LibraryUiState(
    val selectedFilter: LibraryFilter = LibraryFilter.TRACKS,
    val trackSortOrder: TrackSortOrder = TrackSortOrder.TITLE_ASC,
    val trackCount: Int = 0,
    val favoriteCount: Int = 0,
    val recentTracks: List<Track> = emptyList(),
    val recentlyAddedTracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val selectedAlbum: Album? = null,
    val albumTracks: List<Track> = emptyList(),
    val selectedArtist: Artist? = null,
    val artistAlbums: List<Album> = emptyList(),
    val artistTracks: List<Track> = emptyList(),
    val selectedPlaylist: Playlist? = null,
    val isSyncing: Boolean = false,
    val hasPermission: Boolean = true,
    val searchQuery: String = "",
    val activeTrackId: Long? = null,
    val isPlaying: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LibraryViewModel(
    private val libraryController: LibraryController,
    private val playerController: PlayerController,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val userMessageBus: UserMessageBus? = null,
    // Injected so tests can drive the sort/filter pipeline on the test scheduler. Production keeps
    // this work off the main thread (ARCHITECTURE.md §5.4).
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {


    // Browse selection is saved state (ARCHITECTURE.md §5.5): the active filter and sort order must
    // survive rotation and process death. Album/artist/playlist selection is stored as an id and
    // re-resolved from the database, because the full objects are not Parcelable and a stale copy
    // would render outdated titles after a rescan.
    private val _selectedFilter = MutableStateFlow(
        savedStateHandle.get<String>(KEY_FILTER)?.let { runCatching { LibraryFilter.valueOf(it) }.getOrNull() }
            ?: LibraryFilter.TRACKS
    )
    private val _trackSortOrder = MutableStateFlow(
        savedStateHandle.get<String>(KEY_SORT)?.let { runCatching { TrackSortOrder.valueOf(it) }.getOrNull() }
            ?: TrackSortOrder.TITLE_ASC
    )
    private val _isSyncing = MutableStateFlow(false)
    private val _hasPermission = libraryController.hasPermission
    private val _searchQuery = MutableStateFlow(savedStateHandle.get<String>(KEY_QUERY) ?: "")

    private val _selectedAlbum = MutableStateFlow<Album?>(null)
    private val _selectedArtist = MutableStateFlow<Artist?>(null)
    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)

    /**
     * Debounced query for database-backed reads.
     *
     * Every keystroke used to re-filter the materialised library. Debouncing here means a burst of
     * typing issues one query, and `distinctUntilChanged` suppresses re-queries when normalization
     * collapses two inputs to the same term.
     */
    private val debouncedQuery: Flow<String> = _searchQuery
        .debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
        .map { it.trim() }
        .distinctUntilChanged()

    /**
     * Paged library tracks. Query and sort are both applied in SQL.
     *
     * `cachedIn(viewModelScope)` keeps loaded pages across configuration change and — importantly —
     * makes this multicast, so the screen re-collecting does not restart paging from page zero.
     */
    val pagedTracks: Flow<PagingData<Track>> = combine(
        debouncedQuery,
        _trackSortOrder
    ) { query, sort -> query to sort }
        .flatMapLatest { (query, sort) ->
            libraryController.pagedTracks(query = query, sort = sort.toLibrarySort())
        }
        .cachedIn(viewModelScope)

    val pagedFavoriteTracks: Flow<PagingData<Track>> = debouncedQuery
        .flatMapLatest { query -> libraryController.pagedFavoriteTracks(query = query) }
        .cachedIn(viewModelScope)

    val pagedAlbums: Flow<PagingData<Album>> = debouncedQuery
        .flatMapLatest { query -> libraryController.pagedAlbums(query = query) }
        .cachedIn(viewModelScope)

    val pagedArtists: Flow<PagingData<Artist>> = debouncedQuery
        .flatMapLatest { query -> libraryController.pagedArtists(query = query) }
        .cachedIn(viewModelScope)

    val pagedPlaylistTracks: Flow<PagingData<Track>> = _selectedPlaylist
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { playlistId ->
            if (playlistId == null) flowOf(PagingData.empty())
            else libraryController.pagedTracksForPlaylist(playlistId)
        }
        .cachedIn(viewModelScope)

    /** Playlist header aggregate, so the detail screen does not sum a materialised member list. */
    val playlistStats: StateFlow<PlaylistStats> = _selectedPlaylist
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { playlistId ->
            if (playlistId == null) flowOf(PlaylistStats()) else libraryController.observePlaylistStats(playlistId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaylistStats())

    private val _albumTracks = _selectedAlbum.flatMapLatest { album ->
        if (album == null) flowOf(emptyList())
        else libraryController.observeTracksForAlbum(album.albumId)
    }

    private val _artistAlbums = _selectedArtist.flatMapLatest { artist ->
        if (artist == null) flowOf(emptyList())
        else libraryController.observeAlbumsForArtist(artist.name)
    }

    private val _artistTracks = _selectedArtist.flatMapLatest { artist ->
        if (artist == null) flowOf(emptyList())
        else libraryController.observeTracksForArtist(artist.name)
    }

    private data class DetailData(
        val albumTracks: List<Track>,
        val artistAlbums: List<Album>,
        val artistTracks: List<Track>
    )

    private data class SelectedNav(
        val album: Album?,
        val artist: Artist?,
        val playlist: Playlist?,
        val query: String
    )

    /**
     * Counts and the two bounded Recent rows.
     *
     * These are the only library reads left in the state object, and each is `LIMIT`-ed or an
     * aggregate — cost is independent of library size.
     */
    private data class BrowseData(
        val trackCount: Int,
        val favoriteCount: Int,
        val recentTracks: List<Track>,
        val recentlyAddedTracks: List<Track>,
        val playlists: List<Playlist>
    )

    private val _counts = combine(
        debouncedQuery.flatMapLatest { libraryController.observeTrackCount(it) },
        debouncedQuery.flatMapLatest { libraryController.observeFavoriteCount(it) }
    ) { trackCount, favoriteCount -> trackCount to favoriteCount }

    private val _browseData = combine(
        _counts,
        libraryController.observeRecentlyPlayedTracks(limit = RECENT_ROW_LIMIT),
        libraryController.observeRecentlyAddedTracks(limit = RECENT_ROW_LIMIT),
        libraryController.observeAllPlaylists()
    ) { (trackCount, favoriteCount), recent, added, playlists ->
        BrowseData(trackCount, favoriteCount, recent, added, playlists)
    }

    private val _detailData = combine(
        _albumTracks,
        _artistAlbums,
        _artistTracks
    ) { albumTracks, artistAlbums, artistTracks ->
        DetailData(albumTracks, artistAlbums, artistTracks)
    }

    private data class SyncMeta(
        val isSyncing: Boolean,
        val hasPermission: Boolean
    )

    private val _syncMeta = combine(
        _isSyncing,
        _hasPermission
    ) { isSyncing, hasPermission ->
        SyncMeta(isSyncing, hasPermission)
    }

    private data class MetaData(
        val filter: LibraryFilter,
        val sortOrder: TrackSortOrder,
        val isSyncing: Boolean,
        val hasPermission: Boolean
    )

    private val _metaData = combine(
        _selectedFilter,
        _trackSortOrder,
        _syncMeta
    ) { filter, sortOrder, sync ->
        MetaData(filter, sortOrder, sync.isSyncing, sync.hasPermission)
    }

    private val _navData = combine(
        _selectedAlbum,
        _selectedArtist,
        _selectedPlaylist,
        _searchQuery
    ) { album, artist, playlist, query ->
        SelectedNav(album, artist, playlist, query)
    }

    /**
     * Non-paged library state.
     *
     * ARCHITECTURE.md §3.2 and §5.4: playback state is deliberately absent from this combine, and
     * the whole chain runs on [Dispatchers.Default]. There is no longer a full-library sort here —
     * ordering moved into SQL — so this combine assembles counts and bounded rows only.
     */
    private val libraryContent = combine(
        _browseData,
        _detailData,
        _metaData,
        _navData
    ) { browse, detail, meta, nav ->
        LibraryUiState(
            selectedFilter = meta.filter,
            trackSortOrder = meta.sortOrder,
            trackCount = browse.trackCount,
            favoriteCount = browse.favoriteCount,
            recentTracks = browse.recentTracks,
            recentlyAddedTracks = browse.recentlyAddedTracks,
            playlists = browse.playlists,
            albumTracks = detail.albumTracks,
            artistAlbums = detail.artistAlbums,
            artistTracks = detail.artistTracks,
            selectedPlaylist = nav.playlist,
            isSyncing = meta.isSyncing,
            hasPermission = meta.hasPermission,
            selectedAlbum = nav.album,
            selectedArtist = nav.artist,
            searchQuery = nav.query
        )
    }.flowOn(computeDispatcher)

    val uiState: StateFlow<LibraryUiState> = combine(
        libraryContent,
        playerController.nowPlaying
            .map { it.currentTrack?.id?.value to it.isPlaying }
            .distinctUntilChanged()
    ) { content, (activeTrackId, isPlaying) ->
        content.copy(activeTrackId = activeTrackId, isPlaying = isPlaying)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState()
    )

    init {
        // No playback-error collector here: a screen ViewModel must not be the sole owner of
        // globally relevant errors (Phase 6A §14). MainActivity forwards facade events to the
        // UserMessageBus, which the root scaffold collects on every destination.
    }

    /**
     * Selects a browse filter.
     *
     * Clearing [_selectedPlaylist] is required: LibraryScreen checks playlist detail before the
     * filter, so leaving a playlist selected made every filter chip a no-op until the user pressed
     * back.
     */
    fun selectFilter(filter: LibraryFilter) {
        _selectedFilter.value = filter
        savedStateHandle[KEY_FILTER] = filter.name
    }

    // ==================== Detail data, opened by destination id ====================
    //
    // The navigation stack owns WHERE the user is (typed ids only); this ViewModel owns loading the
    // data a detail needs once MainActivity reports the destination. Selection state remains
    // in-memory only: re-entering a detail after process death re-runs open*, so a stale object can
    // never be restored.

    /** Loads album detail for [albumId]; [onMissing] fires when the id no longer resolves. */
    fun openAlbum(albumId: Long, onMissing: () -> Unit = {}) {
        viewModelScope.launch {
            val album = libraryController.getAlbumById(albumId)
            if (album == null) onMissing() else _selectedAlbum.value = album
        }
    }

    fun openArtist(artistId: Long, artistName: String) {
        _selectedArtist.value = Artist(artistId = artistId, name = artistName)
    }

    fun openPlaylist(playlistId: Long, onMissing: () -> Unit = {}) {
        viewModelScope.launch {
            val playlist = libraryController.getPlaylist(playlistId)
            if (playlist == null) onMissing() else _selectedPlaylist.value = playlist
        }
    }

    /** Clears all detail selections when the navigation stack returns to a root. */
    fun clearDetailState() {
        _selectedAlbum.value = null
        _selectedArtist.value = null
        _selectedPlaylist.value = null
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

    fun setPermissionState(granted: Boolean) {
        libraryController.setPermissionState(granted)
        if (granted) {
            triggerSync()
        }
    }

    fun triggerSync() {
        if (!_hasPermission.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                libraryController.syncLibrary()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Typed at the boundary (ARCHITECTURE.md §5.1); the user hears it via the bus, the
                // log keeps the cause.
                val error = ErrorMapper.fromThrowable(e)
                Timber.tag("LibraryViewModel").e(e, "Sync failed")
                userMessageBus?.post(error)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        savedStateHandle[KEY_QUERY] = query
    }

    /**
     * Plays a track in the context of the list it was tapped in.
     *
     * The queue is fetched from the database in the list's current SQL order and bounded by
     * [TrackRepository.MAX_QUEUE_SIZE], because the visible list is paged: the loaded pages are not
     * the whole context, and materialising the whole context to enqueue it is exactly what this phase
     * removes. Playback starts immediately from a single-track queue, then the surrounding context
     * replaces it — otherwise the user waits on a database read before hearing anything.
     */
    fun playTrack(track: Track, contextQueue: List<Track>? = null) {
        if (contextQueue != null) {
            playerController.playTrack(track, contextQueue)
            return
        }
        val favoritesOnly = uiState.value.selectedFilter == LibraryFilter.FAVORITES
        viewModelScope.launch {
            val queue = libraryController.getQueueForContext(
                query = _searchQuery.value,
                sort = _trackSortOrder.value.toLibrarySort(),
                favoritesOnly = favoritesOnly
            )
            playerController.playTrack(track, queue.ifEmpty { listOf(track) })
        }
    }

    fun playAlbum(album: Album, shuffle: Boolean = false) {
        viewModelScope.launch {
            val tracks = libraryController.getTracksForAlbum(album.albumId)
            if (tracks.isNotEmpty()) {
                val queue = if (shuffle) LibraryPolicy.shuffled(tracks, shuffleSeed()) else tracks
                playerController.playQueue(queue, startIndex = 0)
            }
        }
    }

    fun playArtist(artist: Artist) {
        viewModelScope.launch {
            val tracks = libraryController.getTracksForArtist(artist.name)
            if (tracks.isNotEmpty()) {
                playerController.playQueue(tracks, startIndex = 0)
            }
        }
    }

    fun setTrackSortOrder(order: TrackSortOrder) {
        _trackSortOrder.value = order
        savedStateHandle[KEY_SORT] = order.name
    }

    /**
     * Plays the favorites list.
     *
     * Read from the database rather than from UI state: the favorites list is paged, so UI state no
     * longer holds it.
     */
    fun playLikedSongs(shuffle: Boolean = false) {
        viewModelScope.launch {
            val favs = libraryController.getQueueForContext(
                query = _searchQuery.value,
                favoritesOnly = true
            )
            if (favs.isNotEmpty()) {
                val queue = if (shuffle) LibraryPolicy.shuffled(favs, shuffleSeed()) else favs
                playerController.playQueue(queue, startIndex = 0)
            }
        }
    }

    fun playRecentlyPlayed(shuffle: Boolean = false) {
        val tracks = uiState.value.recentTracks
        if (tracks.isNotEmpty()) {
            val queue = if (shuffle) LibraryPolicy.shuffled(tracks, shuffleSeed()) else tracks
            playerController.playQueue(queue, startIndex = 0)
        }
    }

    fun playRecentlyAdded(shuffle: Boolean = false) {
        val tracks = uiState.value.recentlyAddedTracks
        if (tracks.isNotEmpty()) {
            val queue = if (shuffle) LibraryPolicy.shuffled(tracks, shuffleSeed()) else tracks
            playerController.playQueue(queue, startIndex = 0)
        }
    }

    fun playNext(track: Track) {
        playerController.playNext(track)
        userMessageBus?.post("Playing '${track.displayTitle}' next")
    }

    fun addToQueue(track: Track) {
        playerController.enqueue(track)
        userMessageBus?.post("Added '${track.displayTitle}' to queue")
    }

    /** Favorites by [Track]: a streamed track is upserted first, so the favorite cannot silently fail. */
    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            libraryController.toggleFavorite(track)
        }
    }

    // ==================== Playlist Actions (M5-D1 - M5-D5) ====================

    fun selectPlaylist(playlist: Playlist?) {
        _selectedPlaylist.value = playlist
    }

    fun createPlaylist(name: String, trackToAppend: Track? = null) {
        viewModelScope.launch {
            val trimmed = name.trim().ifBlank { "New Playlist" }
            val id = libraryController.createPlaylist(trimmed)
            // addTrack, not addTrackToPlaylist: the track may be an unsaved provider result.
            if (trackToAppend != null) {
                libraryController.addTrack(id, trackToAppend)
            }
            userMessageBus?.post("Created playlist '$trimmed'")
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        viewModelScope.launch {
            val trimmed = newName.trim()
            if (trimmed.isNotBlank()) {
                libraryController.renamePlaylist(playlistId, trimmed)
                if (_selectedPlaylist.value?.id == playlistId) {
                    _selectedPlaylist.value = _selectedPlaylist.value?.copy(name = trimmed)
                }
                userMessageBus?.post("Renamed playlist to '$trimmed'")
            }
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            val name = _selectedPlaylist.value?.name
            libraryController.deletePlaylist(playlistId)
            if (_selectedPlaylist.value?.id == playlistId) {
                _selectedPlaylist.value = null
            }
            userMessageBus?.post(if (name != null) "Deleted playlist '$name'" else "Deleted playlist")
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: Track, trackTitle: String? = null, playlistName: String? = null) {
        viewModelScope.launch {
            val added = libraryController.addTrack(playlistId, track)
            val msg = if (added) {
                if (trackTitle != null && playlistName != null) {
                    "Added '$trackTitle' to $playlistName"
                } else {
                    "Added track to playlist"
                }
            } else {
                "Track already in playlist"
            }
            userMessageBus?.post(msg)
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            libraryController.removeTrackFromPlaylist(playlistId, trackId)
            userMessageBus?.post("Removed track from playlist")
        }
    }

    fun reorderPlaylistTracks(playlistId: Long, orderedTrackIds: List<Long>) {
        viewModelScope.launch {
            libraryController.reorderTracks(playlistId, orderedTrackIds)
        }
    }

    /**
     * Moves one playlist member by [delta] positions.
     *
     * The full ordered id list comes from the database rather than from the visible page, so a move
     * near a page boundary still produces a correct global order. The screen only knows the track it
     * moved and the direction.
     */
    fun movePlaylistTrack(playlistId: Long, trackId: Long, delta: Int) {
        if (delta == 0) return
        viewModelScope.launch {
            val ids = libraryController.getOrderedTrackIds(playlistId).toMutableList()
            val from = ids.indexOf(trackId)
            if (from < 0) return@launch
            val to = (from + delta).coerceIn(0, ids.lastIndex)
            if (to == from) return@launch
            ids.removeAt(from)
            ids.add(to, trackId)
            libraryController.reorderTracks(playlistId, ids)
        }
    }

    fun playPlaylist(playlist: Playlist, shuffle: Boolean = false, startIndex: Int = 0) {
        viewModelScope.launch {
            val tracks = libraryController.getTracksForPlaylist(playlist.id)
            if (tracks.isNotEmpty()) {
                val queue = if (shuffle) LibraryPolicy.shuffled(tracks, shuffleSeed()) else tracks
                val start = if (shuffle) 0 else startIndex.coerceIn(0, queue.size - 1)
                playerController.playQueue(queue, startIndex = start)
            }
        }
    }

    /** Fresh seed per user-initiated shuffle; never used inside a flow transform. */
    private fun shuffleSeed(): Long = System.currentTimeMillis()

    private companion object {
        const val KEY_FILTER = "library.filter"
        const val KEY_SORT = "library.sortOrder"
        const val KEY_QUERY = "library.query"

        /**
         * Query debounce for the database-backed lists. Long enough to collapse a typing burst into
         * one query, short enough that the list feels live.
         */
        const val SEARCH_DEBOUNCE_MS = 250L

        /** Recent rows are horizontal carousels; more than this is never on screen. */
        const val RECENT_ROW_LIMIT = 50
    }
}
