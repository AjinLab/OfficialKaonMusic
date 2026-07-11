package com.kaon.music.media.library.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kaon.music.media.library.db.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT songId FROM favorites")
    fun getFavoriteIdsFlow(): Flow<List<Long>>

    @Query("SELECT songId FROM favorites WHERE songId IN (:songIds)")
    suspend fun getFavoriteIds(songIds: List<Long>): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    suspend fun isFavorite(songId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun delete(songId: Long)
}
