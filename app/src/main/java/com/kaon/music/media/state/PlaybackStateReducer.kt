package com.kaon.music.media.state

import com.kaon.music.core.playback.PlaybackState
import com.kaon.music.media.artwork.Artwork
import com.kaon.music.media.artwork.ArtworkColors
import com.kaon.music.media.codec.AudioInfo
import com.kaon.music.media.model.Song

sealed interface PlaybackError {
    object StorageUnavailable : PlaybackError
    object PermissionDenied : PlaybackError
    object NetworkUnavailable : PlaybackError
    object CodecUnsupported : PlaybackError
    object Unknown : PlaybackError
    data class DecoderFailure(
        val rendererName: String,
        val codecName: String,
        val errorCode: Int
    ) : PlaybackError
}

sealed interface PlaybackStatus {
    object Idle : PlaybackStatus
    object Playing : PlaybackStatus
    object Paused : PlaybackStatus
    object Buffering : PlaybackStatus
    object Ended : PlaybackStatus
    data class Error(val error: PlaybackError) : PlaybackStatus
}

sealed interface PlaybackEvent {
    data class StatusChanged(val status: PlaybackStatus) : PlaybackEvent
    data class PositionChanged(val positionMs: Long) : PlaybackEvent
    data class MediaItemTransitioned(val songId: Long) : PlaybackEvent
    data class MetadataLoaded(
        val title: String,
        val artist: String,
        val album: String,
        val audioInfo: AudioInfo?
    ) : PlaybackEvent
    data class ArtworkLoaded(
        val artwork: Artwork,
        val colors: ArtworkColors?
    ) : PlaybackEvent
    data class QueueChanged(
        val queue: List<Song>,
        val currentIndex: Int
    ) : PlaybackEvent
    data class PlaybackSpeedChanged(val speed: Float) : PlaybackEvent
    data class SleepTimerEndTimeChanged(val endTime: Long?) : PlaybackEvent
}

class PlaybackStateReducer {
    fun reduce(currentState: PlaybackState, event: PlaybackEvent): PlaybackState {
        return when (event) {
            is PlaybackEvent.StatusChanged -> {
                currentState.copy(
                    isPlaying = event.status is PlaybackStatus.Playing
                )
            }
            is PlaybackEvent.PositionChanged -> {
                currentState.copy(currentPosition = event.positionMs)
            }
            is PlaybackEvent.MediaItemTransitioned -> {
                currentState.copy(currentPosition = 0L, duration = 0L)
            }
            is PlaybackEvent.MetadataLoaded -> {
                currentState.copy(
                    title = event.title,
                    artist = event.artist,
                    album = event.album,
                    audioInfo = event.audioInfo
                )
            }
            is PlaybackEvent.ArtworkLoaded -> {
                currentState.copy(
                    artwork = event.artwork,
                    artworkColors = event.colors
                )
            }
            is PlaybackEvent.QueueChanged -> {
                val index = event.currentIndex
                val hasNext = index >= 0 && index < event.queue.size - 1
                val hasPrevious = index > 0
                currentState.copy(
                    queue = event.queue,
                    currentIndex = index,
                    hasNext = hasNext,
                    hasPrevious = hasPrevious
                )
            }
            is PlaybackEvent.PlaybackSpeedChanged -> {
                currentState.copy(playbackSpeed = event.speed)
            }
            is PlaybackEvent.SleepTimerEndTimeChanged -> {
                currentState.copy(sleepTimerEndTime = event.endTime)
            }
        }
    }
}
