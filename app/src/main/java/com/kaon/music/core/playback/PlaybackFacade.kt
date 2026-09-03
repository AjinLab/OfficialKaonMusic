package com.kaon.music.core.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.data.repository.SettingsRepository
import com.kaon.music.core.data.repository.TrackRepository
import com.kaon.music.core.online.AudioQuality
import com.kaon.music.core.online.AudioType
import com.kaon.music.core.playback.model.NowPlaying
import com.kaon.music.core.playback.model.PlaybackProgress
import com.kaon.music.core.playback.model.PlaybackQueue
import com.kaon.music.core.playback.model.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

sealed interface PlaybackEvent {
    data class TrackUnplayable(val trackTitle: String) : PlaybackEvent
}

/**
 * Process-scoped playback facade — the only way UI reaches Media3.
 *
 * ARCHITECTURE.md §3.2 and §4:
 * - Exposes three flows partitioned by change frequency ([nowPlaying], [queue], [progress]) rather
 *   than one object mixing 500 ms position ticks with the whole queue.
 * - Derives state from Media3 callbacks. It never writes state it did not observe from the player,
 *   so the facade cannot drift out of sync with the transport.
 * - Enforces identical-queue detection (seek instead of rebuilding the timeline).
 * - Enforces granular timeline mutations; `setMediaItems` only for an initial queue load.
 */
