package com.kaon.music.media.library.sync

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.time.Clock
import java.time.Instant

class MediaStoreGenerationTracker(
    private val context: Context,
    private val clock: Clock
) : GenerationTracker {

    override fun currentGeneration(): GenerationSnapshot {
        val volume = MediaStore.VOLUME_EXTERNAL
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val gen = MediaStore.getGeneration(context, volume)
                GenerationSnapshot(gen, volume, clock.instant())
            } catch (e: Exception) {
                // Fallback for unexpected platform/resolver exceptions
                GenerationSnapshot(0L, volume, clock.instant())
            }
        } else {
            GenerationSnapshot(0L, volume, clock.instant())
        }
    }
}
