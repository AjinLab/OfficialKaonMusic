package com.kaon.music.plugins.defaultui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kaon.music.core.kernel.Kernel
import com.kaon.music.core.kernel.get
import com.kaon.music.core.playback.PlayerController
import com.kaon.music.media.model.Song

class QueueViewModel(private val kernel: Kernel) : ViewModel() {
    private val playerController = kernel.get<PlayerController>()
    
    val playbackState = playerController.playbackState
    
    fun playFromQueue(index: Int) {
        playerController.play(index)
    }

    class Factory(private val kernel: Kernel) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return QueueViewModel(kernel) as T
        }
    }
}
