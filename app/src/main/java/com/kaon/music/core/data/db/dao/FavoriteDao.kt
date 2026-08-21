package com.kaon.music.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kaon.music.core.data.db.entity.FavoriteTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Query("SELECT track_id FROM favorite_tracks")
    fun observeFavoriteTrackIds(): Flow<List<Long>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_tracks WHERE track_id = :trackId)")
    fun observeIsFavorite(trackId: Long): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_tracks WHERE track_id = :trackId)")
    suspend fun isFavorite(trackId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteTrackEntity)

    @Query("DELETE FROM favorite_tracks WHERE track_id = :trackId")
    suspend fun removeFavorite(trackId: Long)
}
