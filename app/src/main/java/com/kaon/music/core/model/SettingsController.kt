package com.kaon.music.core.model

import com.kaon.music.core.extraction.model.AudioQuality
import com.kaon.music.core.extraction.model.AudioType
import kotlinx.coroutines.flow.Flow

typealias UserSettings = com.kaon.music.core.data.repository.UserSettings

/**
 * Capability controller exposing application and user preference management.
 */
interface SettingsController {
    val settingsFlow: Flow<UserSettings>
    val userSettingsFlow: Flow<UserSettings>
        get() = settingsFlow

    suspend fun updateSettings(transform: (UserSettings) -> UserSettings)
    suspend fun setThemeMode(mode: String)
    suspend fun setAccentColor(color: String)
    suspend fun setStreamingQuality(quality: AudioQuality)
    suspend fun setPreferredAudioType(type: AudioType)
    suspend fun setWifiOnlyStreaming(enabled: Boolean)
    suspend fun setPreResolveNextTracks(enabled: Boolean)
    suspend fun setMinDurationSeconds(seconds: Int)
    suspend fun setShowFormatBadges(show: Boolean)
    suspend fun setShowLosslessBadges(show: Boolean)
    suspend fun setRecordHistory(record: Boolean)
    suspend fun setPauseOnFocusLoss(pause: Boolean)
    suspend fun setSkipSilence(skip: Boolean)
    suspend fun setCrossfadeSeconds(seconds: Int)
    suspend fun setLastFmApiKey(key: String)
    suspend fun setFanartTvApiKey(key: String)
    suspend fun setDiscogsToken(token: String)
    suspend fun setMetadataApiKeys(lastFmKey: String, fanartTvKey: String, discogsToken: String)
    suspend fun clearStreamCache()
}
