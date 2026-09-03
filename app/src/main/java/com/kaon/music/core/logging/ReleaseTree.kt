package com.kaon.music.core.logging

import android.util.Log
import timber.log.Timber

/**
 * Release logging tree: WARN and above only.
 *
 * ARCHITECTURE.md §5.3. `Timber.DebugTree` in a release build sends every DEBUG statement to
 * logcat, where any app with log access can read it. R8 also strips `Timber.v`/`Timber.d` call
 * sites in release via `-assumenosideeffects`, so this tree is the second line of defence for
 * anything that reaches Timber through a different path.
 */
class ReleaseTree : Timber.Tree() {

    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.WARN

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < Log.WARN) return
        if (t != null) {
            Log.println(priority, tag ?: DEFAULT_TAG, "$message\n${Log.getStackTraceString(t)}")
        } else {
            Log.println(priority, tag ?: DEFAULT_TAG, message)
        }
    }

    private companion object {
        const val DEFAULT_TAG = "Kaon"
    }
}
