package com.kaon.music.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaon.music.core.data.online.YouTubeSessionManager
import com.kaon.music.core.data.repository.HistoryRepository
import com.kaon.music.core.data.repository.SettingsRepository
import com.kaon.music.core.data.repository.TrackRepository
import com.kaon.music.core.data.repository.UserSettings
import com.kaon.music.core.online.AudioQuality
import com.kaon.music.core.online.AudioType
import com.kaon.music.core.playback.YouTubeStreamResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val selectedSection: SettingsSection? = null,
    val isSyncing: Boolean = false,
    val userMessage: String? = null
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val trackRepository: TrackRepository,
    private val historyRepository: HistoryRepository? = null,
    private val youtubeSessionManager: YouTubeSessionManager? = null
) : ViewModel() {

    private val _selectedSection = MutableStateFlow<SettingsSection?>(null)
    val selectedSection: StateFlow<SettingsSection?> = _selectedSection.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    val settings: StateFlow<UserSettings> = settingsRepository.userSettingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSettings()
        )

    fun navigateToSection(section: SettingsSection?) {
        _selectedSection.value = section
    }

    fun dismissUserMessage() {
        _userMessage.value = null
    }

    fun setStreamingQuality(quality: AudioQuality) {
        viewModelScope.launch {
            settingsRepository.setStreamingQuality(quality)
        }
    }

    fun setPreferredAudioType(audioType: AudioType) {
        viewModelScope.launch {
            settingsRepository.setPreferredAudioType(audioType)
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setAccentColor(color: String) {
        viewModelScope.launch {
            settingsRepository.setAccentColor(color)
        }
    }

    fun setMinDurationSeconds(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.setMinDurationSeconds(seconds)
            try {
                trackRepository.syncLibrary(minDurationMs = seconds * 1000L)
            } catch (e: Exception) {
                // Non-fatal background sync
            }
        }
    }

    fun setShowFormatBadges(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowFormatBadges(enabled)
        }
    }

    fun setShowLosslessBadges(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowLosslessBadges(enabled)
        }
    }

    fun setPreResolveNextTracks(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPreResolveNextTracks(enabled)
        }
    }

    fun setWifiOnlyStreaming(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setWifiOnlyStreaming(enabled)
        }
    }

    fun setRecordHistory(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRecordHistory(enabled)
        }
    }

    fun setPauseOnFocusLoss(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPauseOnFocusLoss(enabled)
        }
    }

    fun setSkipSilence(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSkipSilence(enabled)
        }
    }

    fun setCrossfadeSeconds(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.setCrossfadeSeconds(seconds)
        }
    }

    fun setLastFmApiKey(key: String) {
        viewModelScope.launch {
            settingsRepository.setLastFmApiKey(key)
        }
    }

    fun setFanartTvApiKey(key: String) {
        viewModelScope.launch {
            settingsRepository.setFanartTvApiKey(key)
        }
    }

    fun setDiscogsToken(token: String) {
        viewModelScope.launch {
            settingsRepository.setDiscogsToken(token)
        }
    }

    fun triggerRescan() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val minDurationMs = settings.value.minDurationSeconds * 1000L
                val result = trackRepository.syncLibrary(minDurationMs = minDurationMs)
                _userMessage.value = "Library synced: ${result.totalDiscovered} tracks found (+${result.added} new)"
            } catch (e: Exception) {
                _userMessage.value = "Sync failed: ${e.message}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun clearListeningHistory() {
        viewModelScope.launch {
            try {
                historyRepository?.clearAllHistory()
                _userMessage.value = "Listening history cleared"
            } catch (e: Exception) {
                _userMessage.value = "Failed to clear history: ${e.message}"
            }
        }
    }

    fun clearStreamCache() {
        viewModelScope.launch {
            try {
                YouTubeStreamResolver.clearCache()
                _userMessage.value = "Stream resolver cache cleared"
            } catch (e: Exception) {
                _userMessage.value = "Cache clear failed: ${e.message}"
            }
        }
    }
}
