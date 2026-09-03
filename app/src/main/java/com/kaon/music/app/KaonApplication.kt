package com.kaon.music.app

import android.app.Application
import com.kaon.music.BuildConfig
import com.kaon.music.app.di.AppContainer
import com.kaon.music.core.logging.ReleaseTree
import com.kaon.music.core.online.YouTubeStreamExtractor
import com.kaon.music.core.playback.YouTubeStreamResolver
import kotlinx.coroutines.launch
import timber.log.Timber

class KaonApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        // ARCHITECTURE.md §5.3: a DebugTree in release publishes every DEBUG statement — including
        // token material from the extraction layer — to logcat.
        Timber.plant(if (BuildConfig.DEBUG) Timber.DebugTree() else ReleaseTree())

        container = AppContainer(this)

        // Context handoff only: both calls just store an application reference (ARCHITECTURE.md §5.4
        // — no file I/O, JSON parsing, or network in onCreate).
        YouTubeStreamExtractor.initialize(this)
        YouTubeStreamResolver.attachContext(this)

        // Session bootstrap and cipher warm-up are network-bound, so they run off the first frame.
        // Both are best-effort: extraction tolerates a cold cipher and a null visitorData.
        container.appScope.launch {
            container.youtubeSessionManager
            YouTubeStreamExtractor.prewarm()
        }
    }
}
