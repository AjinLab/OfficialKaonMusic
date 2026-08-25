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

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.kaon.music.core.data.online.YouTubeSessionManager
import kotlinx.coroutines.runBlocking

/**
 * Android MediaSessionService hosting the ExoPlayer instance.
 *
 * Implements ARCHITECTURE_ATTRIBUTED.md §3 & §4:
 * - Single source of truth for runtime playback state and live timeline.
 * - Media3 owns audio focus and becoming-noisy events.
 * - Manages queue snapshot restoration on cold start and debounced persistence.
 * - Handles hybrid playback: instant local audio & on-demand YouTube streaming.
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
    private lateinit var youtubeSessionManager: YouTubeSessionManager

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
        trackRepository = TrackRepository(database.trackDao(), database.favoriteDao(), syncEngine, database.playEventDao())
        historyRepository = HistoryRepository(database.playEventDao())
        snapshotManager = QueueSnapshotManager(database.queueSnapshotDao(), serviceScope)
        youtubeSessionManager = YouTubeSessionManager(applicationContext, serviceScope)

        // 1. Build and configure ExoPlayer with ResolvingDataSource for online streams
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        val resolvingDataSourceFactory = ResolvingDataSource.Factory(
            httpDataSourceFactory,
            object : ResolvingDataSource.Resolver {
                override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                    val uri = dataSpec.uri
                    if (uri.scheme == "youtube" || uri.host == "youtube.com" || uri.host == "youtu.be") {
                        val videoId = uri.getQueryParameter("v") ?: uri.host.takeIf { uri.scheme == "youtube" } ?: uri.lastPathSegment ?: ""
                        if (videoId.isNotBlank()) {
                            val resolvedUrl = runBlocking {
                                YouTubeStreamResolver.resolveStreamUrl(videoId).getOrNull()
                            }
                            if (!resolvedUrl.isNullOrBlank()) {
                                return dataSpec.buildUpon().setUri(Uri.parse(resolvedUrl)).build()
                            }
                        }
                    }
                    return dataSpec
                }
            }
        )

        val dataSourceFactory = DefaultDataSource.Factory(this, resolvingDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true) // Media3 handles audio focus
            .setHandleAudioBecomingNoisy(true) // Media3 handles becoming noisy (pause on unplug)
            .build()

        // 2. Setup Activity pending intent for notification clicks (opens full player)
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_EXPAND_PLAYER, true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 3. Build MediaSession
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(MediaSessionCallback())
            .build()

        // 4. Attach Player listeners for queue snapshotting, unplayable-item policy, and listening history
        setupPlayerListener()

        // 5. Attempt cold-start queue restoration
        restoreQueueSnapshotIfIdle()
    }

    private var consecutiveErrorCount = 0

    private fun setupPlayerListener() {
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Reset consecutive error counter on successful transition
                consecutiveErrorCount = 0
                checkAndRecordPlayEvent(isTransition = true)

                currentTrackId = mediaItem?.mediaId?.toLongOrNull()
                trackStartPlayTimestampMs = System.currentTimeMillis()
                hasRecordedCurrentTrackPlayEvent = false

                scheduleQueueSnapshot()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                consecutiveErrorCount++
                val failedTitle = player.currentMediaItem?.mediaMetadata?.title?.toString() ?: "Unknown Track"
                Timber.tag("PlaybackService").w(error, "Playback error on track '$failedTitle' (consecutive error count: $consecutiveErrorCount)")

                // Notify connected controllers (PlaybackFacade) of unplayable track event
                val args = Bundle().apply {
                    putString("track_title", failedTitle)
                    putInt("error_count", consecutiveErrorCount)
                }
                mediaSession?.broadcastCustomCommand(
                    SessionCommand(ACTION_TRACK_UNPLAYABLE, Bundle.EMPTY),
                    args
                )

                // Unplayable-item policy (M2-D3):
                // Auto-advance to next playable item if below max consecutive errors (3)
                if (consecutiveErrorCount < MAX_CONSECUTIVE_ERRORS && player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                } else {
                    Timber.tag("PlaybackService").e("Stopping playback: Max consecutive error threshold ($MAX_CONSECUTIVE_ERRORS) reached.")
                    consecutiveErrorCount = 0
                    player.stop()
                }
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
            if (tracks.isEmpty()) {
                // If restore finds only orphans/missing tracks: clean empty state, snapshot cleared (§4 #11)
                Timber.tag("PlaybackService").w("Restoration snapshot contained only missing tracks. Clearing snapshot.")
                snapshotManager.clearSnapshot()
                return@launch
            }
            if (player.mediaItemCount > 0) return@launch

            val mediaItems = tracks.map { track ->
                val uri = when {
                    track.source == "YOUTUBE" && !track.youtubeVideoId.isNullOrBlank() -> Uri.parse("youtube://${track.youtubeVideoId}")
                    else -> track.contentUri
                }
                MediaItem.Builder()
                    .setMediaId(track.id.toString())
                    .setUri(uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.title)
                            .setArtist(track.artist)
                            .setAlbumTitle(track.album)
                            .setArtworkUri(track.contentUri.takeIf { track.source == "YOUTUBE" })
                            .build()
                    )
                    .build()
            }

            val targetIndex = snapshot.currentIndex.coerceIn(0, mediaItems.size - 1)
            player.setMediaItems(mediaItems, targetIndex, snapshot.currentPositionMs)
            player.repeatMode = snapshot.repeatMode
            player.shuffleModeEnabled = snapshot.isShuffleEnabled
            player.prepare()
            player.playWhenReady = false // Restored queue starts paused (M2-D2)

            Timber.tag("PlaybackService").i("Restored ${mediaItems.size} tracks from queue snapshot at index $targetIndex (paused)")
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
                try {
                    historyRepository.recordPlayEvent(
                        trackId = trackId,
                        eventType = PlayEvent.EventType.PLAY,
                        playedMs = currentPos
                    )
                } catch (e: Exception) {
                    Timber.tag("PlaybackService").w(e, "Non-fatal: Failed to record PLAY event for track $trackId")
                }
            }
        } else if (isTransition && currentPos < thresholdMs && currentPos > 2000L) {
            // Skip event
            serviceScope.launch(Dispatchers.IO) {
                try {
                    historyRepository.recordPlayEvent(
                        trackId = trackId,
                        eventType = PlayEvent.EventType.SKIP,
                        playedMs = currentPos
                    )
                } catch (e: Exception) {
                    Timber.tag("PlaybackService").w(e, "Non-fatal: Failed to record SKIP event for track $trackId")
                }
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

    companion object {
        const val ACTION_TRACK_UNPLAYABLE = "com.kaon.music.ACTION_TRACK_UNPLAYABLE"
        const val EXTRA_EXPAND_PLAYER = "extra_expand_player"
        private const val MAX_CONSECUTIVE_ERRORS = 3
    }
}
