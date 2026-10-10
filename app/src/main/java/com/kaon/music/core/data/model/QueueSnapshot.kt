package com.kaon.music.core.data.model

/**
 * Persisted playback queue.
 *
 * Every entry is now a canonical [TrackId]: a streamed track is a real `tracks` row from the moment it
 * is enqueued, so restoring a mixed local + YouTube queue is one id lookup rather than a parse of
 * provider identity out of a serialized string (ARCHITECTURE.md §3.1).
 */
data class QueueSnapshot(
    val entries: List<QueueEntry>,
    val currentIndex: Int,
    val currentPositionMs: Long,
    val isShuffleEnabled: Boolean,
    val repeatMode: Int,
    val timestamp: Long = System.currentTimeMillis()
) {
    val trackIds: List<TrackId> get() = entries.map { it.trackId }
}

/**
 * One queue position.
 *
 * Identity only. Where the audio comes from is a property of the referenced row, so a restore reads
 * the locator from the database instead of re-deriving it here — the same entry works for a local
 * file, a YouTube stream and a future download.
 */
data class QueueEntry(
    val trackId: TrackId
)
