package com.kaon.music.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaon.music.core.data.online.YouTubeSessionManager
import com.kaon.music.core.model.ErrorMapper
import com.kaon.music.core.model.LibraryController
import com.kaon.music.core.model.SettingsController
import com.kaon.music.core.model.UserMessageBus
import com.kaon.music.core.model.UserSettings
import com.kaon.music.core.extraction.model.AudioQuality
import com.kaon.music.core.extraction.model.AudioType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val selectedSection: SettingsSection? = null,
    val isSyncing: Boolean = false
)

class SettingsViewModel(
    private val settingsController: SettingsController,
    private val libraryController: LibraryController,
    private val youtubeSessionManager: YouTubeSessionManager? = null,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val userMessageBus: UserMessageBus? = null
) : ViewModel() {

    // Restored so rotating inside a settings subpage does not bounce the user back to the root list.
    private val _selectedSection = MutableStateFlow(
        savedStateHandle.get<String>(KEY_SECTION)?.let { runCatching { SettingsSection.valueOf(it) }.getOrNull() }
    )
    val selectedSection: StateFlow<SettingsSection?> = _selectedSection.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()


    val settings: StateFlow<UserSettings> = settingsController.userSettingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSettings()
        )

    fun navigateToSection(section: SettingsSection?) {
        _selectedSection.value = section
        savedStateHandle[KEY_SECTION] = section?.name
    }

    fun dismissUserMessage() {
            }

    fun setStreamingQuality(quality: AudioQuality) {
        viewModelScope.launch {
            settingsController.setStreamingQuality(quality)
        }
    }

    fun setPreferredAudioType(audioType: AudioType) {
        viewModelScope.launch {
            settingsController.setPreferredAudioType(audioType)
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            settingsController.setThemeMode(mode)
        }
    }

    fun setAccentColor(color: String) {
        viewModelScope.launch {
            settingsController.setAccentColor(color)
        }
    }

    fun setMinDurationSeconds(seconds: Int) {
        viewModelScope.launch {
            settingsController.setMinDurationSeconds(seconds)
            try {
                libraryController.syncLibrary(minDurationMs = seconds * 1000L)
            } catch (e: Exception) {
                // Non-fatal background sync
            }
        }
    }

    fun setShowFormatBadges(enabled: Boolean) {
        viewModelScope.launch {
            settingsController.setShowFormatBadges(enabled)
        }
    }

    fun setShowLosslessBadges(enabled: Boolean) {
        viewModelScope.launch {
            settingsController.setShowLosslessBadges(enabled)
        }
    }

    fun setPreResolveNextTracks(enabled: Boolean) {
        viewModelScope.launch {
            settingsController.setPreResolveNextTracks(enabled)
        }
    }

    fun setWifiOnlyStreaming(enabled: Boolean) {
        viewModelScope.launch {
            settingsController.setWifiOnlyStreaming(enabled)
        }
    }

    fun setRecordHistory(enabled: Boolean) {
        viewModelScope.launch {
            settingsController.setRecordHistory(enabled)
        }
    }

    fun setPauseOnFocusLoss(enabled: Boolean) {
        viewModelScope.launch {
            settingsController.setPauseOnFocusLoss(enabled)
        }
    }

    fun setSkipSilence(enabled: Boolean) {
        viewModelScope.launch {
            settingsController.setSkipSilence(enabled)
        }
    }

    fun setCrossfadeSeconds(seconds: Int) {
        viewModelScope.launch {
            settingsController.setCrossfadeSeconds(seconds)
        }
    }

    fun setLastFmApiKey(key: String) {
        viewModelScope.launch {
            settingsController.setLastFmApiKey(key)
        }
    }

    fun setFanartTvApiKey(key: String) {
        viewModelScope.launch {
            settingsController.setFanartTvApiKey(key)
        }
    }

    fun setDiscogsToken(token: String) {
        viewModelScope.launch {
            settingsController.setDiscogsToken(token)
        }
    }

    fun triggerRescan() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val minDurationMs = settings.value.minDurationSeconds * 1000L
                val result = libraryController.syncLibrary(minDurationMs = minDurationMs)
                userMessageBus?.post("Library synced: ${result.totalDiscovered} tracks found (+${result.added} new)")
            } catch (e: Exception) {
                userMessageBus?.post(ErrorMapper.fromThrowable(e))
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun clearListeningHistory() {
        viewModelScope.launch {
            try {
                libraryController.clearListeningHistory()
                userMessageBus?.post("Listening history cleared")
            } catch (e: Exception) {
                userMessageBus?.post("Failed to clear history")
            }
        }
    }

    fun clearStreamCache() {
        viewModelScope.launch {
            try {
                settingsController.clearStreamCache()
                userMessageBus?.post("Stream resolver cache cleared")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                userMessageBus?.post("Cache clear failed — try again")
            }
        }
    }

    private companion object {
        const val KEY_SECTION = "settings.section"
    }
}
