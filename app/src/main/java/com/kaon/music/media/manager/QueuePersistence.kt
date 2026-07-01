package com.kaon.music.media.manager

data class QueueSnapshot(
    val songIds: List<Long>,
    val currentIndex: Int,
    val repeatMode: String,
    val shuffleMode: String,
    val playbackPosition: Long = 0L,
    val currentSongId: Long = -1L,
    val timestamp: Long = System.currentTimeMillis()
)

interface QueuePersistence {
    suspend fun save(snapshot: QueueSnapshot)
    suspend fun restore(): QueueSnapshot?
}
