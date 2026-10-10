package com.kaon.music.app.di

import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.kaon.music.feature.home.HomeViewModel
import com.kaon.music.feature.library.LibraryViewModel
import com.kaon.music.feature.player.PlayerViewModel
import com.kaon.music.feature.search.SearchViewModel
import com.kaon.music.feature.settings.SettingsViewModel

/**
 * ViewModel factory wiring [AppContainer] dependencies into saved-state-aware ViewModels.
 *
 * ARCHITECTURE.md §5.5: every ViewModel must come from a [androidx.lifecycle.ViewModelStore]. They
 * were previously created with `remember { }` inside `setContent`, so `onCleared()` was unreachable
 * and `viewModelScope` was never cancelled — each configuration change created a fresh set while the
 * previous set stayed reachable through PlaybackFacade's flow subscriber slots.
 */
@Suppress("DEPRECATION") // AbstractSavedStateViewModelFactory is the supported API on lifecycle 2.8.
class KaonViewModelFactory(
    private val container: AppContainer,
    owner: SavedStateRegistryOwner,
    defaultArgs: Bundle? = null
) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle
    ): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(
            libraryController = container.libraryController,
            playerController = container.playerController,
            savedStateHandle = handle
        ) as T

        modelClass.isAssignableFrom(SearchViewModel::class.java) -> SearchViewModel(
            libraryController = container.libraryController,
            playerController = container.playerController,
            networkConnectivityMonitor = container.networkMonitor,
            savedStateHandle = handle
        ) as T

        modelClass.isAssignableFrom(LibraryViewModel::class.java) -> LibraryViewModel(
            libraryController = container.libraryController,
            playerController = container.playerController,
            savedStateHandle = handle,
            userMessageBus = container.userMessageBus
        ) as T

        modelClass.isAssignableFrom(PlayerViewModel::class.java) -> PlayerViewModel(
            playerController = container.playerController,
            libraryController = container.libraryController,
            savedStateHandle = handle
        ) as T

        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(
            settingsController = container.settingsController,
            libraryController = container.libraryController,
            youtubeSessionManager = container.youtubeSessionManager,
            savedStateHandle = handle,
            userMessageBus = container.userMessageBus
        ) as T

        else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
