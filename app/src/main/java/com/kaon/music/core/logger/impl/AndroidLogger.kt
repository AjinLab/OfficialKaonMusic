package com.kaon.music.core.logger.impl

import android.util.Log
import com.kaon.music.core.logger.Logger

class AndroidLogger : Logger {

    override fun debug(tag: String, msg: String) {
        Log.d(tag, msg)
    }

    override fun info(tag: String, msg: String) {
        Log.i(tag, msg)
    }

    override fun warn(tag: String, msg: String) {
        Log.w(tag, msg)
    }

    override fun error(tag: String, msg: String) {
        Log.e(tag, msg)
    }
}
