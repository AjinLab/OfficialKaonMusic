package com.kaon.music.plugins.defaultui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kaon.music.core.kernel.Kernel
import com.kaon.music.media.library.LibraryController
import com.kaon.music.media.search.SearchResults
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val kernel: Kernel
) : ViewModel() {

    private val library = kernel.get(LibraryController::class)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow(SearchResults(emptyList()))
    val results: StateFlow<SearchResults> = _results.asStateFlow()

    init {
        _query
            .debounce(150)
            .distinctUntilChanged()
            .mapLatest { q -> library.searchAll(q.trim()) }
            .onEach { res -> _results.value = res }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun playSong(index: Int) {
        val playerController = kernel.get(com.kaon.music.core.playback.PlayerController::class)
        // Just play this single song? Or enqueue the whole search result? 
        // We'll just play the song individually or play the list of songs in results.
        val songs = _results.value.items.filterIsInstance<com.kaon.music.media.search.SongResult>().map { it.song }
        playerController.setQueue(songs, index)
        playerController.play()
    }

    class Factory(private val kernel: Kernel) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SearchViewModel(kernel) as T
        }
    }
}
