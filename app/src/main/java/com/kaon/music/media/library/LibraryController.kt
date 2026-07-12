package com.kaon.music.media.library

import com.kaon.music.media.model.Song
import com.kaon.music.media.model.Artist
import com.kaon.music.media.model.Album
import com.kaon.music.media.model.Genre
import com.kaon.music.media.model.Folder
import com.kaon.music.media.model.Playlist
import com.kaon.music.media.model.AlbumDetail
import com.kaon.music.media.model.ArtistDetail
import com.kaon.music.media.search.SearchResult
import kotlinx.coroutines.flow.Flow

interface LibraryController {
    val songs: Flow<List<Song>>
    val artists: Flow<List<Artist>>
    val albums: Flow<List<Album>>
    val folders: Flow<List<Folder>>

    suspend fun refresh()
    fun search(query: String): Flow<List<SearchResult>>
    suspend fun searchAll(query: String): com.kaon.music.media.search.SearchResults
    suspend fun getSongsByIds(ids: List<Long>): List<Song>
    suspend fun getAllSongIds(): List<Long>

    fun album(id: Long): Flow<Album?>
    fun artist(id: Long): Flow<Artist?>
    
    fun albumSongs(albumId: Long): Flow<List<Song>>
    fun artistAlbums(artistId: Long): Flow<List<Album>>
    fun artistSongs(artistId: Long): Flow<List<Song>>

    fun albumDetail(id: Long): Flow<AlbumDetail>
    fun artistDetail(id: Long): Flow<ArtistDetail>

    suspend fun genres(): List<Genre>
    suspend fun folders(): List<Folder>
    
    fun playlistsFlow(): Flow<List<Playlist>>
    suspend fun createPlaylist(name: String): Long
    suspend fun deletePlaylist(id: Long)
    suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>)
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)
    fun playlistSongs(playlistId: Long): Flow<List<Song>>
    fun observePlaylist(id: Long): Flow<Playlist?>
    
    fun favorites(): Flow<List<Song>>
    suspend fun toggleFavorite(songId: Long)
    suspend fun deleteSong(songId: Long): Boolean

    suspend fun applyLibrarySnapshot(snapshot: com.kaon.music.media.library.db.LibrarySnapshot)
}
