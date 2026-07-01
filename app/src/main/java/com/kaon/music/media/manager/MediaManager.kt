package com.kaon.music.media.manager

import android.content.Context
import android.net.Uri
import androidx.media3.common.Player
import com.kaon.music.media.model.Song
import com.kaon.music.core.playback.PlaybackEngine
import com.kaon.music.core.playback.PlayerController
import com.kaon.music.core.playback.QueueState
import com.kaon.music.media.services.MetadataProvider
import com.kaon.music.media.artwork.ArtworkLoader
import com.kaon.music.media.artwork.Artwork
import com.kaon.music.media.state.PlaybackStateStore
import com.kaon.music.media.library.LibraryController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

class MediaManager(
    private val context: Context,
    private val engine: PlaybackEngine,
    private val queueManager: QueueManager,
    private val metadataReader: MetadataProvider,
    private val artworkLoader: ArtworkLoader,
    private val queuePersistence: QueuePersistence,
    private val libraryController: LibraryController
) : PlayerController {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val player = engine.player
    private val stateStore = PlaybackStateStore()
    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private var artworkJob: kotlinx.coroutines.Job? = null
    override val playbackState = stateStore.state

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                stateStore.update { it.copy(isPlaying = isPlaying) }
                saveState()
            }

            override fun onPlaybackStateChanged(state: Int) {
                // Let ExoPlayer handle STATE_ENDED for playlist looping
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    val index = player.currentMediaItemIndex
                    if (index != queueManager.currentIndex()) {
                        val song = queueManager.play(index)
                        if (song != null) {
                            load(song)
                        }
                    }
                }
            }
        })

        scope.launch {
            while (true) {
                stateStore.update {
                    it.copy(
                        currentPosition = player.currentPosition,
                        duration = if (player.duration >= 0) player.duration else 0L
                    )
                }
                saveState()
                delay(5000)
            }
        }

        scope.launch {
            queueManager.queueState.collect { queueState ->
                stateStore.update {
                    it.copy(
                        queue = queueState.queue,
                        currentIndex = queueState.currentIndex,
                        hasNext = queueState.hasNext,
                        hasPrevious = queueState.hasPrevious,
                        repeatMode = queueState.repeatMode,
                        shuffleMode = if (queueState.shuffle) ShuffleMode.ON else ShuffleMode.OFF
                    )
                }
                saveState()

                val mediaItems = queueState.queue.map { song ->
                    androidx.media3.common.MediaItem.Builder()
                        .setMediaId(song.id.toString())
                        .setUri(song.uri)
                        .setMediaMetadata(
                            androidx.media3.common.MediaMetadata.Builder()
                                .setTitle(song.title)
                                .setArtist(song.artist)
                                .setAlbumTitle(song.album)
                                .setArtworkUri(song.artworkPath?.let { if (it.startsWith("/")) Uri.fromFile(java.io.File(it)) else Uri.parse(it) })
                                .build()
                        )
                        .build()
                }

                if (queueState.queue.isNotEmpty() && queueState.currentIndex >= 0) {
                    val needsPlaylistUpdate = player.mediaItemCount != mediaItems.size || 
                        (0 until player.mediaItemCount).any { player.getMediaItemAt(it).mediaId != mediaItems[it].mediaId }

                    if (needsPlaylistUpdate) {
                        val startPos = if (player.currentMediaItemIndex == queueState.currentIndex) player.currentPosition else 0L
                        engine.setMediaItems(mediaItems, queueState.currentIndex, startPos)
                    } else if (player.currentMediaItemIndex != queueState.currentIndex) {
                        player.seekTo(queueState.currentIndex, 0)
                    }
                }

                player.repeatMode = when (queueState.repeatMode) {
                    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                }
                player.shuffleModeEnabled = queueState.shuffle
            }
        }
    }

    override fun setQueue(queue: List<Song>, startIndex: Int) {
        queueManager.setQueue(queue, startIndex)
    }

    override fun currentQueue(): StateFlow<QueueState> {
        return queueManager.queueState
    }

    override fun play(index: Int) {
        val song = queueManager.play(index)
        if (song != null) {
            load(song)
            play()
        }
    }

    override fun playSong(song: Song) {
        val playedSong = queueManager.playSong(song)
        if (playedSong != null) {
            load(playedSong)
            play()
        }
    }

    override fun playNext(song: Song) {
        queueManager.playNext(song)
    }

    override fun playNext() {
        next()
    }

    override fun playPrevious() {
        previous()
    }

    private fun load(song: Song) {
        val metadata = metadataReader.read(song)
        val artworkUri = song.artworkPath?.let { Uri.parse(it) }

        val mediaMetadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(metadata.title)
            .setArtist(metadata.artist)
            .setAlbumTitle(metadata.album)
            .setArtworkUri(artworkUri)
            .build()

        val mediaItem = androidx.media3.common.MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(song.uri)
            .setMediaMetadata(mediaMetadata)
            .build()
            
        val currentIndex = queueManager.currentIndex()
        if (currentIndex in 0 until player.mediaItemCount) {
            player.replaceMediaItem(currentIndex, mediaItem)
        }

        stateStore.update {
            it.copy(
                title = metadata.title,
                artist = metadata.artist,
                album = metadata.album,
                artwork = Artwork.None,
                artworkColors = null
            )
        }
        
        // Cancel any in-flight artwork extraction (handles rapid track skipping)
        artworkJob?.cancel()
        artworkJob = scope.launch {
            val result = artworkLoader.load(song)
            // Only update if this job hasn't been cancelled by a newer track
            if (isActive) {
                stateStore.update {
                    it.copy(
                        artwork = result.artwork,
                        artworkColors = result.colors
                    )
                }
            }
        }
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

    override fun togglePlayback() {
        if (player.isPlaying) pause() else play()
    }

    override fun next() {
        val song = queueManager.next()
        if (song != null) {
            load(song)
            play()
        }
    }

    override fun previous() {
        if (player.currentPosition > 5000) {
            seekTo(0)
            return
        }
        val song = queueManager.previous()
        if (song != null) {
            load(song)
            play()
        }
    }

    override fun seekTo(position: Long) {
        player.seekTo(position)
    }

    override fun seekForward() {
        val newPos = (player.currentPosition + 10000).coerceAtMost(if (player.duration > 0) player.duration else 0)
        seekTo(newPos)
    }

    override fun seekBackward() {
        val newPos = (player.currentPosition - 10000).coerceAtLeast(0)
        seekTo(newPos)
    }

    fun refreshQueueState() {
        stateStore.update {
            val qs = queueManager.queueState.value
            it.copy(
                queue = qs.queue,
                currentIndex = qs.currentIndex,
                repeatMode = qs.repeatMode,
                shuffleMode = if (qs.shuffle) ShuffleMode.ON else ShuffleMode.OFF,
                hasNext = qs.hasNext,
                hasPrevious = qs.hasPrevious
            )
        }
    }

    override fun setShuffle(enabled: Boolean) {
        queueManager.setShuffle(enabled)
    }

    override fun setRepeatMode(mode: RepeatMode) {
        queueManager.setRepeatMode(mode)
    }

    private fun saveState() {
        val song = queueManager.current()
        val currentSongId = song?.id ?: -1L
        val snapshot = queueManager.getSnapshot(player.currentPosition, currentSongId)
        scope.launch(Dispatchers.IO) {
            queuePersistence.save(snapshot)
        }
    }

    suspend fun restoreState(library: LibraryController) {
        val snapshot = queuePersistence.restore() ?: return

        val songsUnsorted = library.getSongsByIds(snapshot.songIds)
        val songMap = songsUnsorted.associateBy { it.id }
        val restoredQueue = snapshot.songIds.mapNotNull { songMap[it] }

        queueManager.restoreFromSnapshot(snapshot, restoredQueue)
        
        val songToLoad = queueManager.current()
        if (songToLoad != null) {
            load(songToLoad)
            seekTo(snapshot.playbackPosition)
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        engine.setPlaybackSpeed(speed)
        stateStore.update { it.copy(playbackSpeed = speed) }
    }

    override fun setSleepTimer(minutes: Int) {
        cancelSleepTimer()
        val endTime = System.currentTimeMillis() + (minutes * 60 * 1000L)
        stateStore.update { it.copy(sleepTimerEndTime = endTime) }
        sleepTimerJob = scope.launch {
            delay(minutes * 60 * 1000L)
            pause()
            stateStore.update { it.copy(sleepTimerEndTime = null) }
        }
    }

    override fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        stateStore.update { it.copy(sleepTimerEndTime = null) }
    }
}