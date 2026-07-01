package com.kaon.music.plugins.defaultui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import com.kaon.music.core.kernel.Kernel
import com.kaon.music.core.kernel.get
import com.kaon.music.core.playback.PlayerController
import com.kaon.music.media.library.LibraryController
import com.kaon.music.media.model.Song

class ArtistViewModel(
    savedStateHandle: SavedStateHandle,
    kernel: Kernel
) : ViewModel() {
    private val artistId: Long = savedStateHandle.get<Long>("artistId") ?: -1L
    private val libraryController = kernel.get<LibraryController>()
    private val playerController = kernel.get<PlayerController>()

    val artistDetail = libraryController.artistDetail(artistId)

    fun playSongs(queue: List<Song>, startIndex: Int = 0) {
        if (queue.isNotEmpty() && startIndex in queue.indices) {
            playerController.setQueue(queue, startIndex)
            playerController.play(startIndex)
        }
    }

    class Factory(
        private val kernel: Kernel
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T {
            val savedStateHandle = extras.createSavedStateHandle()
            return ArtistViewModel(savedStateHandle, kernel) as T
        }
    }
}