class PlaybackFacade(
    private val context: Context,
    private val trackRepository: TrackRepository,
    settingsRepository: SettingsRepository? = null
) {
    private val facadeScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _nowPlaying = MutableStateFlow(NowPlaying())
    val nowPlaying: StateFlow<NowPlaying> = _nowPlaying.asStateFlow()

    private val _queue = MutableStateFlow(PlaybackQueue())
    val queue: StateFlow<PlaybackQueue> = _queue.asStateFlow()

    private val _progress = MutableStateFlow(PlaybackProgress())
    val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()

    @androidx.annotation.VisibleForTesting
    internal fun updateNowPlayingForTesting(state: NowPlaying) {
        _nowPlaying.value = state
    }

    @androidx.annotation.VisibleForTesting
    internal fun updateQueueForTesting(state: PlaybackQueue) {
        _queue.value = state
    }

    @androidx.annotation.VisibleForTesting
    internal fun updateProgressForTesting(state: PlaybackProgress) {
        _progress.value = state
    }

    private val _oneShotEvents = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 1)
    val oneShotEvents: SharedFlow<PlaybackEvent> = _oneShotEvents.asSharedFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var playerListener: Player.Listener? = null

    private var positionPollJob: Job? = null
    private var preResolveEnabled = true
    private var streamingQuality: AudioQuality = AudioQuality.AUTO
    private var preferredAudioType: AudioType = AudioType.AUTO

    init {
        // The settings repository is injected (ARCHITECTURE.md §4) rather than constructed here;
        // constructing one produced a fourth independent DataStore collector for the same file.
        if (settingsRepository != null) {
            facadeScope.launch {
                try {
                    settingsRepository.userSettingsFlow.collect { settings ->
                        preResolveEnabled = settings.preResolveNextTracks
                        streamingQuality = settings.streamingQuality
                        preferredAudioType = settings.preferredAudioType
                    }
                } catch (e: Throwable) {
                    Timber.tag("PlaybackFacade").w(e, "Settings collection stopped")
                }
            }
        }
        try {
            connectToService()
        } catch (e: Throwable) {
            Timber.tag("PlaybackFacade").w(e, "Could not connect to playback service (expected in unit tests)")
        }
    }

    private fun connectToService() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, KaonPlaybackService::class.java)
        )

        controllerFuture = MediaController.Builder(context, sessionToken)
            .setListener(object : MediaController.Listener {
                override fun onCustomCommand(
                    controller: MediaController,
                    command: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    if (command.customAction == KaonPlaybackService.ACTION_TRACK_UNPLAYABLE) {
                        val title = args.getString("track_title") ?: "Unknown Track"
                        _oneShotEvents.tryEmit(PlaybackEvent.TrackUnplayable(title))
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .buildAsync()

        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get()
                if (controller != null) {
                    mediaController = controller
                    attachPlayerListener(controller)
                    updateFullState(controller)
                    _nowPlaying.update { it.copy(isConnected = true) }
                    Timber.tag("PlaybackFacade").i("Connected to MediaSessionService")
                }
            } catch (e: Exception) {
                Timber.tag("PlaybackFacade").e(e, "Failed to connect to MediaSessionService")
            }
        }, MoreExecutors.directExecutor())
    }

    private fun attachPlayerListener(player: Player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _nowPlaying.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    startPositionPolling()
                } else {
                    stopPositionPolling()
                    updatePosition(player)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                facadeScope.launch {
                    val track = mediaItem?.let { item -> resolveTrack(item) }
                    _nowPlaying.update {
                        it.copy(
                            currentTrack = track,
                            currentIndex = player.currentMediaItemIndex,
                            durationMs = player.duration.coerceAtLeast(0L)
                        )
                    }
                    _queue.update { it.copy(currentIndex = player.currentMediaItemIndex) }
                    updatePosition(player)
                    triggerNextTracksPreResolution()
                }
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                updateQueueFromTimeline(player)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _nowPlaying.update { it.copy(durationMs = player.duration.coerceAtLeast(0L)) }
                updatePosition(player)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _nowPlaying.update { it.copy(isShuffleEnabled = shuffleModeEnabled) }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _nowPlaying.update { it.copy(repeatMode = repeatMode.toRepeatMode()) }
            }
        }
        playerListener = listener
        player.addListener(listener)
    }

    private fun updateFullState(player: Player) {
        val currentMediaItem = player.currentMediaItem

        facadeScope.launch {
            val track = currentMediaItem?.let { item -> resolveTrack(item) }
            _nowPlaying.update {
                it.copy(
                    currentTrack = track,
                    isPlaying = player.isPlaying,
                    durationMs = player.duration.coerceAtLeast(0L),
                    currentIndex = player.currentMediaItemIndex,
                    isShuffleEnabled = player.shuffleModeEnabled,
                    repeatMode = player.repeatMode.toRepeatMode()
                )
            }
            updatePosition(player)
            updateQueueFromTimeline(player)
            if (player.isPlaying) {
                startPositionPolling()
            }
        }
    }

    private fun updateQueueFromTimeline(player: Player) {
        facadeScope.launch {
            val count = player.mediaItemCount
            val queueTracks = buildList {
                for (i in 0 until count) {
                    resolveTrack(player.getMediaItemAt(i))?.let(::add)
                }
            }
            _queue.value = PlaybackQueue(
                tracks = queueTracks,
                currentIndex = player.currentMediaItemIndex
            )
        }
    }

    private fun startPositionPolling() {
        positionPollJob?.cancel()
        positionPollJob = facadeScope.launch {
            while (isActive) {
                mediaController?.let { player ->
                    updatePosition(player)
                }
                // Battery rule: position ticks ~500ms while playing, none while paused. Only
                // PlaybackProgress is written here, so a tick cannot invalidate queue or library
                // state (ARCHITECTURE.md §3.2).
                delay(500)
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollJob?.cancel()
        positionPollJob = null
    }

    private fun updatePosition(player: Player) {
        _progress.value = PlaybackProgress(
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.coerceAtLeast(0L),
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
        )
    }

    private fun Int.toRepeatMode(): RepeatMode = when (this) {
        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
        else -> RepeatMode.OFF
    }

    // ==================== Intent Methods (UI Actions) ====================

    fun play() {
        mediaController?.play()
    }

    fun pause() {
        mediaController?.pause()
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun skipNext() {
        mediaController?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        mediaController?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        controller.seekTo(positionMs)
        // Reading straight back from the player keeps this an observation rather than an optimistic
        // write (ARCHITECTURE.md §4). The player has already applied the seek synchronously.
        updatePosition(controller)
    }

    /**
     * Tapping a track sets the queue starting at the tapped track.
     *
     * Identical-queue detection: if the tapped context matches the current queue, seek instead of
     * rebuilding the timeline, which would otherwise restart the currently playing item.
     */
    fun playTrack(track: Track, queue: List<Track>) {
        val controller = mediaController ?: return
        if (queue.isEmpty()) return

        val currentQueue = _queue.value.tracks
        val isIdenticalQueue = currentQueue.isNotEmpty() &&
                currentQueue.size == queue.size &&
                currentQueue.indices.all { i -> currentQueue[i].id == queue[i].id }

        val targetIndex = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        if (isIdenticalQueue) {
            if (controller.currentMediaItemIndex != targetIndex) {
                controller.seekToDefaultPosition(targetIndex)
            }
            if (!controller.isPlaying) {
                controller.play()
            }
        } else {
            val mediaItems = queue.map { it.toMediaItem() }
            controller.setMediaItems(mediaItems, targetIndex, 0L)
            controller.prepare()
            controller.play()
        }
        // Next-track preparation is triggered by onMediaItemTransition (fires for both
        // setMediaItems and identical-queue seeks), using the player's shuffle-aware next index.
    }

    fun playQueue(queue: List<Track>, startIndex: Int = 0) {
        val controller = mediaController ?: return
        if (queue.isEmpty()) return

        val mediaItems = queue.map { it.toMediaItem() }
        val index = startIndex.coerceIn(0, queue.size - 1)

        controller.setMediaItems(mediaItems, index, 0L)
        controller.prepare()
        controller.play()
    }

    private var preResolutionJob: kotlinx.coroutines.Job? = null

    /**
     * Prepares the likely next queue item while the current track plays.
     *
     * The next index comes from the player itself (`nextMediaItemIndex`), which already
     * accounts for shuffle order and repeat mode; timeline-index arithmetic from the facade
     * would pre-resolve the wrong items when shuffle is enabled. Repeat-one (next == current)
     * and end-of-queue (C.INDEX_UNSET) resolve nothing. Fires on media item transitions —
     * queue mutations that change the next item take effect at the next transition, and a
     * stale warm entry is harmless (bounded TTL).
     */
    private fun triggerNextTracksPreResolution() {
        preResolutionJob?.cancel()
        if (!preResolveEnabled) return
        val controller = mediaController ?: return
        val currentIndex = controller.currentMediaItemIndex
        val nextIndex = controller.nextMediaItemIndex
        if (nextIndex < 0 || nextIndex == currentIndex) return
        preResolutionJob = facadeScope.launch {
            val nextItem = runCatching { controller.getMediaItemAt(nextIndex) }.getOrNull() ?: return@launch
            val nextTrack = resolveTrack(nextItem) ?: return@launch
            if (nextTrack.source == "YOUTUBE" && !nextTrack.youtubeVideoId.isNullOrBlank()) {
                YouTubeStreamResolver.preResolve(
                    videoId = nextTrack.youtubeVideoId,
                    quality = streamingQuality,
                    audioType = preferredAudioType
                )
            }
        }
    }

    /**
     * Granular queue mutation: Add to end of queue without restarting playback.
     */
    fun enqueue(track: Track) {
        val controller = mediaController ?: return
        controller.addMediaItem(track.toMediaItem())
    }

    /**
     * Granular queue mutation: Play next after currently playing item.
     */
    fun playNext(track: Track) {
        val controller = mediaController ?: return
        val nextIndex = (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount)
        controller.addMediaItem(nextIndex, track.toMediaItem())
    }

    /**
     * Granular queue mutation: Move item in timeline.
     */
    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val controller = mediaController ?: return
        if (fromIndex in 0 until controller.mediaItemCount && toIndex in 0 until controller.mediaItemCount) {
            controller.moveMediaItem(fromIndex, toIndex)
        }
    }

    /**
     * Granular queue mutation: Remove item from timeline.
     * Removing current item advances seamlessly (M2 Failure matrix #13).
     */
    fun removeQueueItem(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until controller.mediaItemCount) {
            controller.removeMediaItem(index)
        }
    }

    /**
     * Clears all queue items and stops playback.
     *
     * `clearMediaItems` triggers `onTimelineChanged`/`onMediaItemTransition`, so the resulting empty
     * state arrives through the listeners like any other transport change rather than being written
     * here (ARCHITECTURE.md §4).
     */
    fun clearQueue() {
        val controller = mediaController ?: return
        preResolutionJob?.cancel()
        controller.stop()
        controller.clearMediaItems()
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        val controller = mediaController ?: return
        val nextMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = nextMode
    }

    /**
     * Releases the controller, its listener, and the facade scope.
     *
     * Called from [com.kaon.music.app.di.AppContainer] teardown paths only; the facade is
     * process-scoped, so in normal operation it lives until the process dies.
     */
    fun release() {
        stopPositionPolling()
        playerListener?.let { listener -> mediaController?.removeListener(listener) }
        playerListener = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
        facadeScope.cancel()
    }

    private fun Track.toMediaItem(): MediaItem {
        val uri = when {
            source == "YOUTUBE" && !youtubeVideoId.isNullOrBlank() -> Uri.parse("youtube://$youtubeVideoId")
            else -> contentUri
        }
        val builder = MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
        return builder.setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(contentUri.takeIf { source == "YOUTUBE" })
                    .setExtras(Bundle().apply {
                        putLong(EXTRA_MEDIA_STORE_ID, mediaStoreId)
                        putString(EXTRA_SOURCE, source)
                        putString(EXTRA_YOUTUBE_VIDEO_ID, youtubeVideoId)
                        putString(EXTRA_MIME_TYPE, mimeType)
                        putLong(EXTRA_ALBUM_ID, albumId)
                        putLong(EXTRA_DURATION_MS, durationMs)
                        putLong(EXTRA_SIZE_BYTES, sizeBytes)
                    })
                    .build()
            )
            .build()
    }

    /**
     * Online search results are intentionally not inserted into the local library.
     * Media3 still carries their complete metadata, so the UI must resolve from
     * that metadata when Room has no matching row.
     */
    private suspend fun resolveTrack(mediaItem: MediaItem): Track? {
        val id = mediaItem.mediaId.toLongOrNull() ?: return null
        trackRepository.getTrackById(id)?.let { return it }

        val metadata = mediaItem.mediaMetadata
        val extras = metadata.extras
        val uri = mediaItem.localConfiguration?.uri
        val source = extras?.getString(EXTRA_SOURCE)
            ?: if (uri?.scheme == "youtube") "YOUTUBE" else "LOCAL"
        val youtubeVideoId = extras?.getString(EXTRA_YOUTUBE_VIDEO_ID)
            ?: uri?.takeIf { it.scheme == "youtube" }?.schemeSpecificPart

        return Track(
            id = id,
            mediaStoreId = extras?.getLong(EXTRA_MEDIA_STORE_ID, 0L) ?: 0L,
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            album = metadata.albumTitle?.toString().orEmpty(),
            albumId = extras?.getLong(EXTRA_ALBUM_ID, 0L) ?: 0L,
            durationMs = extras?.getLong(EXTRA_DURATION_MS, 0L) ?: 0L,
            sizeBytes = extras?.getLong(EXTRA_SIZE_BYTES, 0L) ?: 0L,
            dateModified = 0L,
            contentUri = metadata.artworkUri.takeIf { source == "YOUTUBE" } ?: uri,
            source = source,
            youtubeVideoId = youtubeVideoId,
            mimeType = extras?.getString(EXTRA_MIME_TYPE)
        )
    }

    private companion object {
        const val EXTRA_MEDIA_STORE_ID = "kaon.media_store_id"
        const val EXTRA_SOURCE = "kaon.source"
        const val EXTRA_YOUTUBE_VIDEO_ID = "kaon.youtube_video_id"
        const val EXTRA_MIME_TYPE = "kaon.mime_type"
        const val EXTRA_ALBUM_ID = "kaon.album_id"
        const val EXTRA_DURATION_MS = "kaon.duration_ms"
        const val EXTRA_SIZE_BYTES = "kaon.size_bytes"
    }
}
