package com.kaon.music.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaon.music.core.data.model.Album
import com.kaon.music.core.data.model.Artist
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.data.repository.TrackRepository
import com.kaon.music.core.playback.PlaybackEvent
import com.kaon.music.core.playback.PlaybackFacade
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    MOST_PLAYED
}

data class LibraryUiState(
    val selectedFilter: LibraryFilter = LibraryFilter.TRACKS,
    val trackSortOrder: TrackSortOrder = TrackSortOrder.TITLE_ASC,
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val favoriteTracks: List<Track> = emptyList(),
    val recentTracks: List<Track> = emptyList(),
    val mostPlayedTracks: List<Track> = emptyList(),
    val recentlyAddedTracks: List<Track> = emptyList(),
    val playlists: List<com.kaon.music.core.data.model.Playlist> = emptyList(),
    val isLikedSongsSelected: Boolean = false,
    val selectedAlbum: Album? = null,
    val albumTracks: List<Track> = emptyList(),
    val selectedArtist: Artist? = null,
    val artistAlbums: List<Album> = emptyList(),
    val artistTracks: List<Track> = emptyList(),
    val selectedPlaylist: com.kaon.music.core.data.model.Playlist? = null,
    val playlistTracks: List<Track> = emptyList(),
    val isSyncing: Boolean = false,
    val hasPermission: Boolean = true,
    val searchQuery: String = "",
    val activeTrackId: Long? = null,
    val isPlaying: Boolean = false,
    val userMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val trackRepository: TrackRepository,
    private val playbackFacade: PlaybackFacade,
    private val playlistRepository: com.kaon.music.core.data.repository.PlaylistRepository
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow(LibraryFilter.TRACKS)
    private val _trackSortOrder = MutableStateFlow(TrackSortOrder.TITLE_ASC)
    private val _isLikedSongsSelected = MutableStateFlow(false)
    private val _isSyncing = MutableStateFlow(false)
    private val _hasPermission = MutableStateFlow(true)
    private val _searchQuery = MutableStateFlow("")
    private val _userMessage = MutableStateFlow<String?>(null)

    private val _selectedAlbum = MutableStateFlow<Album?>(null)
    private val _selectedArtist = MutableStateFlow<Artist?>(null)
    private val _selectedPlaylist = MutableStateFlow<com.kaon.music.core.data.model.Playlist?>(null)

    private val _filteredTracks = combine(
        trackRepository.observeAllTracks(),
        _searchQuery
    ) { tracks, query ->
        if (query.isBlank()) {
            tracks
        } else {
            val q = query.lowercase().trim()
            tracks.filter {
                it.title.lowercase().contains(q) ||
                        it.artist.lowercase().contains(q) ||
                        it.album.lowercase().contains(q)
            }
        }
    }

    private val _filteredAlbums = combine(
        trackRepository.observeAllAlbums(),
        _searchQuery
    ) { albums, query ->
        if (query.isBlank()) {
            albums
        } else {
            val q = query.lowercase().trim()
            albums.filter {
                it.title.lowercase().contains(q) ||
                        it.artist.lowercase().contains(q)
            }
        }
    }

    private val _filteredArtists = combine(
        trackRepository.observeAllArtists(),
        _searchQuery
    ) { artists, query ->
        if (query.isBlank()) {
            artists
        } else {
            val q = query.lowercase().trim()
            artists.filter {
                it.name.lowercase().contains(q)
            }
        }
    }

    private val _filteredFavTracks = combine(
        trackRepository.observeFavoriteTracks(),
        _searchQuery
    ) { favs, query ->
        if (query.isBlank()) {
            favs
        } else {
            val q = query.lowercase().trim()
            favs.filter {
                it.title.lowercase().contains(q) ||
                        it.artist.lowercase().contains(q) ||
                        it.album.lowercase().contains(q)
            }
        }
    }

    private val _filteredPlaylists = combine(
        playlistRepository.observeAllPlaylists(),
        _searchQuery
    ) { playlists, query ->
        if (query.isBlank()) {
            playlists
        } else {
            val q = query.lowercase().trim()
            playlists.filter { it.name.lowercase().contains(q) }
        }
    }

    private val _albumTracks = _selectedAlbum.flatMapLatest { album ->
        if (album == null) flowOf(emptyList())
        else trackRepository.observeTracksForAlbum(album.albumId)
    }

    private val _artistAlbums = _selectedArtist.flatMapLatest { artist ->
        if (artist == null) flowOf(emptyList())
        else trackRepository.observeAlbumsForArtist(artist.name)
    }

    private val _artistTracks = _selectedArtist.flatMapLatest { artist ->
        if (artist == null) flowOf(emptyList())
        else trackRepository.observeTracksForArtist(artist.name)
    }

    private val _playlistTracks = _selectedPlaylist.flatMapLatest { playlist ->
        if (playlist == null) flowOf(emptyList())
        else playlistRepository.observeTracksForPlaylist(playlist.id)
    }

    private data class BrowseData(
        val tracks: List<Track>,
        val albums: List<Album>,
        val artists: List<Artist>,
        val favTracks: List<Track>,
        val recentTracks: List<Track>,
        val mostPlayedTracks: List<Track>,
        val recentlyAddedTracks: List<Track>,
        val playlists: List<com.kaon.music.core.data.model.Playlist>
    )

    private data class DetailData(
        val albumTracks: List<Track>,
        val artistAlbums: List<Album>,
        val artistTracks: List<Track>,
        val playlistTracks: List<Track>
    )

    private data class SelectedNav(
        val album: Album?,
        val artist: Artist?,
        val playlist: com.kaon.music.core.data.model.Playlist?,
        val query: String
    )

    private val _recentData = combine(
        trackRepository.observeRecentlyPlayedTracks(limit = 50),
        trackRepository.observeMostPlayedTracks(limit = 50),
        trackRepository.observeRecentlyAddedTracks(limit = 50)
    ) { recent, mostPlayed, added ->
        Triple(recent, mostPlayed, added)
    }

    private data class BrowseSubData(
        val recent: List<Track>,
        val mostPlayed: List<Track>,
        val added: List<Track>,
        val playlists: List<com.kaon.music.core.data.model.Playlist>
    )

    private val _browseData = combine(
        _filteredTracks,
        _filteredAlbums,
        _filteredArtists,
        _filteredFavTracks,
        combine(_recentData, _filteredPlaylists) { (recent, mostPlayed, added), playlists ->
            BrowseSubData(recent, mostPlayed, added, playlists)
        }
    ) { tracks, albums, artists, favTracks, sub ->
        BrowseData(tracks, albums, artists, favTracks, sub.recent, sub.mostPlayed, sub.added, sub.playlists)
    }

    private val _detailData = combine(
        _albumTracks,
        _artistAlbums,
        _artistTracks,
        _playlistTracks
    ) { albumTracks, artistAlbums, artistTracks, playlistTracks ->
        DetailData(albumTracks, artistAlbums, artistTracks, playlistTracks)
    }

    private data class SyncMeta(
        val isSyncing: Boolean,
        val hasPermission: Boolean,
        val userMessage: String?,
        val isLikedSongsSelected: Boolean
    )

    private data class MetaData(
        val filter: LibraryFilter,
        val sortOrder: TrackSortOrder,
        val isSyncing: Boolean,
        val hasPermission: Boolean,
        val userMessage: String?,
        val isLikedSongsSelected: Boolean
    )

    private val _syncMeta = combine(
        _isSyncing,
        _hasPermission,
        _userMessage,
        _isLikedSongsSelected
    ) { isSyncing, hasPermission, userMessage, isLikedSongsSelected ->
        SyncMeta(isSyncing, hasPermission, userMessage, isLikedSongsSelected)
    }

    private val _metaData = combine(
        _selectedFilter,
        _trackSortOrder,
        _syncMeta
    ) { filter, sortOrder, sync ->
        MetaData(filter, sortOrder, sync.isSyncing, sync.hasPermission, sync.userMessage, sync.isLikedSongsSelected)
    }

    private val _navData = combine(
        _selectedAlbum,
        _selectedArtist,
        _selectedPlaylist,
        _searchQuery
    ) { album, artist, playlist, query ->
        SelectedNav(album, artist, playlist, query)
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        _browseData,
        _detailData,
        _metaData,
        playbackFacade.playbackState,
        _navData
    ) { browse, detail, meta, playback, nav ->
        val sortedTracks = when (meta.sortOrder) {
            TrackSortOrder.TITLE_ASC -> browse.tracks.sortedBy { it.title.lowercase() }
            TrackSortOrder.RECENTLY_ADDED -> browse.tracks.sortedByDescending { it.dateAdded }
            TrackSortOrder.MOST_PLAYED -> {
                val mostPlayedIds = browse.mostPlayedTracks.map { it.id }
                val rankMap = mostPlayedIds.mapIndexed { index, id -> id to index }.toMap()
                val (played, unplayed) = browse.tracks.partition { rankMap.containsKey(it.id) }
                val sortedPlayed = played.sortedBy { rankMap[it.id] }
                val sortedUnplayed = unplayed.sortedBy { it.title.lowercase() }
                sortedPlayed + sortedUnplayed
            }
        }

        LibraryUiState(
            selectedFilter = meta.filter,
            trackSortOrder = meta.sortOrder,
            tracks = sortedTracks,
            albums = browse.albums,
            artists = browse.artists,
            favoriteTracks = browse.favTracks,
            recentTracks = browse.recentTracks,
            mostPlayedTracks = browse.mostPlayedTracks,
            recentlyAddedTracks = browse.recentlyAddedTracks,
            playlists = browse.playlists,
            activeTrackId = playback.currentTrack?.id,
            isPlaying = playback.isPlaying,
            albumTracks = detail.albumTracks,
            artistAlbums = detail.artistAlbums,
            artistTracks = detail.artistTracks,
            selectedPlaylist = nav.playlist,
            playlistTracks = detail.playlistTracks,
            isSyncing = meta.isSyncing,
            hasPermission = meta.hasPermission,
            userMessage = meta.userMessage,
            selectedAlbum = nav.album,
            selectedArtist = nav.artist,
            isLikedSongsSelected = meta.isLikedSongsSelected,
            searchQuery = nav.query
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState()
    )

    init {
        viewModelScope.launch {
            playbackFacade.oneShotEvents.collect { event ->
                when (event) {
                    is PlaybackEvent.TrackUnplayable -> {
                        _userMessage.value = "Unable to play: ${event.trackTitle}"
                    }
                }
            }
        }
    }

    fun selectFilter(filter: LibraryFilter) {
        _selectedFilter.value = filter
        _isLikedSongsSelected.value = false
        _selectedAlbum.value = null
        _selectedArtist.value = null
    }

    fun selectLikedSongs() {
        _isLikedSongsSelected.value = true
    }

    fun clearLikedSongs() {
        _isLikedSongsSelected.value = false
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
        _hasPermission.value = granted
        if (granted) {
            triggerSync()
        }
    }

    fun triggerSync() {
        if (!_hasPermission.value) return
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

    fun playTrack(track: Track, contextQueue: List<Track>? = null) {
        val queue = contextQueue ?: when (uiState.value.selectedFilter) {
            LibraryFilter.FAVORITES -> uiState.value.favoriteTracks
            else -> uiState.value.tracks
        }
        playbackFacade.playTrack(track, queue)
    }

    fun playAlbum(album: Album, shuffle: Boolean = false) {
        viewModelScope.launch {
            val tracks = trackRepository.getTracksForAlbum(album.albumId)
            if (tracks.isNotEmpty()) {
                val queue = if (shuffle) tracks.shuffled() else tracks
                playbackFacade.playQueue(queue, startIndex = 0)
            }
        }
    }

    fun playArtist(artist: Artist) {
        viewModelScope.launch {
            val tracks = trackRepository.getTracksForArtist(artist.name)
            if (tracks.isNotEmpty()) {
                playbackFacade.playQueue(tracks, startIndex = 0)
            }
        }
    }

    fun setTrackSortOrder(order: TrackSortOrder) {
        _trackSortOrder.value = order
    }

    fun playLikedSongs(shuffle: Boolean = false) {
        val favs = uiState.value.favoriteTracks
        if (favs.isNotEmpty()) {
            val queue = if (shuffle) favs.shuffled() else favs
            playbackFacade.playQueue(queue, startIndex = 0)
        }
    }

    fun playRecentlyPlayed(shuffle: Boolean = false) {
        val tracks = uiState.value.recentTracks
        if (tracks.isNotEmpty()) {
            val queue = if (shuffle) tracks.shuffled() else tracks
            playbackFacade.playQueue(queue, startIndex = 0)
        }
    }

    fun playRecentlyAdded(shuffle: Boolean = false) {
        val tracks = uiState.value.recentlyAddedTracks
        if (tracks.isNotEmpty()) {
            val queue = if (shuffle) tracks.shuffled() else tracks
            playbackFacade.playQueue(queue, startIndex = 0)
        }
    }

    fun playNext(track: Track) {
        playbackFacade.playNext(track)
        _userMessage.value = "Playing '${track.displayTitle}' next"
    }

    fun addToQueue(track: Track) {
        playbackFacade.enqueue(track)
        _userMessage.value = "Added '${track.displayTitle}' to queue"
    }

    fun toggleFavorite(trackId: Long) {
        viewModelScope.launch {
            trackRepository.toggleFavorite(trackId)
        }
    }

    // ==================== Playlist Actions (M5-D1 - M5-D5) ====================

    fun selectPlaylist(playlist: com.kaon.music.core.data.model.Playlist?) {
        _selectedPlaylist.value = playlist
    }

    fun createPlaylist(name: String, trackIdToAppend: Long? = null) {
        viewModelScope.launch {
            val trimmed = name.trim().ifBlank { "New Playlist" }
            val id = playlistRepository.createPlaylist(trimmed)
            if (trackIdToAppend != null) {
                playlistRepository.addTrackToPlaylist(id, trackIdToAppend)
            }
            _userMessage.value = "Created playlist '$trimmed'"
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        viewModelScope.launch {
            val trimmed = newName.trim()
            if (trimmed.isNotBlank()) {
                playlistRepository.renamePlaylist(playlistId, trimmed)
                if (_selectedPlaylist.value?.id == playlistId) {
                    _selectedPlaylist.value = _selectedPlaylist.value?.copy(name = trimmed)
                }
                _userMessage.value = "Renamed playlist to '$trimmed'"
            }
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            val name = _selectedPlaylist.value?.name
            playlistRepository.deletePlaylist(playlistId)
            if (_selectedPlaylist.value?.id == playlistId) {
                _selectedPlaylist.value = null
            }
            _userMessage.value = if (name != null) "Deleted playlist '$name'" else "Deleted playlist"
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long, trackTitle: String? = null, playlistName: String? = null) {
        viewModelScope.launch {
            val added = playlistRepository.addTrackToPlaylist(playlistId, trackId)
            val msg = if (added) {
                if (trackTitle != null && playlistName != null) {
                    "Added '$trackTitle' to $playlistName"
                } else {
                    "Added track to playlist"
                }
            } else {
                "Track already in playlist"
            }
            _userMessage.value = msg
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            playlistRepository.removeTrackFromPlaylist(playlistId, trackId)
            _userMessage.value = "Removed track from playlist"
        }
    }

    fun reorderPlaylistTracks(playlistId: Long, orderedTrackIds: List<Long>) {
        viewModelScope.launch {
            playlistRepository.reorderTracks(playlistId, orderedTrackIds)
        }
    }

    fun playPlaylist(playlist: com.kaon.music.core.data.model.Playlist, shuffle: Boolean = false, startIndex: Int = 0) {
        viewModelScope.launch {
            val tracks = playlistRepository.getTracksForPlaylist(playlist.id)
            if (tracks.isNotEmpty()) {
                val queue = if (shuffle) tracks.shuffled() else tracks
                val start = if (shuffle) 0 else startIndex.coerceIn(0, queue.size - 1)
                playbackFacade.playQueue(queue, startIndex = start)
            }
        }
    }

    fun dismissUserMessage() {
        _userMessage.value = null
    }
}
