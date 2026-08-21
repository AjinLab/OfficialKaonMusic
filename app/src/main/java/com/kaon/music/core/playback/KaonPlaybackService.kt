package com.kaon.music.core.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.kaon.music.app.MainActivity
import com.kaon.music.core.data.db.KaonDatabase
import com.kaon.music.core.data.model.PlayEvent
import com.kaon.music.core.data.model.QueueSnapshot
import com.kaon.music.core.data.repository.HistoryRepository
import com.kaon.music.core.data.repository.TrackRepository
import com.kaon.music.core.data.sync.MediaStoreScanner
import com.kaon.music.core.data.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Android MediaSessionService hosting the ExoPlayer instance.
 *
 * Implements ARCHITECTURE_ATTRIBUTED.md §3 & §4:
 * - Single source of truth for runtime playback state and live timeline.
 * - Media3 owns audio focus and becoming-noisy events.
 * - Manages queue snapshot restoration on cold start and debounced persistence.
 */
class KaonPlaybackService : MediaSessionService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null

    private lateinit var database: KaonDatabase
    private lateinit var trackRepository: TrackRepository
    private lateinit var historyRepository: HistoryRepository
    private lateinit var snapshotManager: QueueSnapshotManager

    private var currentTrackId: Long? = null
    private var trackStartPlayTimestampMs: Long = 0L
    private var hasRecordedCurrentTrackPlayEvent = false

    override fun onCreate() {
        super.onCreate()
        Timber.tag("PlaybackService").i("KaonPlaybackService creating")

        // Initialize data dependencies
        database = KaonDatabase.getInstance(applicationContext)
        val scanner = MediaStoreScanner(applicationContext)
        val syncEngine = SyncEngine(scanner, database.trackDao())
        trackRepository = TrackRepository(database.trackDao(), database.favoriteDao(), syncEngine)
        historyRepository = HistoryRepository(database.playEventDao())
        snapshotManager = QueueSnapshotManager(database.queueSnapshotDao(), serviceScope)

        // 1. Build and configure ExoPlayer with built-in Focus and Becoming Noisy handling
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true) // Media3 handles audio focus
            .setHandleAudioBecomingNoisy(true) // Media3 handles becoming noisy (pause on unplug)
            .build()

        // 2. Setup Activity pending intent for notification clicks
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 3. Build MediaSession
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(MediaSessionCallback())
            .build()

        // 4. Attach Player listeners for queue snapshotting and listening history tracking
        setupPlayerListener()

        // 5. Attempt cold-start queue restoration
        restoreQueueSnapshotIfIdle()
    }

    private fun setupPlayerListener() {
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                checkAndRecordPlayEvent(isTransition = true)

                currentTrackId = mediaItem?.mediaId?.toLongOrNull()
                trackStartPlayTimestampMs = System.currentTimeMillis()
                hasRecordedCurrentTrackPlayEvent = false

                scheduleQueueSnapshot()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    // Flush immediately on pause/stop (§4 ARCHITECTURE_ATTRIBUTED.md)
                    checkAndRecordPlayEvent(isTransition = false)
                    snapshotManager.flush()
                } else {
                    scheduleQueueSnapshot()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    checkAndRecordPlayEvent(isTransition = true)
                    snapshotManager.flush()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                scheduleQueueSnapshot()
            }
        })
    }

    private fun scheduleQueueSnapshot() {
        val count = player.mediaItemCount
        if (count == 0) return

        val trackIds = mutableListOf<Long>()
        for (i in 0 until count) {
            val item = player.getMediaItemAt(i)
            item.mediaId.toLongOrNull()?.let { trackIds.add(it) }
        }

        val snapshot = QueueSnapshot(
            trackIds = trackIds,
            currentIndex = player.currentMediaItemIndex,
            currentPositionMs = player.currentPosition,
            isShuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode
        )

        val immediate = !player.isPlaying
        snapshotManager.scheduleSnapshotSave(snapshot, immediate = immediate)
    }

    private fun restoreQueueSnapshotIfIdle() {
        serviceScope.launch {
            // Restore rule: Only restore if player is empty and not currently playing
            if (player.mediaItemCount > 0) return@launch

            val snapshot = snapshotManager.loadSnapshot() ?: return@launch
            if (player.mediaItemCount > 0) return@launch // Double-check race condition

            val tracks = trackRepository.getTracksByIds(snapshot.trackIds)
            if (tracks.isEmpty() || player.mediaItemCount > 0) return@launch

            val mediaItems = tracks.map { track ->
                MediaItem.Builder()
                    .setMediaId(track.id.toString())
                    .setUri(track.contentUri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.title)
                            .setArtist(track.artist)
                            .setAlbumTitle(track.album)
                            .build()
                    )
                    .build()
            }

            val targetIndex = snapshot.currentIndex.coerceIn(0, mediaItems.size - 1)
            player.setMediaItems(mediaItems, targetIndex, snapshot.currentPositionMs)
            player.repeatMode = snapshot.repeatMode
            player.shuffleModeEnabled = snapshot.isShuffleEnabled
            player.prepare()
            player.playWhenReady = false // Restored queue starts paused

            Timber.tag("PlaybackService").i("Restored ${mediaItems.size} tracks from queue snapshot at index $targetIndex")
        }
    }

    private fun checkAndRecordPlayEvent(isTransition: Boolean) {
        val trackId = currentTrackId ?: return
        if (hasRecordedCurrentTrackPlayEvent) return

        val durationMs = player.duration
        val currentPos = player.currentPosition

        // Threshold rule (D6 in k3.md): Record play if played >= 30 seconds OR >= 50% of track
        val thresholdMs = if (durationMs > 0) minOf(30_000L, durationMs / 2) else 30_000L

        if (currentPos >= thresholdMs) {
            hasRecordedCurrentTrackPlayEvent = true
            serviceScope.launch(Dispatchers.IO) {
                historyRepository.recordPlayEvent(
                    trackId = trackId,
                    eventType = PlayEvent.EventType.PLAY,
                    playedMs = currentPos
                )
            }
        } else if (isTransition && currentPos < thresholdMs && currentPos > 2000L) {
            // Skip event
            serviceScope.launch(Dispatchers.IO) {
                historyRepository.recordPlayEvent(
                    trackId = trackId,
                    eventType = PlayEvent.EventType.SKIP,
                    playedMs = currentPos
                )
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        Timber.tag("PlaybackService").i("KaonPlaybackService destroying")
        snapshotManager.flush()

        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }

        serviceScope.cancel()
        super.onDestroy()
    }

    private inner class MediaSessionCallback : MediaSession.Callback {
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }
}
