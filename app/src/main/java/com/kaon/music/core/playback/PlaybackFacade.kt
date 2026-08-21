package com.kaon.music.core.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.data.repository.TrackRepository
import com.kaon.music.core.playback.model.PlaybackState
import com.kaon.music.core.playback.model.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Process-scoped Playback Facade.
 *
 * Implements ARCHITECTURE_ATTRIBUTED.md §3 & §12:
 * - Single point of entry for UI interactions with Media3 playback.
 * - Exposes immutable StateFlow<PlaybackState> and intent methods.
 * - Enforces granular queue timeline mutations (addMediaItem, moveMediaItem, removeMediaItem).
 * - "Kaon observes Media3; Kaon never mirrors Media3".
 */
class PlaybackFacade(
    private val context: Context,
    private val trackRepository: TrackRepository
) {
    private val facadeScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private var positionPollJob: Job? = null

    init {
        connectToService()
    }

    private fun connectToService() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, KaonPlaybackService::class.java)
        )

        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get()
                if (controller != null) {
                    mediaController = controller
                    attachPlayerListener(controller)
                    updateFullState(controller)
                    _playbackState.update { it.copy(isConnected = true) }
                    Timber.tag("PlaybackFacade").i("Connected to MediaSessionService")
                }
            } catch (e: Exception) {
                Timber.tag("PlaybackFacade").e(e, "Failed to connect to MediaSessionService")
            }
        }, MoreExecutors.directExecutor())
    }

    private fun attachPlayerListener(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    startPositionPolling()
                } else {
                    stopPositionPolling()
                    updatePosition(player)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                facadeScope.launch {
                    val track = mediaItem?.mediaId?.toLongOrNull()?.let { trackId ->
                        trackRepository.getTrackById(trackId)
                    }
                    _playbackState.update {
                        it.copy(
                            currentTrack = track,
                            currentIndex = player.currentMediaItemIndex,
                            durationMs = player.duration.coerceAtLeast(0L),
                            playbackPositionMs = player.currentPosition.coerceAtLeast(0L)
                        )
                    }
                }
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                updateQueueFromTimeline(player)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _playbackState.update {
                    it.copy(
                        durationMs = player.duration.coerceAtLeast(0L),
                        bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
                    )
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _playbackState.update { it.copy(isShuffleEnabled = shuffleModeEnabled) }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _playbackState.update {
                    it.copy(
                        repeatMode = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                            else -> RepeatMode.OFF
                        }
                    )
                }
            }
        })
    }

    private fun updateFullState(player: Player) {
        val currentMediaItem = player.currentMediaItem
        val currentId = currentMediaItem?.mediaId?.toLongOrNull()

        facadeScope.launch {
            val track = currentId?.let { trackRepository.getTrackById(it) }
            _playbackState.update {
                it.copy(
                    currentTrack = track,
                    isPlaying = player.isPlaying,
                    playbackPositionMs = player.currentPosition.coerceAtLeast(0L),
                    durationMs = player.duration.coerceAtLeast(0L),
                    bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
                    isShuffleEnabled = player.shuffleModeEnabled,
                    repeatMode = when (player.repeatMode) {
                        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                        else -> RepeatMode.OFF
                    },
                    currentIndex = player.currentMediaItemIndex
                )
            }
            updateQueueFromTimeline(player)
            if (player.isPlaying) {
                startPositionPolling()
            }
        }
    }

    private fun updateQueueFromTimeline(player: Player) {
        facadeScope.launch {
            val count = player.mediaItemCount
            val trackIds = mutableListOf<Long>()
            for (i in 0 until count) {
                player.getMediaItemAt(i).mediaId.toLongOrNull()?.let { trackIds.add(it) }
            }
            val queueTracks = trackRepository.getTracksByIds(trackIds)
            _playbackState.update {
                it.copy(
                    queue = queueTracks,
                    currentIndex = player.currentMediaItemIndex
                )
            }
        }
    }

    private fun startPositionPolling() {
        positionPollJob?.cancel()
        positionPollJob = facadeScope.launch {
            while (isActive) {
                mediaController?.let { player ->
                    updatePosition(player)
                }
                delay(200) // ~5 updates per second for smooth scrubber updates without jank
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollJob?.cancel()
        positionPollJob = null
    }

    private fun updatePosition(player: Player) {
        _playbackState.update {
            it.copy(
                playbackPositionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = player.duration.coerceAtLeast(0L),
                bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
            )
        }
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
        mediaController?.seekTo(positionMs)
        _playbackState.update { it.copy(playbackPositionMs = positionMs) }
    }

    fun playTrack(track: Track, queue: List<Track>) {
        val controller = mediaController ?: return
        val mediaItems = queue.map { it.toMediaItem() }
        val startIndex = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        controller.setMediaItems(mediaItems, startIndex, 0L)
        controller.prepare()
        controller.play()
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

    /**
     * Granular queue mutation: Add to end of queue without restarting playback.
     */
    fun enqueue(track: Track) {
        mediaController?.addMediaItem(track.toMediaItem())
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
        mediaController?.moveMediaItem(fromIndex, toIndex)
    }

    /**
     * Granular queue mutation: Remove item from timeline.
     */
    fun removeQueueItem(index: Int) {
        mediaController?.removeMediaItem(index)
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

    fun release() {
        stopPositionPolling()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }

    private fun Track.toMediaItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(contentUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .build()
            )
            .build()
    }
}
