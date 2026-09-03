package com.kaon.music.core.playback

import com.kaon.music.core.data.db.dao.QueueSnapshotDao
import com.kaon.music.core.data.db.entity.QueueSnapshotEntity
import com.kaon.music.core.data.model.QueueEntry
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
        val entries = parseEntries(entity.serializedTrackIds)
        if (entries.isEmpty()) return null

        return QueueSnapshot(
            entries = entries,
            currentIndex = entity.currentIndex.coerceIn(0, entries.size - 1),
            currentPositionMs = entity.currentPositionMs,
            isShuffleEnabled = entity.isShuffleEnabled,
            repeatMode = entity.repeatMode,
            timestamp = entity.savedAt
        )
    }

    suspend fun clearSnapshot() {
        queueSnapshotDao.clearQueueSnapshot()
        pendingSnapshot = null
        debounceJob?.cancel()
    }

    suspend fun saveSnapshotDirectly(snapshot: QueueSnapshot) {
        val serialized = serializeEntries(snapshot.entries)
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
        Timber.tag("queue").d("Queue snapshot persisted (${snapshot.entries.size} entries)")
    }

    private fun persistSnapshotNow(snapshot: QueueSnapshot) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                saveSnapshotDirectly(snapshot)
            } catch (e: Exception) {
                Timber.tag("queue").e(e, "Failed to persist queue snapshot")
            }
        }
    }

    /**
     * Serializes entries as `trackId[:videoId]`, comma-separated.
     *
     * A bare `trackId` is still valid, so snapshots written by an older build load unchanged (their
     * entries simply carry no provider identity and restore as local-only, exactly as before).
     */
    private fun serializeEntries(entries: List<QueueEntry>): String =
        entries.joinToString(",") { entry ->
            if (entry.youtubeVideoId.isNullOrBlank()) {
                entry.trackId.toString()
            } else {
                "${entry.trackId}$VIDEO_ID_SEPARATOR${entry.youtubeVideoId}"
            }
        }

    private fun parseEntries(serialized: String): List<QueueEntry> {
        if (serialized.isBlank()) return emptyList()
        return serialized.split(",").mapNotNull { raw ->
            val token = raw.trim()
            if (token.isEmpty()) return@mapNotNull null
            val separator = token.indexOf(VIDEO_ID_SEPARATOR)
            if (separator < 0) {
                token.toLongOrNull()?.let { QueueEntry(trackId = it) }
            } else {
                val trackId = token.substring(0, separator).toLongOrNull() ?: return@mapNotNull null
                val videoId = token.substring(separator + 1).takeIf { it.isNotBlank() }
                QueueEntry(trackId = trackId, youtubeVideoId = videoId)
            }
        }
    }

    private companion object {
        // ':' cannot appear in a YouTube video id (they are [A-Za-z0-9_-]) or in a Long.
        const val VIDEO_ID_SEPARATOR = ':'
    }
}
