package com.kaon.music.media.engine

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.kaon.music.core.playback.PlaybackEngine
import com.kaon.music.media.service.KaonPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MediaSessionPlaybackEngine(
    private val context: Context
) : PlaybackEngine {

    private val _playerFlow = MutableStateFlow<Player?>(null)
    override val playerFlow: StateFlow<Player?> = _playerFlow.asStateFlow()

    override val player: Player?
        get() = mediaController

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    init {
        val sessionToken = SessionToken(context, ComponentName(context, KaonPlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get()
                if (controller != null) {
                    mediaController = controller
                    _playerFlow.value = controller
                    Log.d("MediaSessionPlayback", "MediaController connected successfully")
                }
            } catch (e: Exception) {
                Log.e("MediaSessionPlayback", "Failed to connect MediaController", e)
            }
        }, MoreExecutors.directExecutor())
    }

    override fun setMediaItem(item: MediaItem) {
        player?.let { activePlayer ->
            activePlayer.setMediaItem(item)
            activePlayer.prepare()
        }
    }

    override fun setMediaItems(items: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        player?.let { activePlayer ->
            activePlayer.setMediaItems(items, startIndex, startPositionMs)
            if (activePlayer.playbackState == Player.STATE_IDLE || activePlayer.playbackState == Player.STATE_ENDED) {
                activePlayer.prepare()
            }
        }
    }

    override fun setMediaResource(resourceId: Int) {
        val uri = android.net.Uri.parse(
            "android.resource://${context.packageName}/$resourceId"
        )
        player?.let { activePlayer ->
            activePlayer.setMediaItem(MediaItem.fromUri(uri))
            activePlayer.prepare()
        }
    }

    override fun play() {
        player?.play()
    }

    override fun pause() {
        player?.pause()
    }

    override fun stop() {
        player?.stop()
    }

    override fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    override fun setPlaybackSpeed(speed: Float) {
        player?.setPlaybackParameters(androidx.media3.common.PlaybackParameters(speed))
    }

    override fun release() {
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        mediaController = null
        _playerFlow.value = null
    }
}
