package com.kaon.music.media.manager

import android.content.Context
import android.net.Uri
import androidx.media3.common.Player
import com.kaon.music.media.model.Song
import com.kaon.music.core.playback.PlaybackEngine
import com.kaon.music.core.playback.PlayerController
import com.kaon.music.core.playback.QueueState
import com.kaon.music.core.playback.CurrentSongState
import com.kaon.music.core.playback.ProgressState
import com.kaon.music.core.playback.ControlsState
import com.kaon.music.media.services.MetadataProvider
import com.kaon.music.media.artwork.ArtworkLoader
import com.kaon.music.media.artwork.Artwork
import com.kaon.music.media.state.PlaybackStateStore
import com.kaon.music.media.library.LibraryController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import com.kaon.music.media.state.PlaybackStateReducer
import com.kaon.music.media.state.PlaybackStatus
import com.kaon.music.media.state.PlaybackEvent
import kotlin.time.Duration.Companion.seconds

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
    private val reducer = PlaybackStateReducer()
    private val sleepTimerManager = SleepTimerManager(
        clock = java.time.Clock.systemDefaultZone(),
        scope = scope,
        onTimerExpired = { pause() }
    )
    private var artworkJob: kotlinx.coroutines.Job? = null
    override val playbackState = stateStore.state

    override val currentSong: StateFlow<CurrentSongState?> = 
        playbackState.map { it.toCurrentSongState() }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, null)
    
    override val progress: StateFlow<ProgressState> = 
        playbackState.map { it.toProgressState() }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, ProgressState())
        
    override val controls: StateFlow<ControlsState> = 
        playbackState.map { it.toControlsState() }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, ControlsState())

    override val queue: StateFlow<ImmutableList<Song>> =
        playbackState.map { it.queue }
            .distinctUntilChanged()
            .map { it.toImmutableList() }
            .stateIn(scope, SharingStarted.Eagerly, emptyList<Song>().toImmutableList())

    private var lastSavedSongId: Long = -1L
    private var lastSavedQueueRevision: Long = -1L
    private var lastSavedPosition: Long = -1L
    private var lastLoadedSongId: Long = -1L
    private var queueRevision: Long = 0L
    private var lastSyncedQueue: List<Song>? = null

    init {
        scope.launch {
            sleepTimerManager.sleepTimerState.collect { timerState ->
                val endTime = when {
                    timerState.endOfSong -> -1L
                    timerState.enabled && timerState.remaining != null -> {
                        System.currentTimeMillis() + timerState.remaining.inWholeMilliseconds
                    }
                    else -> null
                }
                stateStore.update { it.copy(sleepTimerEndTime = endTime) }
            }
        }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val status = if (isPlaying) PlaybackStatus.Playing else PlaybackStatus.Paused
                stateStore.update { reducer.reduce(it, PlaybackEvent.StatusChanged(status)) }
                saveState(force = true)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    stateStore.update { it.copy(duration = if (player.duration >= 0) player.duration else it.duration) }
                }
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                val timerState = sleepTimerManager.sleepTimerState.value
                if (timerState.enabled && timerState.endOfSong) {
                    sleepTimerManager.stopTimer()
                    pause()
                    return
                }
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    val index = player.currentMediaItemIndex
                    if (index != queueManager.currentIndex() && index >= 0) {
                        val song = queueManager.play(index)
                        if (song != null) {
                            load(song)
                        }
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e(
                    "KAON",
                    "Playback failed: errorCode=${error.errorCode}, errorCodeName=${error.errorCodeName}, cause=${error.cause?.message}",
                    error
                )

                val isUnsupported = error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                        error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED ||
                        error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ||
                        error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                
                if (isUnsupported) {
                    android.widget.Toast.makeText(
                        context,
                        "This audio format isn't supported on your device.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } else {
                    android.widget.Toast.makeText(
                        context,
                        "Playback failed: ${error.localizedMessage ?: "Unknown error"}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                player.stop()
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                for (group in tracks.groups) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        android.util.Log.d(
                            "KAON",
                            """
                            TracksChanged:
                            sampleMime=${format.sampleMimeType}
                            codecs=${format.codecs}
                            bitrate=${format.bitrate}
                            sampleRate=${format.sampleRate}
                            channelCount=${format.channelCount}
                            """.trimIndent()
                        )
                    }
                }
            }
        })

        scope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    val currentPos = player.currentPosition
                    val duration = if (player.duration >= 0) player.duration else 0L
                    val buffered = player.bufferedPosition
                    
                    stateStore.update {
                        it.copy(
                            currentPosition = currentPos,
                            duration = duration,
                            bufferedPosition = buffered
                        )
                    }
                    saveState()
                }
                delay(if (player.isPlaying) 250 else 1000)
            }
        }

        scope.launch {
            queueManager.queueState.collect { queueState ->
                val currentState = stateStore.current()
                if (
                    currentState.queue !== queueState.queue ||
                    currentState.currentIndex != queueState.currentIndex ||
                    currentState.repeatMode != queueState.repeatMode ||
                    (currentState.shuffleMode == ShuffleMode.ON) != queueState.shuffle
                ) {
                    queueRevision++
                }
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

                player.repeatMode = when (queueState.repeatMode) {
                    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                }
                player.shuffleModeEnabled = queueState.shuffle
            }
        }
    }

    private fun syncPlayerPlaylist(queue: List<Song>, currentIndex: Int, startPositionMs: Long = 0) {
        if (queue.isEmpty() || currentIndex < 0) return

        if (lastSyncedQueue === queue && player.mediaItemCount == queue.size) {
            if (player.currentMediaItemIndex != currentIndex || startPositionMs > 0) {
                player.seekTo(currentIndex, startPositionMs)
            }
            return
        }

        val mediaItems = queue.map { song ->
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

        val needsPlaylistUpdate = player.mediaItemCount != mediaItems.size || 
            (0 until player.mediaItemCount).any { player.getMediaItemAt(it).mediaId != mediaItems[it].mediaId }

        if (needsPlaylistUpdate) {
            engine.setMediaItems(mediaItems, currentIndex, startPositionMs)
            lastSyncedQueue = queue
        } else {
            lastSyncedQueue = queue
            if (player.currentMediaItemIndex != currentIndex || startPositionMs > 0) {
                player.seekTo(currentIndex, startPositionMs)
            }
        }
    }

    override fun setQueue(queue: List<Song>, startIndex: Int) {
        lastLoadedSongId = -1L
        queueManager.setQueue(queue, startIndex)
        syncPlayerPlaylist(queue, startIndex)
        val song = queue.getOrNull(startIndex)
        if (song != null) {
            load(song)
        }
    }

    override fun currentQueue(): StateFlow<QueueState> {
        return queueManager.queueState
    }

    override fun play(index: Int) {
        val song = queueManager.play(index)
        if (song != null) {
            syncPlayerPlaylist(queueManager.queueState.value.queue, index)
            load(song)
            play()
        }
    }

    override fun playSong(song: Song) {
        val playedSong = queueManager.playSong(song)
        if (playedSong != null) {
            val state = queueManager.queueState.value
            syncPlayerPlaylist(state.queue, state.currentIndex)
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
        if (song.id == lastLoadedSongId) return
        lastLoadedSongId = song.id

        android.util.Log.d(
            "KAON",
            "load: songId=${song.id} uri=${song.uri} mime=${song.mimeType}"
        )

        // 1. Instantly update UI metadata with basic values
        stateStore.update {
            it.copy(
                title = song.title,
                artist = song.artist,
                album = song.album,
                artwork = Artwork.None,
                artworkColors = null,
                audioInfo = null
            )
        }

        // 2. Fetch full tags, artwork, palette, and audio info asynchronously & concurrently
        artworkJob?.cancel()
        artworkJob = scope.launch {
            val metadataDeferred = async(Dispatchers.IO) { metadataReader.read(song) }
            val audioInfoDeferred = async(Dispatchers.IO) {
                com.kaon.music.media.codec.AudioMetadataExtractor.extract(
                    context = context,
                    uriString = song.uri,
                    mimeTypeString = song.mimeType,
                    path = song.path
                )
            }
            val artworkDeferred = async(Dispatchers.IO) { artworkLoader.load(song) }

            val metadata = metadataDeferred.await()
            val audioInfo = audioInfoDeferred.await()
            val artworkResult = artworkDeferred.await()

            if (isActive) {
                stateStore.update {
                    it.copy(
                        title = metadata.title.takeIf { it.isNotBlank() } ?: song.title,
                        artist = metadata.artist.takeIf { it.isNotBlank() } ?: song.artist,
                        album = metadata.album.takeIf { it.isNotBlank() } ?: song.album,
                        artwork = artworkResult.artwork,
                        artworkColors = artworkResult.colors,
                        audioInfo = audioInfo
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
        lastLoadedSongId = -1L
        player.stop()
    }

    override fun togglePlayback() {
        if (player.isPlaying) pause() else play()
    }

    override fun next() {
        val song = queueManager.next()
        if (song != null) {
            val state = queueManager.queueState.value
            syncPlayerPlaylist(state.queue, state.currentIndex)
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
            val state = queueManager.queueState.value
            syncPlayerPlaylist(state.queue, state.currentIndex)
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

    override fun toggleShuffle() {
        val currentMode = queueManager.queueState.value.shuffle
        setShuffle(!currentMode)
    }

    override fun setRepeatMode(mode: RepeatMode) {
        queueManager.setRepeatMode(mode)
    }

    private fun saveState() {
        saveState(force = false)
    }

    private fun saveState(force: Boolean) {
        val song = queueManager.current()
        val currentSongId = song?.id ?: -1L
        val currentPos = player.currentPosition

        val shouldSave = force || 
                currentSongId != lastSavedSongId || 
                queueRevision != lastSavedQueueRevision ||
                Math.abs(currentPos - lastSavedPosition) >= 30000

        if (shouldSave) {
            lastSavedSongId = currentSongId
            lastSavedQueueRevision = queueRevision
            lastSavedPosition = currentPos

            val snapshot = queueManager.getSnapshot(currentPos, currentSongId)
            scope.launch(Dispatchers.IO) {
                queuePersistence.save(snapshot)
            }
        }
    }

    suspend fun restoreState(library: LibraryController) {
        val snapshot = queuePersistence.restore() ?: return

        val songsUnsorted = library.getSongsByIds(snapshot.songIds)
        val songMap = songsUnsorted.associateBy { it.id }
        val restoredQueue = snapshot.songIds.mapNotNull { songMap[it] }

        queueManager.restoreFromSnapshot(snapshot, restoredQueue)
        
        if (restoredQueue.isNotEmpty()) {
            syncPlayerPlaylist(restoredQueue, queueManager.currentIndex(), snapshot.playbackPosition)
        }
        
        val songToLoad = queueManager.current()
        if (songToLoad != null) {
            load(songToLoad)
        }
    }

    override fun setPlaybackSpeed(speed: Float) {
        engine.setPlaybackSpeed(speed)
        stateStore.update { it.copy(playbackSpeed = speed) }
    }

    override fun setSleepTimer(minutes: Int) {
        if (minutes == -1) {
            sleepTimerManager.startEndOfSongTimer()
        } else {
            sleepTimerManager.startTimer((minutes * 60).seconds)
        }
    }

    override fun cancelSleepTimer() {
        sleepTimerManager.stopTimer()
    }
}
