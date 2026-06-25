package com.kaon.music.media

interface PlaybackEngine {

    fun setMediaUri(uri: String)

    fun setMediaResource(resourceId: Int)

    fun play()

    fun pause()

    fun stop()

    fun seekTo(positionMs: Long)

    fun release()
}