package com.kaon.music.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kaon.music.core.extraction.model.AudioQuality
import com.kaon.music.core.extraction.model.AudioType
import com.kaon.music.core.model.SettingsController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kaon_settings")

data class UserSettings(
    val streamingQuality: AudioQuality = AudioQuality.AUTO,
    val preferredAudioType: AudioType = AudioType.AUTO,
    val themeMode: String = "DARK",
    val accentColor: String = "CORAL",
    val minDurationSeconds: Int = 5,
    val showFormatBadges: Boolean = true,
    val showLosslessBadges: Boolean = true,
    val preResolveNextTracks: Boolean = true,
    val wifiOnlyStreaming: Boolean = false,
    val recordHistory: Boolean = true,
    val pauseOnFocusLoss: Boolean = true,
    val skipSilence: Boolean = false,
    val crossfadeSeconds: Int = 0,
    val lastFmApiKey: String = "",
    val fanartTvApiKey: String = "",
    val discogsToken: String = ""
)

class SettingsRepository(private val context: Context) : SettingsController {

    private object PreferencesKeys {
        val STREAMING_QUALITY = stringPreferencesKey("streaming_quality")
        val PREFERRED_AUDIO_TYPE = stringPreferencesKey("preferred_audio_type")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val MIN_DURATION_SECONDS = intPreferencesKey("min_duration_seconds")
        val SHOW_FORMAT_BADGES = booleanPreferencesKey("show_format_badges")
        val SHOW_LOSSLESS_BADGES = booleanPreferencesKey("show_lossless_badges")
        val PRE_RESOLVE_NEXT_TRACKS = booleanPreferencesKey("pre_resolve_next_tracks")
        val WIFI_ONLY_STREAMING = booleanPreferencesKey("wifi_only_streaming")
        val RECORD_HISTORY = booleanPreferencesKey("record_history")
        val PAUSE_ON_FOCUS_LOSS = booleanPreferencesKey("pause_on_focus_loss")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
        val LAST_FM_API_KEY = stringPreferencesKey("last_fm_api_key")
        val FANART_TV_API_KEY = stringPreferencesKey("fanart_tv_api_key")
        val DISCOGS_TOKEN = stringPreferencesKey("discogs_token")
    }


    override val userSettingsFlow: Flow<UserSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val qualityStr = preferences[PreferencesKeys.STREAMING_QUALITY] ?: AudioQuality.AUTO.name
            val streamingQuality = try {
                AudioQuality.valueOf(qualityStr)
            } catch (e: Exception) {
                AudioQuality.AUTO
            }

            val typeStr = preferences[PreferencesKeys.PREFERRED_AUDIO_TYPE] ?: AudioType.AUTO.name
            val preferredAudioType = try {
                AudioType.valueOf(typeStr)
            } catch (e: Exception) {
                AudioType.AUTO
            }

            UserSettings(
                streamingQuality = streamingQuality,
                preferredAudioType = preferredAudioType,
                themeMode = preferences[PreferencesKeys.THEME_MODE] ?: "DARK",
                accentColor = preferences[PreferencesKeys.ACCENT_COLOR] ?: "CORAL",
                minDurationSeconds = preferences[PreferencesKeys.MIN_DURATION_SECONDS] ?: 5,
                showFormatBadges = preferences[PreferencesKeys.SHOW_FORMAT_BADGES] ?: true,
                showLosslessBadges = preferences[PreferencesKeys.SHOW_LOSSLESS_BADGES] ?: true,
                preResolveNextTracks = preferences[PreferencesKeys.PRE_RESOLVE_NEXT_TRACKS] ?: true,
                wifiOnlyStreaming = preferences[PreferencesKeys.WIFI_ONLY_STREAMING] ?: false,
                recordHistory = preferences[PreferencesKeys.RECORD_HISTORY] ?: true,
                pauseOnFocusLoss = preferences[PreferencesKeys.PAUSE_ON_FOCUS_LOSS] ?: true,
                skipSilence = preferences[PreferencesKeys.SKIP_SILENCE] ?: false,
                crossfadeSeconds = preferences[PreferencesKeys.CROSSFADE_SECONDS] ?: 0,
                lastFmApiKey = preferences[PreferencesKeys.LAST_FM_API_KEY] ?: "",
                fanartTvApiKey = preferences[PreferencesKeys.FANART_TV_API_KEY] ?: "",
                discogsToken = preferences[PreferencesKeys.DISCOGS_TOKEN] ?: ""
            )
        }

    override suspend fun setStreamingQuality(quality: AudioQuality) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.STREAMING_QUALITY] = quality.name
        }
    }

    override suspend fun setPreferredAudioType(audioType: AudioType) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PREFERRED_AUDIO_TYPE] = audioType.name
        }
    }

    override suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode
        }
    }

    override suspend fun setAccentColor(color: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCENT_COLOR] = color
        }
    }

    override suspend fun setMinDurationSeconds(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MIN_DURATION_SECONDS] = seconds
        }
    }

    override suspend fun setShowFormatBadges(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_FORMAT_BADGES] = enabled
        }
    }

    override suspend fun setShowLosslessBadges(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_LOSSLESS_BADGES] = enabled
        }
    }

    override suspend fun setPreResolveNextTracks(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PRE_RESOLVE_NEXT_TRACKS] = enabled
        }
    }

    override suspend fun setWifiOnlyStreaming(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WIFI_ONLY_STREAMING] = enabled
        }
    }

    override suspend fun setRecordHistory(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.RECORD_HISTORY] = enabled
        }
    }

    override suspend fun setPauseOnFocusLoss(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PAUSE_ON_FOCUS_LOSS] = enabled
        }
    }

    override suspend fun setSkipSilence(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SKIP_SILENCE] = enabled
        }
    }

    override suspend fun setCrossfadeSeconds(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CROSSFADE_SECONDS] = seconds
        }
    }

    override suspend fun setLastFmApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_FM_API_KEY] = key.trim()
        }
    }

    override suspend fun setFanartTvApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FANART_TV_API_KEY] = key.trim()
        }
    }

    override suspend fun setDiscogsToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DISCOGS_TOKEN] = token.trim()
        }
    }

    override val settingsFlow: Flow<UserSettings>
        get() = userSettingsFlow

    override suspend fun updateSettings(transform: (UserSettings) -> UserSettings) {
        val current = userSettingsFlow.first()
        val updated = transform(current)
        setStreamingQuality(updated.streamingQuality)
        setPreferredAudioType(updated.preferredAudioType)
        setThemeMode(updated.themeMode)
        setAccentColor(updated.accentColor)
        setMinDurationSeconds(updated.minDurationSeconds)
        setShowFormatBadges(updated.showFormatBadges)
        setShowLosslessBadges(updated.showLosslessBadges)
        setPreResolveNextTracks(updated.preResolveNextTracks)
        setWifiOnlyStreaming(updated.wifiOnlyStreaming)
        setRecordHistory(updated.recordHistory)
        setPauseOnFocusLoss(updated.pauseOnFocusLoss)
        setSkipSilence(updated.skipSilence)
        setCrossfadeSeconds(updated.crossfadeSeconds)
        setMetadataApiKeys(updated.lastFmApiKey, updated.fanartTvApiKey, updated.discogsToken)
    }

    override suspend fun setMetadataApiKeys(lastFmKey: String, fanartTvKey: String, discogsToken: String) {
        setLastFmApiKey(lastFmKey)
        setFanartTvApiKey(fanartTvKey)
        setDiscogsToken(discogsToken)
    }
}

