package com.kaon.music.media.library.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kaon.music.media.library.db.entity.AlbumEntity
import com.kaon.music.media.library.db.entity.CountProjection
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY title ASC")
    fun getAllAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE artistId = :artistId ORDER BY title ASC")
    fun getAlbumsByArtist(artistId: Long): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :id")
    fun getAlbum(id: Long): Flow<AlbumEntity?>

    @Query("SELECT * FROM albums WHERE id IN (:ids)")
    suspend fun getAlbumsByIds(ids: List<Long>): List<AlbumEntity>

    @Query("SELECT artistId AS id, COUNT(*) AS count FROM albums WHERE artistId IN (:artistIds) GROUP BY artistId")
    suspend fun getAlbumCountsByArtistIds(artistIds: List<Long>): List<CountProjection>

    @Upsert
    suspend fun upsertAlbums(albums: List<AlbumEntity>)

    @Query("DELETE FROM albums WHERE id NOT IN (:ids)")
    suspend fun deleteAlbumsNotIn(ids: List<Long>)

    @Query("DELETE FROM albums WHERE NOT EXISTS (SELECT 1 FROM songs WHERE songs.albumId = albums.id)")
    suspend fun deleteOrphanAlbums()
}
