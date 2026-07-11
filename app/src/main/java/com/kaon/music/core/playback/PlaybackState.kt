package com.kaon.music.core.playback

import com.kaon.music.media.model.Song
import com.kaon.music.media.manager.RepeatMode
import com.kaon.music.media.manager.ShuffleMode
import com.kaon.music.media.artwork.Artwork
import com.kaon.music.media.artwork.ArtworkColors
import androidx.compose.runtime.Immutable

@Immutable
data class PlaybackState(
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleMode: ShuffleMode = ShuffleMode.OFF,

    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,

    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val title: String = "Unknown",
    val artist: String = "Unknown",
    val album: String = "Unknown Album",

    val playbackSpeed: Float = 1.0f,
    val sleepTimerEndTime: Long? = null,

    val albumArtUri: String? = null,
    val artwork: Artwork = Artwork.None,
    val artworkColors: ArtworkColors? = null,
    val audioInfo: com.kaon.music.media.codec.AudioInfo? = null
) {
    val currentSong: Song?
        get() = queue.getOrNull(currentIndex)

    fun toCurrentSongState(): CurrentSongState? {
        val song = currentSong ?: return null
        return CurrentSongState(
            id = song.id,
            title = title,
            artist = artist,
            album = album,
            artistId = song.artistId,
            albumId = song.albumId,
            artwork = artwork,
            artworkColors = artworkColors,
            audioInfo = audioInfo
        )
    }

    fun toProgressState() = ProgressState(
        currentPosition = currentPosition,
        duration = duration,
        bufferedPosition = bufferedPosition,
        isPlaying = isPlaying
    )

    fun toControlsState() = ControlsState(
        isPlaying = isPlaying,
        repeatMode = repeatMode,
        shuffleMode = shuffleMode,
        hasNext = hasNext,
        hasPrevious = hasPrevious
    )
}

@Immutable
data class CurrentSongState(
    val id: Long = -1L,
    val title: String = "Unknown",
    val artist: String = "Unknown",
    val album: String = "Unknown Album",
    val artistId: Long = -1L,
    val albumId: Long = -1L,
    val artwork: Artwork = Artwork.None,
    val artworkColors: ArtworkColors? = null,
    val audioInfo: com.kaon.music.media.codec.AudioInfo? = null
)

@Immutable
data class ProgressState(
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val isPlaying: Boolean = false
)

@Immutable
data class ControlsState(
    val isPlaying: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleMode: ShuffleMode = ShuffleMode.OFF,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false
)
