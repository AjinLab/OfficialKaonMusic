package com.kaon.music.core.playback

import com.kaon.music.media.model.Song
import com.kaon.music.media.manager.RepeatMode
import kotlinx.coroutines.flow.StateFlow

interface PlayerController {

    val playbackState: StateFlow<PlaybackState>

    fun setQueue(queue: List<Song>, startIndex: Int = 0)

    fun currentQueue(): StateFlow<QueueState>

    fun play(index: Int)

    fun playSong(song: Song)
    
    fun playNext(song: Song)
    
    fun playNext()

    fun playPrevious()

    fun play()

    fun pause()

    fun stop()

    fun togglePlayback()

    fun next()

    fun previous()

    fun seekTo(position: Long)
    
    fun seekForward()
    
    fun seekBackward()

    fun setShuffle(enabled: Boolean)

    fun setRepeatMode(mode: RepeatMode)

    fun setPlaybackSpeed(speed: Float)

    fun setSleepTimer(minutes: Int)

    fun cancelSleepTimer()
}