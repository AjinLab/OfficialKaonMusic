package com.kaon.music.core.model

import com.kaon.music.core.data.model.Track
import com.kaon.music.core.playback.PlaybackEvent
import com.kaon.music.core.playback.model.NowPlaying
import com.kaon.music.core.playback.model.PlaybackProgress
import com.kaon.music.core.playback.model.PlaybackQueue
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Capability controller exposing transport control and partitioned playback state to UI layers.
 *
 * Enforces ARCHITECTURE.md §3.2 and §4: UI layers interact through this interface rather than
 * directly touching Media3 playback sessions or platform services.
 */
interface PlayerController {
    val nowPlaying: StateFlow<NowPlaying>
    val queue: StateFlow<PlaybackQueue>
    val progress: StateFlow<PlaybackProgress>
    val oneShotEvents: SharedFlow<PlaybackEvent>

    fun playTrack(track: Track, queue: List<Track>)
    fun playQueue(queue: List<Track>, startIndex: Int = 0)
    fun togglePlayPause()
    fun skipNext()
    fun skipPrevious()
    fun seekTo(positionMs: Long)
    fun enqueue(track: Track)
    fun playNext(track: Track)
    fun moveQueueItem(fromIndex: Int, toIndex: Int)
    fun removeQueueItem(index: Int)
    fun clearQueue()
    fun toggleShuffle()
    fun cycleRepeatMode()
}
