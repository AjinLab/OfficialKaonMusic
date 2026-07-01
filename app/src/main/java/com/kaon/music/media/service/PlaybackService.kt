package com.kaon.music.media.service

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.kaon.music.KaonApplication
import com.kaon.music.core.playback.PlaybackEngine
import com.kaon.music.core.playback.PlayerController
import com.kaon.music.media.engine.KaonForwardingPlayer
import com.kaon.music.core.kernel.get

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val kernel = (applicationContext as KaonApplication).kernel
        val engine = kernel.get(PlaybackEngine::class)
        val playerController = kernel.get(PlayerController::class)
        
        val forwardingPlayer = KaonForwardingPlayer(engine.player, playerController)
        mediaSession = MediaSession.Builder(this, forwardingPlayer).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
