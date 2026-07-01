package com.kaon.music

import android.app.Application
import com.kaon.music.core.kernel.impl.KaonKernel

class KaonApplication : Application() {
    lateinit var kernel: KaonKernel
        private set

    override fun onCreate() {
        super.onCreate()
        kernel = KaonKernel(this)
    }
}
