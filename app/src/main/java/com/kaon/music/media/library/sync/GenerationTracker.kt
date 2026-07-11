package com.kaon.music.media.library.sync

import java.time.Instant

data class GenerationSnapshot(
    val generation: Long,
    val volumeName: String,
    val timestamp: Instant
)

interface GenerationTracker {
    fun currentGeneration(): GenerationSnapshot
}
