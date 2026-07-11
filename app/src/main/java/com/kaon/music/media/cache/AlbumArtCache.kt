package com.kaon.music.media.cache

import android.content.Context
import android.graphics.Bitmap
import com.kaon.music.media.artwork.ArtworkColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class AlbumArtCache(context: Context) {
    private val cacheDir = File(context.cacheDir, "artwork").apply { mkdirs() }
    private val lruIndex = ConcurrentHashMap<String, Long>()
    private val MAX_SIZE = 100 * 1024 * 1024L // 100MB
    private val initMutex = Mutex()
    private var isInitialized = false

    private suspend fun ensureInitialized() = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext
        initMutex.withLock {
            if (!isInitialized) {
                cacheDir.listFiles()?.forEach { file ->
                    val key = file.nameWithoutExtension
                    if (!file.name.contains(".palette_v1")) {
                        lruIndex[key] = file.lastModified()
                    }
                }
                isInitialized = true
            }
        }
    }

    suspend fun get(key: String): File? = withContext(Dispatchers.IO) {
        ensureInitialized()
        val file = File(cacheDir, "$key.webp")
        if (file.exists()) {
            val time = System.currentTimeMillis()
            lruIndex[key] = time
            file.setLastModified(time)
            file
        } else {
            null
        }
    }

    suspend fun put(key: String, bytes: ByteArray): File? = withContext(Dispatchers.IO) {
        ensureInitialized()
        try {
            val file = File(cacheDir, "$key.webp")
            FileOutputStream(file).use { it.write(bytes) }
            val time = System.currentTimeMillis()
            lruIndex[key] = time
            file.setLastModified(time)
            evictIfNeeded()
            file
        } catch (e: Exception) {
            null
        }
    }

    suspend fun put(key: String, bitmap: Bitmap): File? = withContext(Dispatchers.IO) {
        ensureInitialized()
        try {
            val file = File(cacheDir, "$key.webp")
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            val time = System.currentTimeMillis()
            lruIndex[key] = time
            file.setLastModified(time)
            evictIfNeeded()
            file
        } catch (e: Exception) {
            null
        }
    }

    fun putPalette(key: String, colors: ArtworkColors) {
        try {
            val file = File(cacheDir, "$key.palette_v1")
            file.writeText("${colors.dominant},${colors.vibrant},${colors.muted},${colors.onDominant}")
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun getPalette(key: String): ArtworkColors? {
        val file = File(cacheDir, "$key.palette_v1")
        if (!file.exists()) return null
        return try {
            val parts = file.readText().split(",")
            if (parts.size >= 4) {
                ArtworkColors(
                    dominant = parts[0].toInt(),
                    vibrant = parts[1].toInt(),
                    muted = parts[2].toInt(),
                    onDominant = parts[3].toInt()
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun evictIfNeeded() {
        var currentSize = cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
        if (currentSize <= MAX_SIZE) return

        val files = cacheDir.listFiles()?.toList() ?: return
        val sortedFiles = files.sortedBy { lruIndex[it.nameWithoutExtension] ?: 0L }

        for (file in sortedFiles) {
            val size = file.length()
            val key = file.nameWithoutExtension
            if (file.delete()) {
                currentSize -= size
                lruIndex.remove(key)
                // Also delete palette file if it exists
                val paletteFile = File(cacheDir, "$key.palette_v1")
                if (paletteFile.exists()) {
                    currentSize -= paletteFile.length()
                    paletteFile.delete()
                }
            }
            if (currentSize <= MAX_SIZE) break
        }
    }
}
