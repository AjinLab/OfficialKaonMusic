package com.kaon.music.core.playback.model

import com.kaon.music.core.data.model.Track

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

/**
 * Playback state, partitioned by change frequency (ARCHITECTURE.md §3.2).
 *
 * The single object this replaced carried the 500 ms-ticking position alongside the whole queue.
 * Because that object was a `combine` source in three screen ViewModels, every position tick
 * re-ran full-library sorting and filtering on the main dispatcher. Splitting by change frequency
 * is what makes that structurally impossible rather than merely discouraged.
 */

/**
 * Changes on track transition, play/pause, and mode changes — rare.
 *
 * Safe for any ViewModel to observe.
 */
data class NowPlaying(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val currentIndex: Int = -1,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isConnected: Boolean = false
)

/**
 * Changes on queue mutation — rare. Observed by the player screen only.
 *
 * Holds resolved [Track]s because the queue sheet renders titles and artwork directly. Resolution
 * happens once per timeline change in the facade, not once per observer.
 */
data class PlaybackQueue(
    val tracks: List<Track> = emptyList(),
    val currentIndex: Int = -1
)

/**
 * Changes every 500 ms while playing.
 *
 * Must be read at the leaf composable that draws it. Never hoist this into a screen-level UI state
 * object and never make it a `combine` source alongside library data.
 */
data class PlaybackProgress(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L
) {
    val fraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val bufferedFraction: Float
        get() = if (durationMs > 0) (bufferedPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}
