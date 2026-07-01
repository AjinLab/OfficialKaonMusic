package com.kaon.music.media.artwork

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Thread-safe cache for artwork palette colors, keyed by a stable artwork identifier
 * (artwork file path or hash — not just albumId, since editions/discs may differ).
 *
 * Palette extraction runs on IO and results are cached in memory.
 * LRU eviction is synchronized to prevent concurrent modification.
 */
class ArtworkPaletteCache(private val maxSize: Int = 200) {

    private data class Entry(val colors: ArtworkColors, val accessTime: Long)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val evictionLock = ReentrantLock()

    /**
     * Returns cached colors for [artworkKey], or extracts from the artwork file at [artworkFile].
     * The [artworkKey] should be a stable identifier like the artwork file path or artwork hash.
     */
    suspend fun getOrExtract(artworkKey: String, artworkFile: File): ArtworkColors? {
        // Cache hit
        cache[artworkKey]?.let { entry ->
            cache[artworkKey] = entry.copy(accessTime = System.currentTimeMillis())
            return entry.colors
        }

        // Cache miss — extract on IO
        return withContext(Dispatchers.IO) {
            try {
                val bitmap = BitmapFactory.decodeFile(artworkFile.absolutePath) ?: return@withContext null
                val colors = extractColors(bitmap)
                bitmap.recycle()

                if (colors != null) {
                    cache[artworkKey] = Entry(colors, System.currentTimeMillis())
                    evictIfNeeded()
                }
                colors
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Extracts palette colors from an already-loaded bitmap.
     */
    suspend fun getOrExtract(artworkKey: String, bitmap: Bitmap): ArtworkColors? {
        cache[artworkKey]?.let { entry ->
            cache[artworkKey] = entry.copy(accessTime = System.currentTimeMillis())
            return entry.colors
        }

        return withContext(Dispatchers.IO) {
            try {
                val colors = extractColors(bitmap)
                if (colors != null) {
                    cache[artworkKey] = Entry(colors, System.currentTimeMillis())
                    evictIfNeeded()
                }
                colors
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun extractColors(bitmap: Bitmap): ArtworkColors? {
        val palette = Palette.from(bitmap).generate()

        val dominantSwatch = palette.dominantSwatch
        val vibrantSwatch = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.darkVibrantSwatch
        val mutedSwatch = palette.mutedSwatch ?: palette.lightMutedSwatch ?: palette.darkMutedSwatch

        // Use dominant as the base; fall back to vibrant or muted if absent
        val dominantColor = dominantSwatch?.rgb
            ?: vibrantSwatch?.rgb
            ?: mutedSwatch?.rgb
            ?: return null

        val vibrantColor = vibrantSwatch?.rgb ?: dominantColor
        val mutedColor = mutedSwatch?.rgb ?: dominantColor

        // Compute onDominant: light text on dark background, dark text on light background
        val luminance = ColorUtils.calculateLuminance(dominantColor)
        val onDominant = if (luminance > 0.5) {
            Color.argb(222, 28, 27, 27)   // near-black for light backgrounds
        } else {
            Color.argb(222, 255, 255, 255) // near-white for dark backgrounds
        }

        return ArtworkColors(
            dominant = dominantColor,
            vibrant = vibrantColor,
            muted = mutedColor,
            onDominant = onDominant
        )
    }

    /**
     * LRU eviction — synchronized to prevent concurrent modification during eviction.
     */
    private fun evictIfNeeded() {
        if (cache.size <= maxSize) return

        evictionLock.withLock {
            if (cache.size <= maxSize) return // double-check after acquiring lock

            val sortedEntries = cache.entries
                .sortedBy { it.value.accessTime }
                .take(cache.size - maxSize)

            for (entry in sortedEntries) {
                cache.remove(entry.key)
            }
        }
    }

    fun clear() {
        cache.clear()
    }
}
