package com.kaon.music.core.data.model

/**
 * Persisted playback queue.
 *
 * [entries] carries enough identity to rebuild a Media3 timeline without a database row per item.
 * That matters for YouTube tracks: they are not inserted into the `tracks` table (see
 * ARCHITECTURE.md §6 — unifying that is migration phase 4), so a restore keyed only on `trackId`
 * silently dropped every streamed item in the queue.
 */
data class QueueSnapshot(
    val entries: List<QueueEntry>,
    val currentIndex: Int,
    val currentPositionMs: Long,
    val isShuffleEnabled: Boolean,
    val repeatMode: Int,
    val timestamp: Long = System.currentTimeMillis()
) {
    val trackIds: List<Long> get() = entries.map { it.trackId }
}

/**
 * One queue position.
 *
 * [youtubeVideoId] is the persisted provider identity. It is what lets a cold start resolve a
 * streamed track directly instead of re-searching for it — the durable half of what WAVE achieves
 * with its `trackId -> videoId` Hive box, without a second persistence technology.
 */
data class QueueEntry(
    val trackId: Long,
    val youtubeVideoId: String? = null
) {
    val isRemote: Boolean get() = !youtubeVideoId.isNullOrBlank()
}
