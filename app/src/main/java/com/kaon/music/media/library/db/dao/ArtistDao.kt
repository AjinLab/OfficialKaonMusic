package com.kaon.music.media.library.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kaon.music.media.library.db.entity.ArtistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {
    @Query("SELECT * FROM artists ORDER BY name ASC")
    fun getAllArtists(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE id = :id")
    fun getArtist(id: Long): Flow<ArtistEntity?>

    @Upsert
    suspend fun upsertArtists(artists: List<ArtistEntity>)

    @Query("DELETE FROM artists WHERE id NOT IN (:ids)")
    suspend fun deleteArtistsNotIn(ids: List<Long>)
    
    @Query("DELETE FROM artists WHERE NOT EXISTS (SELECT 1 FROM albums WHERE albums.artistId = artists.id)")
    suspend fun deleteOrphanArtists()
}
