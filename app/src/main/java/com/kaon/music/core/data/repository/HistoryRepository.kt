package com.kaon.music.core.data.repository

import com.kaon.music.core.data.db.dao.PlayEventDao
import com.kaon.music.core.data.db.dao.TrackDao
import com.kaon.music.core.data.db.entity.PlayEventEntity
import com.kaon.music.core.data.model.PlayEvent
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.data.model.TrackId
import timber.log.Timber

class HistoryRepository(
    private val playEventDao: PlayEventDao,
    private val trackDao: TrackDao? = null,
    /**
     * Makes provider media durable before its first play event.
     *
     * Optional so the playback service can construct a history repository without a full graph, but
     * when present it is what lets a YouTube track generate history (spec §6, §9).
     */
    private val trackRepository: TrackRepository? = null
) {

    /**
     * Records an event for a track that may not be persisted yet.
     *
     * A YouTube track reaches playback as [TrackId.UNSAVED], and the id-only path below would have
     * dropped its history on the floor. Persisting first is what makes "played 30 seconds of a stream"
     * count the same as playing a local file (spec §30.9).
     */
    suspend fun recordPlayEvent(track: Track, eventType: PlayEvent.EventType, playedMs: Long) {
        val persisted = trackRepository?.ensurePersisted(track) ?: track
        if (!persisted.id.isSaved) {
            Timber.tag("HistoryRepository").w(
                "Skipped play event: track has no durable identity (provider=${track.provider})"
            )
            return
        }
        recordPlayEvent(persisted.id.value, eventType, playedMs)
    }

    /**
     * Records an event for an already-persisted track id.
     *
     * The existence check is retained — `play_events` no longer has a foreign key on `track_id`
     * (ARCHITECTURE.md §6), so nothing else would stop history accumulating against an id that never
     * existed. It is no longer a *remote* early return: a streamed track has a row by this point.
     */
    suspend fun recordPlayEvent(trackId: Long, eventType: PlayEvent.EventType, playedMs: Long) {
        if (trackDao != null && trackDao.getTrackById(trackId) == null) {
            Timber.tag("HistoryRepository").w("Skipped play event: no track row for id $trackId")
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
