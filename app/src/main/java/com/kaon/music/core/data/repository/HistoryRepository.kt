package com.kaon.music.core.data.repository

import com.kaon.music.core.data.db.dao.PlayEventDao
import com.kaon.music.core.data.db.entity.PlayEventEntity
import com.kaon.music.core.data.model.PlayEvent
import kotlinx.coroutines.flow.Flow

class HistoryRepository(
    private val playEventDao: PlayEventDao
) {

    suspend fun recordPlayEvent(trackId: Long, eventType: PlayEvent.EventType, playedMs: Long) {
        playEventDao.insertEvent(
            PlayEventEntity(
                trackId = trackId,
                eventType = eventType.name,
                playedAt = System.currentTimeMillis(),
                playedMs = playedMs
            )
        )
    }

    fun observeRecentlyPlayedTrackIds(limit: Int = 50): Flow<List<Long>> {
        return playEventDao.observeRecentlyPlayedTrackIds(limit)
    }
}
