package com.kaon.music.core.playback.model

import com.kaon.music.core.data.model.Track

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

/**
 * Immutable playback state exposed to the UI via PlaybackFacade.
 */
data class PlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val playbackPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isConnected: Boolean = false
) {
    val progress: Float
        get() = if (durationMs > 0) (playbackPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}
