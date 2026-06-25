package com.kaon.music.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class ExoPlaybackEngine(
    private val context: Context
) : PlaybackEngine {

    private val player = ExoPlayer.Builder(context)
        .build()

    override fun setMediaUri(uri: String) {
        player.setMediaItem(
            MediaItem.fromUri(uri)
        )
        player.prepare()
    }

    override fun setMediaResource(resourceId: Int) {
        val uri = Uri.parse(
            "android.resource://${context.packageName}/$resourceId"
        )

        player.setMediaItem(
            MediaItem.fromUri(uri)
        )
        player.prepare()
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun stop() {
        player.stop()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    override fun release() {
        player.release()
    }
}