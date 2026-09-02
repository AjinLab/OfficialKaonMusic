package com.kaon.music.core.data.repository

import com.kaon.music.core.data.db.dao.PlayEventDao
import com.kaon.music.core.data.db.dao.TrackDao
import com.kaon.music.core.data.db.entity.PlayEventEntity
import com.kaon.music.core.data.model.PlayEvent
import timber.log.Timber

class HistoryRepository(
    private val playEventDao: PlayEventDao,
    private val trackDao: TrackDao? = null
) {

    suspend fun recordPlayEvent(trackId: Long, eventType: PlayEvent.EventType, playedMs: Long) {
        // Only insert history events for tracks that exist in the local database to satisfy the foreign key
        if (trackDao != null && trackDao.getTrackById(trackId) == null) {
            return
        }

        try {
            playEventDao.insertEvent(
                PlayEventEntity(
                    trackId = trackId,
                    eventType = eventType.name,
                    playedAt = System.currentTimeMillis(),
                    playedMs = playedMs
                )
            )
        } catch (e: Exception) {
            Timber.tag("HistoryRepository").w(e, "Failed to record play event for track $trackId")
        }
    }

    suspend fun clearAllHistory(): Int {
        return playEventDao.clearAllEvents()
    }
}
