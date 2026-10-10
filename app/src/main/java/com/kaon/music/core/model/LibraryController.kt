package com.kaon.music.core.model

import androidx.paging.PagingData
import com.kaon.music.core.data.model.Album
import com.kaon.music.core.data.model.Artist
import com.kaon.music.core.data.model.LyricsResult
import com.kaon.music.core.data.model.Playlist
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.policy.LibraryPolicy
import kotlinx.coroutines.flow.Flow

typealias LibrarySort = com.kaon.music.core.data.repository.LibrarySort
typealias PlaylistStats = com.kaon.music.core.data.repository.PlaylistStats
typealias SyncResult = com.kaon.music.core.data.sync.SyncResult

/**
 * Capability controller exposing media library access, playlists, and metadata queries to UI layers.
 *
 * Keeps UI completely insulated from Room entities, DAOs, or raw repositories.
 */
interface LibraryController {
    fun observeTrackCount(query: String = ""): Flow<Int>
    fun observeFavoriteCount(query: String = ""): Flow<Int>
    fun observeSyncState(): Flow<Boolean>

    fun pagedTracks(query: String = "", sort: LibrarySort = LibrarySort.TITLE_ASC): Flow<PagingData<Track>>
    fun pagedFavoriteTracks(query: String = ""): Flow<PagingData<Track>>
    fun pagedAlbums(query: String = ""): Flow<PagingData<Album>>
    fun pagedArtists(query: String = ""): Flow<PagingData<Artist>>

    fun observeRecentlyPlayedTracks(limit: Int = 20): Flow<List<Track>>
    fun observeRecentlyAddedTracks(limit: Int = 20): Flow<List<Track>>
    fun observeMostPlayedTracks(limit: Int = 20): Flow<List<Track>>
    fun pagedFavoriteSample(limit: Int = 20): Flow<List<Track>>

    fun observeRecentAlbums(limit: Int = 10): Flow<List<Album>>
    fun observeRecentArtists(limit: Int = 10): Flow<List<Artist>>

    fun observeTracksForAlbum(albumId: Long): Flow<List<Track>>
    fun observeAlbumsForArtist(artistName: String): Flow<List<Album>>
    fun observeTracksForArtist(artistName: String): Flow<List<Track>>

    suspend fun getAlbumById(albumId: Long): Album?
    suspend fun getTracksForAlbum(albumId: Long): List<Track>
    suspend fun getTracksForArtist(artistName: String): List<Track>
    suspend fun getAlbumsForArtist(artistId: Long): List<Album>

    fun searchTracks(query: String, limit: Int = 20): Flow<List<Track>>
    fun searchAlbums(query: String, limit: Int = 20): Flow<List<Album>>
    fun searchArtists(query: String, limit: Int = 20): Flow<List<Artist>>

    fun observePlaylists(): Flow<List<Playlist>>
    fun observeAllPlaylists(): Flow<List<Playlist>> = observePlaylists()
    fun pagedTracksForPlaylist(playlistId: Long): Flow<PagingData<Track>>
    fun observePlaylistStats(playlistId: Long): Flow<PlaylistStats>

    suspend fun getPlaylist(playlistId: Long): Playlist?
    suspend fun getPlaylistWithTracks(playlistId: Long): Pair<Playlist, List<Track>>?
    suspend fun getPlaylistStats(playlistId: Long): PlaylistStats
    suspend fun getTracksForPlaylist(playlistId: Long): List<Track>
    suspend fun getOrderedTrackIds(playlistId: Long): List<Long>

    suspend fun toggleFavorite(track: Track)
    suspend fun syncLibrary(minDurationMs: Long = 5000L): SyncResult
    suspend fun clearListeningHistory()

    suspend fun createPlaylist(name: String): Long
    suspend fun addToPlaylist(playlistId: Long, track: Track): Boolean
    suspend fun addTrack(playlistId: Long, track: Track): Boolean = addToPlaylist(playlistId, track)
    suspend fun removeFromPlaylist(playlistId: Long, trackId: Long)
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) = removeFromPlaylist(playlistId, trackId)
    suspend fun reorderTracks(playlistId: Long, trackIds: List<Long>)

    suspend fun deletePlaylist(playlistId: Long)
    suspend fun renamePlaylist(playlistId: Long, newName: String)

    suspend fun getQueueForContext(
        query: String = "",
        sort: LibrarySort = LibrarySort.TITLE_ASC,
        favoritesOnly: Boolean = false,
        limit: Int = 1000
    ): List<Track>
    suspend fun fetchLyricsAndMetadata(track: Track): LyricsResult?
    suspend fun saveQueueAsPlaylist(title: String, tracks: List<Track>): Long
}
