package com.kaon.music.core.playback

import com.kaon.music.media.model.Song
import com.kaon.music.media.manager.RepeatMode

data class QueueState(
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF
) {
    val currentSong: Song?
        get() = queue.getOrNull(currentIndex)
}
