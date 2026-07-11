package com.kaon.music

import android.app.Application
import com.kaon.music.core.kernel.impl.KaonKernel
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.request.crossfade

class KaonApplication : Application(), SingletonImageLoader.Factory {
    lateinit var kernel: KaonKernel
        private set

    override fun onCreate() {
        super.onCreate()
        kernel = KaonKernel(this)
    }

    override fun newImageLoader(context: coil3.PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25) // 25% of memory
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50 * 1024 * 1024L) // 50MB
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
