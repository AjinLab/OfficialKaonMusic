package com.kaon.music.media.engine

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import com.kaon.music.core.playback.PlaybackEngine

class ExoPlaybackEngine(
    private val context: Context
) : PlaybackEngine {

    private val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)
        .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

    override val player = ExoPlayer.Builder(context, renderersFactory)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )
        .build()

    init {

        player.addListener(
            object : Player.Listener {

                override fun onPlayerError(
                    error: PlaybackException
                ) {
                    Log.e(
                        "KAON",
                        "PLAYER ERROR",
                        error
                    )
                }

                override fun onIsPlayingChanged(
                    isPlaying: Boolean
                ) {
                    Log.d(
                        "KAON",
                        "IS_PLAYING=$isPlaying position=${player.currentPosition}"
                    )
                }
            }
        )
    }

    override fun setMediaItem(item: MediaItem) {
        player.setMediaItem(item)
        player.prepare()
    }

    override fun setMediaItems(items: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        player.setMediaItems(items, startIndex, startPositionMs)
        if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
            player.prepare()
        }
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

        Log.d(
            "KAON",
            "Duration=${player.duration}"
        )

        Log.d(
            "KAON",
            "Position=${player.currentPosition}"
        )
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

    override fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackParameters(androidx.media3.common.PlaybackParameters(speed))
    }

    override fun release() {
        player.release()
    }
}