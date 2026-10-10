package com.kaon.music.core.data.repository

import androidx.paging.PagingData
import com.kaon.music.core.data.model.Album
import com.kaon.music.core.data.model.Artist
import com.kaon.music.core.data.model.LyricsResult
import com.kaon.music.core.data.model.Playlist
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.data.sync.SyncEngine
import com.kaon.music.core.model.LibraryController
import com.kaon.music.core.model.LibrarySort
import com.kaon.music.core.model.PlaylistStats
import com.kaon.music.core.policy.LibraryPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.firstOrNull

/**
 * Standard implementation of [LibraryController] coordinating repositories behind the capability interface.
 */
class DefaultLibraryController(
    private val trackRepository: TrackRepository,
    private val playlistRepository: PlaylistRepository? = null,
    private val metadataRepository: MetadataRepository? = null,
    private val syncEngine: SyncEngine? = null,
    private val historyRepository: HistoryRepository? = null
) : LibraryController {

    private val _hasPermission = kotlinx.coroutines.flow.MutableStateFlow(true)
    override val hasPermission: kotlinx.coroutines.flow.StateFlow<Boolean> = _hasPermission

    override fun setPermissionState(granted: Boolean) {
        _hasPermission.value = granted
    }

    override fun observeTrackCount(query: String): Flow<Int> =
        trackRepository.observeTrackCount(query)

    override fun observeFavoriteCount(query: String): Flow<Int> =
        trackRepository.observeFavoriteCount(query)

    override fun observeSyncState(): Flow<Boolean> =
        flowOf(false)

    override fun pagedTracks(query: String, sort: LibrarySort): Flow<PagingData<Track>> =
        trackRepository.pagedTracks(query = query, sort = sort)

    override fun pagedFavoriteTracks(query: String): Flow<PagingData<Track>> =
        trackRepository.pagedFavoriteTracks(query = query)

    override fun pagedAlbums(query: String): Flow<PagingData<Album>> =
        trackRepository.pagedAlbums(query = query)

    override fun pagedArtists(query: String): Flow<PagingData<Artist>> =
        trackRepository.pagedArtists(query = query)

    override fun observeRecentlyPlayedTracks(limit: Int): Flow<List<Track>> =
        trackRepository.observeRecentlyPlayedTracks(limit)

    override fun observeRecentlyAddedTracks(limit: Int): Flow<List<Track>> =
        trackRepository.observeRecentlyAddedTracks(limit)

    override fun observeMostPlayedTracks(limit: Int): Flow<List<Track>> =
        trackRepository.observeMostPlayedTracks(limit)

    override fun pagedFavoriteSample(limit: Int): Flow<List<Track>> =
        trackRepository.pagedFavoriteSample(limit)

    override fun observeRecentAlbums(limit: Int): Flow<List<Album>> =
        trackRepository.observeRecentAlbums(limit)

    override fun observeRecentArtists(limit: Int): Flow<List<Artist>> =
        trackRepository.observeRecentArtists(limit)

    override fun observeTracksForAlbum(albumId: Long): Flow<List<Track>> =
        trackRepository.observeTracksForAlbum(albumId)

    override fun observeAlbumsForArtist(artistName: String): Flow<List<Album>> =
        trackRepository.observeAlbumsForArtist(artistName)

    override fun observeTracksForArtist(artistName: String): Flow<List<Track>> =
        trackRepository.observeTracksForArtist(artistName)

    override suspend fun getAlbumById(albumId: Long): Album? =
        trackRepository.getAlbumById(albumId)

    override suspend fun getTracksForAlbum(albumId: Long): List<Track> =
        trackRepository.getTracksForAlbum(albumId)

    override suspend fun getTracksForArtist(artistName: String): List<Track> =
        trackRepository.getTracksForArtist(artistName)

    override suspend fun getAlbumsForArtist(artistId: Long): List<Album> =
        emptyList()

    override fun searchTracks(query: String, limit: Int): Flow<List<Track>> =
        trackRepository.searchTracks(query, limit)

    override fun searchAlbums(query: String, limit: Int): Flow<List<Album>> =
        trackRepository.searchAlbums(query, limit)

    override fun searchArtists(query: String, limit: Int): Flow<List<Artist>> =
        trackRepository.searchArtists(query, limit)

    override fun observePlaylists(): Flow<List<Playlist>> =
        playlistRepository?.observeAllPlaylists() ?: flowOf(emptyList())

    override fun observeAllPlaylists(): Flow<List<Playlist>> =
        playlistRepository?.observeAllPlaylists() ?: flowOf(emptyList())

    override fun pagedTracksForPlaylist(playlistId: Long): Flow<PagingData<Track>> =
        playlistRepository?.pagedTracksForPlaylist(playlistId) ?: flowOf(PagingData.empty())

    override fun observePlaylistStats(playlistId: Long): Flow<PlaylistStats> =
        playlistRepository?.observePlaylistStats(playlistId) ?: flowOf(PlaylistStats())

    override suspend fun getPlaylist(playlistId: Long): Playlist? =
        playlistRepository?.getPlaylist(playlistId)

    override suspend fun getPlaylistWithTracks(playlistId: Long): Pair<Playlist, List<Track>>? {
        val repo = playlistRepository ?: return null
        val playlist = repo.getPlaylist(playlistId) ?: return null
        val tracks = repo.getTracksForPlaylist(playlistId)
        return playlist to tracks
    }

    override suspend fun getPlaylistStats(playlistId: Long): PlaylistStats =
        playlistRepository?.observePlaylistStats(playlistId)?.firstOrNull() ?: PlaylistStats()

    override suspend fun getTracksForPlaylist(playlistId: Long): List<Track> =
        playlistRepository?.getTracksForPlaylist(playlistId) ?: emptyList()

    override suspend fun getOrderedTrackIds(playlistId: Long): List<Long> =
        playlistRepository?.getOrderedTrackIds(playlistId) ?: emptyList()

    override suspend fun toggleFavorite(track: Track) {
        trackRepository.toggleFavorite(track)
    }

    override suspend fun syncLibrary(minDurationMs: Long): com.kaon.music.core.data.sync.SyncResult =
        trackRepository.syncLibrary(minDurationMs)

    override suspend fun clearListeningHistory() {
        historyRepository?.clearAllHistory()
    }

    override suspend fun createPlaylist(name: String): Long =
        playlistRepository?.createPlaylist(name) ?: 0L

    override suspend fun addToPlaylist(playlistId: Long, track: Track): Boolean =
        playlistRepository?.addTrack(playlistId, track) ?: false

    override suspend fun addTrack(playlistId: Long, track: Track): Boolean =
        playlistRepository?.addTrack(playlistId, track) ?: false

    override suspend fun removeFromPlaylist(playlistId: Long, trackId: Long) {
        playlistRepository?.removeTrackFromPlaylist(playlistId, trackId)
    }

    override suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) =
        playlistRepository?.removeTrackFromPlaylist(playlistId, trackId) ?: Unit

    override suspend fun reorderTracks(playlistId: Long, trackIds: List<Long>) {
        playlistRepository?.reorderTracks(playlistId, trackIds)
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        playlistRepository?.deletePlaylist(playlistId)
    }

    override suspend fun renamePlaylist(playlistId: Long, newName: String) {
        playlistRepository?.renamePlaylist(playlistId, newName)
    }

    override suspend fun getQueueForContext(
        query: String,
        sort: LibrarySort,
        favoritesOnly: Boolean,
        limit: Int
    ): List<Track> =
        trackRepository.getQueueForContext(query = query, sort = sort, favoritesOnly = favoritesOnly, limit = limit)

    override suspend fun fetchLyricsAndMetadata(track: Track): LyricsResult? =
        metadataRepository?.getLyrics(track)

    override suspend fun saveQueueAsPlaylist(title: String, tracks: List<Track>): Long {
        val repo = playlistRepository ?: return 0L
        val playlistId = repo.createPlaylist(title)
        for (track in tracks) {
            repo.addTrack(playlistId, track)
        }
        return playlistId
    }
}
