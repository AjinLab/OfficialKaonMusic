package com.kaon.music.core.data.model

/**
 * Raw listening event recorded for history and analytics preservation.
 */
data class PlayEvent(
    val id: Long = 0,
    val trackId: Long,
    val eventType: EventType,
    val playedAt: Long,
    val playedMs: Long
) {
    enum class EventType {
        PLAY,
        SKIP
    }
}

/**
 * Snapshot of the playback queue persisted to Room across process death.
 */
data class QueueSnapshot(
    val trackIds: List<Long>,
    val currentIndex: Int,
    val currentPositionMs: Long,
    val isShuffleEnabled: Boolean,
    val repeatMode: Int,
    val timestamp: Long = System.currentTimeMillis()
)
