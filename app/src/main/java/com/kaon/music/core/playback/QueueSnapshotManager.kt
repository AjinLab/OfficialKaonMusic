package com.kaon.music.core.playback

import com.kaon.music.core.data.db.dao.QueueSnapshotDao
import com.kaon.music.core.data.db.entity.QueueSnapshotEntity
import com.kaon.music.core.data.model.ProviderId
import com.kaon.music.core.data.model.QueueEntry
import com.kaon.music.core.data.model.QueueSnapshot
import com.kaon.music.core.data.model.TrackId
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
 *
 * Entries are canonical [TrackId]s. Provider identity is no longer encoded in the serialized string:
 * a streamed track is a `tracks` row before it can be queued, so the row already knows its provider
 * and locator. [legacyIdentityResolver] exists only to read snapshots written by builds that stored
 * `hashId:videoId` pairs, where the hash half was never a real row.
 */
class QueueSnapshotManager(
    private val queueSnapshotDao: QueueSnapshotDao,
    private val serviceScope: CoroutineScope,
    private val legacyIdentityResolver: suspend (ProviderId, String) -> TrackId? = { _, _ -> null }
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
        val tokens = parseTokens(entity.serializedTrackIds)
        if (tokens.isEmpty()) return null

        // A token that cannot be resolved shifts every later position left, so the saved index has to
        // move with it or a restore would resume on the wrong track.
        var droppedBeforeCurrent = 0
        val entries = ArrayList<QueueEntry>(tokens.size)
        tokens.forEachIndexed { index, token ->
            val trackId = token.resolve()
            if (trackId == null) {
                if (index < entity.currentIndex) droppedBeforeCurrent++
            } else {
                entries += QueueEntry(trackId)
            }
        }
        if (entries.isEmpty()) return null

        return QueueSnapshot(
            entries = entries,
            currentIndex = (entity.currentIndex - droppedBeforeCurrent).coerceIn(0, entries.size - 1),
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
     * Serializes entries as comma-separated canonical [TrackId]s.
     *
     * Nothing provider-specific is written any more: the referenced `tracks` row owns the locator, so a
     * snapshot is pure identity and a mixed local + YouTube queue needs no per-provider encoding.
     */
    private fun serializeEntries(entries: List<QueueEntry>): String =
        entries.joinToString(",") { it.trackId.value.toString() }

    /**
     * Reads both the canonical form (`trackId`) and the legacy `hashId:videoId` form.
     *
     * Legacy tokens keep their provider identity instead of an id, because the hash half was never a
     * real row; [resolve] turns them into canonical rows.
     */
    private fun parseTokens(serialized: String): List<SnapshotToken> {
        if (serialized.isBlank()) return emptyList()
        return serialized.split(",").mapNotNull { raw ->
            val token = raw.trim()
            if (token.isEmpty()) return@mapNotNull null
            val separator = token.indexOf(LEGACY_VIDEO_ID_SEPARATOR)
            if (separator < 0) {
                token.toLongOrNull()?.let { SnapshotToken(trackId = TrackId(it)) }
            } else {
                val legacyVideoId = token.substring(separator + 1).trim()
                    .takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                SnapshotToken(
                    trackId = token.substring(0, separator).trim().toLongOrNull()?.let(::TrackId),
                    legacyVideoId = legacyVideoId
                )
            }
        }
    }

    /** Resolves one serialized position to a row id, or `null` when the position cannot be restored. */
    private suspend fun SnapshotToken.resolve(): TrackId? =
        if (legacyVideoId != null) {
            legacyIdentityResolver(ProviderId.YOUTUBE, legacyVideoId)
        } else {
            trackId?.takeIf { it.isSaved }
        }

    private data class SnapshotToken(
        val trackId: TrackId?,
        val legacyVideoId: String? = null
    )

    private companion object {
        // ':' cannot appear in a YouTube video id (they are [A-Za-z0-9_-]) or in a Long.
        const val LEGACY_VIDEO_ID_SEPARATOR = ':'
    }
}
