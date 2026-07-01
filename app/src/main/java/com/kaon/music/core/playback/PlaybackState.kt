package com.kaon.music.core.playback

import com.kaon.music.media.model.Song
import com.kaon.music.media.manager.RepeatMode
import com.kaon.music.media.manager.ShuffleMode
import com.kaon.music.media.artwork.Artwork
import com.kaon.music.media.artwork.ArtworkColors

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

    val title: String = "Unknown",
    val artist: String = "Unknown",
    val album: String = "Unknown Album",

    val playbackSpeed: Float = 1.0f,
    val sleepTimerEndTime: Long? = null,

    val albumArtUri: String? = null,
    val artwork: Artwork = Artwork.None,
    val artworkColors: ArtworkColors? = null
) {
    val currentSong: Song?
        get() = queue.getOrNull(currentIndex)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PlaybackState

        if (queue != other.queue) return false
        if (currentIndex != other.currentIndex) return false
        if (hasNext != other.hasNext) return false
        if (hasPrevious != other.hasPrevious) return false
        if (isPlaying != other.isPlaying) return false
        if (currentPosition != other.currentPosition) return false
        if (duration != other.duration) return false
        if (title != other.title) return false
        if (artist != other.artist) return false
        if (album != other.album) return false
        if (playbackSpeed != other.playbackSpeed) return false
        if (sleepTimerEndTime != other.sleepTimerEndTime) return false
        if (albumArtUri != other.albumArtUri) return false
        if (artwork != other.artwork) return false
        if (artworkColors != other.artworkColors) return false

        return true
    }

    override fun hashCode(): Int {
        var result = queue.hashCode()
        result = 31 * result + currentIndex
        result = 31 * result + hasNext.hashCode()
        result = 31 * result + hasPrevious.hashCode()
        result = 31 * result + isPlaying.hashCode()
        result = 31 * result + currentPosition.hashCode()
        result = 31 * result + duration.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + playbackSpeed.hashCode()
        result = 31 * result + (sleepTimerEndTime?.hashCode() ?: 0)
        result = 31 * result + (albumArtUri?.hashCode() ?: 0)
        result = 31 * result + artwork.hashCode()
        result = 31 * result + (artworkColors?.hashCode() ?: 0)
        return result
    }
}