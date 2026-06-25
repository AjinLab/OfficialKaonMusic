package com.kaon.music.media

import androidx.compose.material3.Button

class MediaManager(
    private val engine: PlaybackEngine
) {

    fun load(uri: String) {
        engine.setMediaUri(uri)
    }

    fun loadResource(resourceId: Int) {
        engine.setMediaResource(resourceId)
    }

    fun play() {
        engine.play()
    }

    fun pause() {
        engine.pause()
    }

    fun stop() {
        engine.stop()
    }
}