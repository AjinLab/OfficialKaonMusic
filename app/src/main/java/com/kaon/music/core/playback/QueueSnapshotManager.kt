package com.kaon.music.core.playback

import com.kaon.music.core.data.db.dao.QueueSnapshotDao
import com.kaon.music.core.data.db.entity.QueueSnapshotEntity
import com.kaon.music.core.data.model.QueueSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Manages debounced writes and restoration of the queue snapshot in Room.
 *
 * Implements ARCHITECTURE_ATTRIBUTED.md §4:
 * - Writes are debounced on timeline/position changes.
 * - Flushed immediately on pause/stop.
 * - Restored only on cold start when player is empty.
 */
class QueueSnapshotManager(
    private val queueSnapshotDao: QueueSnapshotDao,
    private val serviceScope: CoroutineScope
) {
    private var debounceJob: Job? = null
    private var pendingSnapshot: QueueSnapshot? = null

    fun scheduleSnapshotSave(snapshot: QueueSnapshot, immediate: Boolean = false) {
        pendingSnapshot = snapshot
        debounceJob?.cancel()

        if (immediate) {
            persistSnapshotNow(snapshot)
        } else {
            debounceJob = serviceScope.launch(Dispatchers.IO) {
                delay(2000) // 2-second debounce for continuous playback updates
                pendingSnapshot?.let { persistSnapshotNow(it) }
            }
        }
    }

    fun flush() {
        debounceJob?.cancel()
        pendingSnapshot?.let { snapshot ->
            persistSnapshotNow(snapshot)
        }
    }

    suspend fun loadSnapshot(): QueueSnapshot? {
        val entity = queueSnapshotDao.getQueueSnapshot() ?: return null
        val trackIds = parseTrackIds(entity.serializedTrackIds)
        if (trackIds.isEmpty()) return null

        return QueueSnapshot(
            trackIds = trackIds,
            currentIndex = entity.currentIndex.coerceIn(0, trackIds.size - 1),
            currentPositionMs = entity.currentPositionMs,
            isShuffleEnabled = entity.isShuffleEnabled,
            repeatMode = entity.repeatMode,
            timestamp = entity.savedAt
        )
    }

    private fun persistSnapshotNow(snapshot: QueueSnapshot) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val serialized = snapshot.trackIds.joinToString(",")
                val entity = QueueSnapshotEntity(
                    id = 1,
                    serializedTrackIds = serialized,
                    currentIndex = snapshot.currentIndex,
                    currentPositionMs = snapshot.currentPositionMs,
                    isShuffleEnabled = snapshot.isShuffleEnabled,
                    repeatMode = snapshot.repeatMode,
                    savedAt = snapshot.timestamp
                )
                queueSnapshotDao.saveQueueSnapshot(entity)
                Timber.tag("QueueSnapshot").d("Queue snapshot persisted (${snapshot.trackIds.size} tracks)")
            } catch (e: Exception) {
                Timber.tag("QueueSnapshot").e(e, "Failed to persist queue snapshot")
            }
        }
    }

    private fun parseTrackIds(serialized: String): List<Long> {
        if (serialized.isBlank()) return emptyList()
        return serialized.split(",").mapNotNull { it.trim().toLongOrNull() }
    }
}
