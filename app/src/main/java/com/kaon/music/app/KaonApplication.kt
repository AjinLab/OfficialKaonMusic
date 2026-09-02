package com.kaon.music.app

import android.app.Application
import com.kaon.music.app.di.AppContainer
import timber.log.Timber

class KaonApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize Timber tagged logging (ARCHITECTURE_ATTRIBUTED.md §17 / k3.md D9)
        Timber.plant(Timber.DebugTree())

        // 2. Initialize application dependency container
        container = AppContainer(this)

        // Bootstrap the shared InnerTube session before any screen or playback service makes a
        // request. The service reuses this instance instead of creating a second session loader.
        container.youtubeSessionManager

        // 3. Initialize YouTube Cipher deobfuscator
        com.kaon.music.core.online.cipher.CipherDeobfuscator.initialize(this)
    }
}
