package com.kaon.music.core.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
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
import com.kaon.music.core.data.model.QueueEntry
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
import com.kaon.music.core.online.YouTubeStreamExtractor
import com.kaon.music.app.KaonApplication
import kotlinx.coroutines.runBlocking

import com.kaon.music.core.data.repository.SettingsRepository
import com.kaon.music.core.data.repository.UserSettings

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
    private lateinit var settingsRepository: SettingsRepository

    private var currentUserSettings: UserSettings = UserSettings()

    private var currentTrackId: Long? = null
    private var trackStartPlayTimestampMs: Long = 0L
    private var hasRecordedCurrentTrackPlayEvent = false

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        Timber.tag("PlaybackService").i("KaonPlaybackService creating")

        // Initialize data dependencies
        database = KaonDatabase.getInstance(applicationContext)
        val scanner = MediaStoreScanner(applicationContext)
        val syncEngine = SyncEngine(scanner, database.trackDao())
        trackRepository = TrackRepository(database.trackDao(), database.favoriteDao(), syncEngine, database.playEventDao())
        historyRepository = HistoryRepository(database.playEventDao(), database.trackDao())
        snapshotManager = QueueSnapshotManager(database.queueSnapshotDao(), serviceScope)
        youtubeSessionManager =
            (application as? KaonApplication)?.container?.youtubeSessionManager
                ?: YouTubeSessionManager(applicationContext, serviceScope)
        settingsRepository =
            (application as? KaonApplication)?.container?.settingsRepository
                ?: SettingsRepository(applicationContext)

        // 1. Build and configure ExoPlayer with ResolvingDataSource for online streams
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val httpDataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(
            okhttp3.OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        )

        val resolvingDataSourceFactory = ResolvingDataSource.Factory(
            httpDataSourceFactory,
            object : ResolvingDataSource.Resolver {
                override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                    val uri = dataSpec.uri
                    if (uri.scheme == "youtube" || uri.host == "youtube.com" || uri.host == "music.youtube.com" || uri.host == "youtu.be") {
                        val videoId = when {
                            uri.scheme == "youtube" -> uri.authority ?: uri.host ?: uri.schemeSpecificPart?.removePrefix("//") ?: ""
                            uri.host == "youtube.com" || uri.host == "music.youtube.com" -> uri.getQueryParameter("v") ?: ""
                            uri.host == "youtu.be" -> uri.lastPathSegment ?: ""
                            else -> ""
                        }.trim()

                        if (videoId.isNotBlank()) {
                            // Resolution failure must surface as a classified IO error, never as the
                            // raw `youtube://` placeholder reaching the HTTP stack. ExoPlayer's bounded
                            // load-error policy and the service error listener take it from there.
                            val resolved = try {
                                runBlocking {
                                    YouTubeStreamResolver.resolveStreamData(
                                        videoId = videoId,
                                        quality = currentUserSettings.streamingQuality,
                                        audioType = currentUserSettings.preferredAudioType,
                                        wifiOnly = currentUserSettings.wifiOnlyStreaming,
                                        // The user is waiting on this loader thread, so it outranks
                                        // any speculative pre-resolution (ARCHITECTURE.md §7).
                                        purpose = ResolvePurpose.PLAYBACK
                                    )
                                }.getOrThrow()
                            } catch (ce: kotlinx.coroutines.CancellationException) {
                                // Loader thread interrupted (load cancelled by a skip/release);
                                // abandon this open without an error cycle.
                                null
                            } catch (t: Throwable) {
                                throw java.io.IOException("Stream resolution failed for video $videoId", t)
                            }
                            if (resolved != null && resolved.url.isNotBlank()) {
                                return dataSpec.withResolvedStream(resolved)
                            }
                            throw java.io.IOException("Stream resolution returned no playable URL for video $videoId")
                        }
                        throw java.io.IOException("Malformed stream placeholder URI: $uri")
                    }
                    return dataSpec
                }
            }
        )

        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(this).apply {
            setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableAudioTrackPlaybackParams(true)
        }

        val dataSourceFactory = DefaultDataSource.Factory(this, resolvingDataSourceFactory)
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory().apply {
            setConstantBitrateSeekingEnabled(true)
        }
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)

        player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true) // Media3 handles audio focus
            .setHandleAudioBecomingNoisy(true) // Media3 handles becoming noisy (pause on unplug)
            .build()

        // Observe and apply user settings dynamically to ExoPlayer
        serviceScope.launch {
            settingsRepository.userSettingsFlow.collect { settings ->
                currentUserSettings = settings
                player.skipSilenceEnabled = settings.skipSilence
                player.setAudioAttributes(audioAttributes, settings.pauseOnFocusLoss)
            }
        }

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
                val currentItem = player.currentMediaItem
                val uri = currentItem?.localConfiguration?.uri
                val isYouTube = uri != null && (uri.scheme == "youtube" || uri.host == "youtube.com" || uri.host == "music.youtube.com" || uri.host == "youtu.be")
                val videoId = if (isYouTube) {
                    when {
                        uri!!.scheme == "youtube" -> uri.authority ?: uri.host ?: uri.schemeSpecificPart?.removePrefix("//") ?: ""
                        uri.host == "youtube.com" || uri.host == "music.youtube.com" -> uri.getQueryParameter("v") ?: ""
                        uri.host == "youtu.be" -> uri.lastPathSegment ?: ""
                        else -> ""
                    }.trim()
                } else ""

                val is403or410 = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                    error.cause?.message?.contains("403") == true ||
                    error.cause?.message?.contains("410") == true

                if (isYouTube && videoId.isNotBlank() && is403or410 && consecutiveErrorCount == 0) {
                    consecutiveErrorCount++
                    Timber.tag("playback").i("403/410 on YouTube video $videoId — invalidating and re-resolving")
                    YouTubeStreamExtractor.markWebRemixFailed(videoId)
                    YouTubeStreamResolver.invalidate(videoId)
                    // A rejected URL often means the player config the cipher used has rotated. The
                    // refresh is fire-and-forget: this recovery attempt proceeds either way, and a
                    // config change only affects subsequent extractions.
                    serviceScope.launch {
                        runCatching { YouTubeStreamExtractor.refreshAfterStreamRejection() }
                            .onFailure { Timber.tag("playback").w(it, "Cipher config refresh failed") }
                    }
                    val currentPos = player.currentPosition
                    val mediaItem = player.currentMediaItem
                    if (mediaItem != null) {
                        val index = player.currentMediaItemIndex
                        player.replaceMediaItem(index, mediaItem)
                        player.seekTo(index, currentPos)
                        player.prepare()
                        player.play()
                    }
                    return
                }

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

        val entries = mutableListOf<QueueEntry>()
        for (i in 0 until count) {
            val item = player.getMediaItemAt(i)
            val trackId = item.mediaId.toLongOrNull() ?: continue
            // Persist the provider identity alongside the id. Without it, a restore can only look the
            // track up in Room, and streamed tracks have no row there.
            entries += QueueEntry(trackId = trackId, youtubeVideoId = item.youtubeVideoId())
        }

        val snapshot = QueueSnapshot(
            entries = entries,
            currentIndex = player.currentMediaItemIndex,
            currentPositionMs = player.currentPosition,
            isShuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode
        )

        val immediate = !player.isPlaying
        snapshotManager.scheduleSnapshotSave(snapshot, immediate = immediate)
    }

    /** Reads the persisted provider id from the item extras, falling back to its `youtube://` URI. */
    private fun MediaItem.youtubeVideoId(): String? {
        mediaMetadata.extras?.getString(MediaItemExtras.YOUTUBE_VIDEO_ID)?.takeIf { it.isNotBlank() }?.let { return it }
        val uri = localConfiguration?.uri ?: return null
        return if (uri.scheme == "youtube") uri.schemeSpecificPart?.removePrefix("//")?.takeIf { it.isNotBlank() } else null
    }

    private fun restoreQueueSnapshotIfIdle() {
        serviceScope.launch {
            // Restore rule: Only restore if player is empty and not currently playing
            if (player.mediaItemCount > 0) return@launch

            val snapshot = snapshotManager.loadSnapshot() ?: return@launch
            if (player.mediaItemCount > 0) return@launch // Double-check race condition

            // Local tracks are rehydrated from Room; remote ones are rebuilt from the persisted
            // provider id, so a streamed queue survives process death instead of being dropped.
            val localTracks = trackRepository
                .getTracksByIds(snapshot.entries.filterNot { it.isRemote }.map { it.trackId })
                .associateBy { it.id }

            val restorable = snapshot.entries.filter { it.isRemote || localTracks.containsKey(it.trackId) }
            if (restorable.isEmpty()) {
                // Restore found only orphaned local tracks: clear rather than leave a snapshot that
                // can never be restored.
                Timber.tag("queue").w("Restoration snapshot contained no restorable entries. Clearing.")
                snapshotManager.clearSnapshot()
                return@launch
            }
            if (player.mediaItemCount > 0) return@launch

            val mediaItems = restorable.map { entry ->
                val track = localTracks[entry.trackId]
                val uri = when {
                    entry.isRemote -> Uri.parse("youtube://${entry.youtubeVideoId}")
                    else -> track?.contentUri
                }
                MediaItem.Builder()
                    .setMediaId(entry.trackId.toString())
                    .setUri(uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track?.title)
                            .setArtist(track?.artist)
                            .setAlbumTitle(track?.album)
                            .setArtworkUri(track?.contentUri.takeIf { track?.source == "YOUTUBE" })
                            .setExtras(
                                Bundle().apply {
                                    putString(
                                        MediaItemExtras.SOURCE,
                                        if (entry.isRemote) MediaItemExtras.SOURCE_YOUTUBE else MediaItemExtras.SOURCE_LOCAL
                                    )
                                    putString(MediaItemExtras.YOUTUBE_VIDEO_ID, entry.youtubeVideoId)
                                }
                            )
                            .build()
                    )
                    .build()
            }

            // Index is derived from the restorable list, not the original snapshot: dropping an
            // orphaned entry shifts every position after it, so the pre-filter index would resume on
            // the wrong track.
            val previousEntry = snapshot.entries.getOrNull(snapshot.currentIndex)
            val targetIndex = restorable.indexOf(previousEntry)
                .takeIf { it >= 0 }
                ?.coerceIn(0, mediaItems.size - 1)
                ?: 0
            player.setMediaItems(mediaItems, targetIndex, snapshot.currentPositionMs)
            player.repeatMode = snapshot.repeatMode
            player.shuffleModeEnabled = snapshot.isShuffleEnabled
            player.prepare()
            player.playWhenReady = false // Restored queue starts paused (M2-D2)

            Timber.tag("queue").i("Restored ${mediaItems.size} entries from queue snapshot at index $targetIndex (paused)")
        }
    }

    private fun checkAndRecordPlayEvent(isTransition: Boolean) {
        if (!currentUserSettings.recordHistory) return
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
