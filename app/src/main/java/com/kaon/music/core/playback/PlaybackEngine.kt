package com.kaon.music.core.playback

import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import kotlinx.coroutines.flow.StateFlow

interface PlaybackEngine {

    val player: Player?
    val playerFlow: StateFlow<Player?>

    val isPlaying: Boolean
    val currentPosition: Long
    val duration: Long
    val bufferedPosition: Long
    val mediaItemCount: Int
    val currentMediaItemIndex: Int

    fun getMediaItemAt(index: Int): MediaItem?
    fun replaceMediaItem(index: Int, mediaItem: MediaItem)
    fun setRepeatMode(repeatMode: Int)
    fun setShuffleModeEnabled(enabled: Boolean)

    fun addListener(listener: Player.Listener)
    fun removeListener(listener: Player.Listener)

    fun setMediaItem(item: MediaItem)
    fun setMediaItems(items: List<MediaItem>, startIndex: Int, startPositionMs: Long)
    fun setMediaResource(resourceId: Int)
    fun play()
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long)
    fun setPlaybackSpeed(speed: Float)
    fun release()
}