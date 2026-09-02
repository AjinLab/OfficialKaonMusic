package com.kaon.music.core.data.online

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale

private val Context.dataStore by preferencesDataStore(name = "youtube_session_prefs")

/**
 * Manages InnerTube session bootstrap, locale matching, and visitor tokens.
 */
class YouTubeSessionManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val visitorDataKey = stringPreferencesKey("yt_visitor_data")
    private val cookieKey = stringPreferencesKey("yt_cookie")

    init {
        initializeSession()
    }

    private fun initializeSession() {
        // InnerTube expects the region and a BCP-47 language tag, not just the language code.
        val deviceLocale = Locale.getDefault()
        YouTube.locale = YouTubeLocale(
            gl = deviceLocale.country.ifBlank { "US" },
            hl = deviceLocale.toLanguageTag().ifBlank { "en-US" },
        )

        scope.launch {
            try {
                val prefs = context.dataStore.data.firstOrNull()
                val savedVisitorData = prefs?.get(visitorDataKey)
                val savedCookie = prefs?.get(cookieKey)

                if (!savedVisitorData.isNullOrBlank()) {
                    YouTube.visitorData = savedVisitorData
                }
                if (!savedCookie.isNullOrBlank()) {
                    YouTube.cookie = savedCookie
                }

                // A visitor token is required by web clients and by the web PO-token flow. Fetch
                // one during application startup when there is no persisted token so the first
                // playback/search request does not race the bootstrap.
                if (savedVisitorData.isNullOrBlank()) {
                    YouTube.visitorData()
                        .onSuccess(::saveVisitorData)
                        .onFailure { error ->
                            Timber.w(error, "Failed to bootstrap YouTube visitor data")
                        }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to load saved YouTube session preferences")
            }
        }
    }

    fun saveVisitorData(visitorData: String) {
        val normalized = visitorData.trim()
        if (normalized.isBlank()) return

        YouTube.visitorData = normalized
        scope.launch {
            context.dataStore.edit { it[visitorDataKey] = normalized }
        }
    }

    fun saveCookie(cookie: String?) {
        val normalized = cookie?.trim()?.takeIf { it.isNotBlank() }
        YouTube.cookie = normalized
        scope.launch {
            context.dataStore.edit {
                if (normalized != null) {
                    it[cookieKey] = normalized
                } else {
                    it.remove(cookieKey)
                }
            }
        }
    }
}
