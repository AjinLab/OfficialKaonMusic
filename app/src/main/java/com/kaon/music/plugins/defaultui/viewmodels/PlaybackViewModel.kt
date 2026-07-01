package com.kaon.music.plugins.defaultui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kaon.music.core.kernel.Kernel
import com.kaon.music.core.kernel.get
import com.kaon.music.core.playback.PlayerController

class PlaybackViewModel(private val kernel: Kernel) : ViewModel() {
    private val playerController = kernel.get<PlayerController>()
    
    val playbackState = playerController.playbackState
    
    fun togglePlayback() {
        playerController.togglePlayback()
    }
    
    fun next() {
        playerController.next()
    }
    
    fun previous() {
        playerController.previous()
    }
    
    fun seekTo(position: Long) {
        playerController.seekTo(position)
    }

    fun seekForward() {
        playerController.seekForward()
    }

    fun seekBackward() {
        playerController.seekBackward()
    }

    fun setPlaybackSpeed(speed: Float) {
        playerController.setPlaybackSpeed(speed)
    }

    fun setSleepTimer(minutes: Int) {
        playerController.setSleepTimer(minutes)
    }

    fun cancelSleepTimer() {
        playerController.cancelSleepTimer()
    }

    fun toggleShuffle() {
        val currentShuffle = playerController.currentQueue().value.shuffle
        playerController.setShuffle(!currentShuffle)
    }

    fun cycleRepeatMode() {
        val currentMode = playerController.currentQueue().value.repeatMode
        val nextMode = when (currentMode) {
            com.kaon.music.media.manager.RepeatMode.OFF -> com.kaon.music.media.manager.RepeatMode.ALL
            com.kaon.music.media.manager.RepeatMode.ALL -> com.kaon.music.media.manager.RepeatMode.ONE
            com.kaon.music.media.manager.RepeatMode.ONE -> com.kaon.music.media.manager.RepeatMode.OFF
        }
        playerController.setRepeatMode(nextMode)
    }

    class Factory(private val kernel: Kernel) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlaybackViewModel(kernel) as T
        }
    }
}
