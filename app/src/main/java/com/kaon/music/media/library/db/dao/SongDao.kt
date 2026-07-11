package com.kaon.music.media.library.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kaon.music.media.library.db.entity.CountProjection
import com.kaon.music.media.library.db.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs")
    suspend fun getAllSongsList(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE albumId = :albumId ORDER BY trackNumber ASC, title ASC")
    fun getSongsByAlbum(albumId: Long): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE artistId = :artistId ORDER BY albumId ASC, trackNumber ASC, title ASC")
    fun getSongsByArtist(artistId: Long): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getSongsByIds(ids: List<Long>): List<SongEntity>

    @Query("SELECT albumId AS id, COUNT(*) AS count FROM songs WHERE albumId IN (:albumIds) GROUP BY albumId")
    suspend fun getSongCountsByAlbumIds(albumIds: List<Long>): List<CountProjection>

    @Query("SELECT artistId AS id, COUNT(*) AS count FROM songs WHERE artistId IN (:artistIds) GROUP BY artistId")
    suspend fun getSongCountsByArtistIds(artistIds: List<Long>): List<CountProjection>

    @Query("SELECT id FROM songs")
    suspend fun getAllSongIds(): List<Long>

    @Upsert
    suspend fun upsertSongs(songs: List<SongEntity>)

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteSong(id: Long)

    @Query("DELETE FROM songs WHERE id NOT IN (:ids)")
    suspend fun deleteSongsNotIn(ids: List<Long>)
}
