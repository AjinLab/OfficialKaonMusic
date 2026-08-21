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
     * Recently played tracks query derived from raw play events.
     */
    @Query("""
        SELECT track_id FROM play_events 
        WHERE event_type = 'PLAY' 
        ORDER BY played_at DESC 
        LIMIT :limit
    """)
    fun observeRecentlyPlayedTrackIds(limit: Int = 50): Flow<List<Long>>

    /**
     * Most played track query derived from raw play events.
     */
    @Query("""
        SELECT track_id, COUNT(*) as play_count 
        FROM play_events 
        WHERE event_type = 'PLAY' 
        GROUP BY track_id 
        ORDER BY play_count DESC 
        LIMIT :limit
    """)
    suspend fun getMostPlayedTrackIds(limit: Int = 50): List<TrackPlayCount>

    data class TrackPlayCount(
        val track_id: Long,
        val play_count: Long
    )
}
