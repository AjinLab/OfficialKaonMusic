package com.kaon.music.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kaon.music.core.data.db.entity.PlayEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayEventDao {

    @Insert
    suspend fun insertEvent(event: PlayEventEntity): Long

    /**
     * De-duplicated recently played tracks query joining active tracks (M4-D2, M4-D3).
     * Returns active tracks ordered by their most recent PLAY event timestamp.
     */
    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN (
            SELECT track_id, MAX(played_at) as latest_play
            FROM play_events
            WHERE event_type = 'PLAY'
            GROUP BY track_id
        ) p ON t.track_id = p.track_id
        WHERE t.is_missing = 0
        ORDER BY p.latest_play DESC
        LIMIT :limit
    """)
    fun observeRecentlyPlayedTrackEntities(limit: Int = 100): Flow<List<com.kaon.music.core.data.db.entity.TrackEntity>>

    /**
     * Synchronous query for recently played active track entities.
     */
    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN (
            SELECT track_id, MAX(played_at) as latest_play
            FROM play_events
            WHERE event_type = 'PLAY'
            GROUP BY track_id
        ) p ON t.track_id = p.track_id
        WHERE t.is_missing = 0
        ORDER BY p.latest_play DESC
        LIMIT :limit
    """)
    suspend fun getRecentlyPlayedTrackEntities(limit: Int = 100): List<com.kaon.music.core.data.db.entity.TrackEntity>

    /**
     * Most played active tracks query (M4-D2, M4-D4).
     * Returns active tracks ordered by total PLAY event count.
     */
    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN (
            SELECT track_id, COUNT(*) as play_count
            FROM play_events
            WHERE event_type = 'PLAY'
            GROUP BY track_id
        ) p ON t.track_id = p.track_id
        WHERE t.is_missing = 0
        ORDER BY p.play_count DESC
        LIMIT :limit
    """)
    fun observeMostPlayedTrackEntities(limit: Int = 100): Flow<List<com.kaon.music.core.data.db.entity.TrackEntity>>

    /**
     * Synchronous query for most played active track entities.
     */
    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN (
            SELECT track_id, COUNT(*) as play_count
            FROM play_events
            WHERE event_type = 'PLAY'
            GROUP BY track_id
        ) p ON t.track_id = p.track_id
        WHERE t.is_missing = 0
        ORDER BY p.play_count DESC
        LIMIT :limit
    """)
    suspend fun getMostPlayedTrackEntities(limit: Int = 100): List<com.kaon.music.core.data.db.entity.TrackEntity>

    data class TrackPlayCount(
        val track_id: Long,
        val play_count: Long
    )
}
