package com.kaon.music.core.playback

import com.kaon.music.media.model.Song
import com.kaon.music.media.manager.RepeatMode
import kotlinx.coroutines.flow.StateFlow
import kotlinx.collections.immutable.ImmutableList

interface PlayerController {

    val playbackState: StateFlow<PlaybackState>
    val errorEvents: kotlinx.coroutines.flow.Flow<com.kaon.music.media.state.PlaybackError>
    
    val currentSong: StateFlow<CurrentSongState?>
    val progress: StateFlow<ProgressState>
    val controls: StateFlow<ControlsState>
    val queue: StateFlow<ImmutableList<Song>>

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

    fun toggleShuffle()

    fun setRepeatMode(mode: RepeatMode)

    fun setPlaybackSpeed(speed: Float)

    fun setSleepTimer(minutes: Int)

    fun cancelSleepTimer()
}
