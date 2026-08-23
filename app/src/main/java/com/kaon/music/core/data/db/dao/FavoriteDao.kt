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

    @Query("SELECT track_id FROM favorite_tracks")
    suspend fun getFavoriteTrackIds(): List<Long>

    /**
     * Observes all active (non-orphaned) favorite tracks joined with track metadata,
     * ordered reverse-chronologically by the time they were favorited (M4-D1, M4-D2).
     */
    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN favorite_tracks f ON t.track_id = f.track_id
        WHERE t.is_missing = 0
        ORDER BY f.added_at DESC
    """)
    fun observeFavoriteTrackEntities(): Flow<List<com.kaon.music.core.data.db.entity.TrackEntity>>

    /**
     * Synchronous query for active favorite track entities.
     */
    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN favorite_tracks f ON t.track_id = f.track_id
        WHERE t.is_missing = 0
        ORDER BY f.added_at DESC
    """)
    suspend fun getFavoriteTrackEntities(): List<com.kaon.music.core.data.db.entity.TrackEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_tracks WHERE track_id = :trackId)")
    fun observeIsFavorite(trackId: Long): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_tracks WHERE track_id = :trackId)")
    suspend fun isFavorite(trackId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteTrackEntity)

    @Query("DELETE FROM favorite_tracks WHERE track_id = :trackId")
    suspend fun removeFavorite(trackId: Long)
}
