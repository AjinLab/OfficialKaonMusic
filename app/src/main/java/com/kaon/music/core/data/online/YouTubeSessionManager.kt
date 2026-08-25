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
import kotlinx.coroutines.flow.map
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
        // Set locale from device settings
        val deviceLocale = Locale.getDefault()
        YouTube.locale = YouTubeLocale(
            gl = deviceLocale.country.ifBlank { "US" },
            hl = deviceLocale.language.ifBlank { "en" }
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
            } catch (e: Exception) {
                Timber.w(e, "Failed to load saved YouTube session preferences")
            }
        }
    }

    fun saveVisitorData(visitorData: String) {
        YouTube.visitorData = visitorData
        scope.launch {
            context.dataStore.edit { it[visitorDataKey] = visitorData }
        }
    }

    fun saveCookie(cookie: String?) {
        YouTube.cookie = cookie
        scope.launch {
            context.dataStore.edit {
                if (cookie != null) {
                    it[cookieKey] = cookie
                } else {
                    it.remove(cookieKey)
                }
            }
        }
    }
}
