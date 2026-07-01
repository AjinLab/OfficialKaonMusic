package com.kaon.music.plugins.defaultui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kaon.music.core.kernel.Kernel
import com.kaon.music.core.kernel.get
import com.kaon.music.core.playback.PlayerController
import com.kaon.music.media.library.LibraryController
import com.kaon.music.media.model.Song
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(private val kernel: Kernel) : ViewModel() {
    private val libraryController = kernel.get<LibraryController>()
    private val playerController = kernel.get<PlayerController>()
    
    val songs = libraryController.songs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val artists = libraryController.artists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val albums = libraryController.albums.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    fun album(id: Long) = libraryController.album(id)
    fun artist(id: Long) = libraryController.artist(id)
    
    fun albumDetail(albumId: Long) = libraryController.albumDetail(albumId)
    fun artistDetail(artistId: Long) = libraryController.artistDetail(artistId)
    
    fun albumSongs(albumId: Long) = libraryController.albumSongs(albumId)
    fun artistAlbums(artistId: Long) = libraryController.artistAlbums(artistId)
    fun artistSongs(artistId: Long) = libraryController.artistSongs(artistId)
    
    fun refresh() {
        viewModelScope.launch {
            libraryController.refresh()
        }
    }
    
    fun playSong(song: Song) {
        val currentSongs = songs.value
        val index = currentSongs.indexOfFirst { it.id == song.id }
        if (index != -1) {
            playerController.setQueue(currentSongs, index)
            playerController.play(index)
        }
    }

    fun playSongs(queue: List<Song>, startIndex: Int = 0) {
        if (queue.isNotEmpty() && startIndex in queue.indices) {
            playerController.setQueue(queue, startIndex)
            playerController.play(startIndex)
        }
    }

    class Factory(private val kernel: Kernel) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LibraryViewModel(kernel) as T
        }
    }
}
