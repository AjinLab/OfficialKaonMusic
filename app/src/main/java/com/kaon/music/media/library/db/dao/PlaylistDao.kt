package com.kaon.music.media.library.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kaon.music.media.library.db.entity.PlaylistEntity
import com.kaon.music.media.library.db.entity.PlaylistSongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun getPlaylist(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    fun getPlaylistSongs(playlistId: Long): Flow<List<PlaylistSongEntity>>

    @Upsert
    suspend fun upsertPlaylist(playlist: PlaylistEntity): Long

    @Upsert
    suspend fun upsertPlaylistSongs(songs: List<PlaylistSongEntity>)

    @Query("SELECT * FROM playlist_songs")
    fun getAllPlaylistSongs(): Flow<List<PlaylistSongEntity>>

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun deletePlaylistSong(playlistId: Long, songId: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)
}
