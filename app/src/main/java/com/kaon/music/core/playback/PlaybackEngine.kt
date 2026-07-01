package com.kaon.music.core.playback

import androidx.media3.common.Player

interface PlaybackEngine {

    val player: Player

    fun setMediaItem(item: androidx.media3.common.MediaItem)
    
    fun setMediaItems(items: List<androidx.media3.common.MediaItem>, startIndex: Int, startPositionMs: Long)

    fun setMediaResource(resourceId: Int)

    fun play()

    fun pause()

    fun stop()

    fun seekTo(positionMs: Long)

    fun setPlaybackSpeed(speed: Float)

    fun release()
}