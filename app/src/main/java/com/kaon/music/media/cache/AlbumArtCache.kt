package com.kaon.music.media.cache

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class AlbumArtCache(context: Context) {
    private val cacheDir = File(context.cacheDir, "artwork").apply { mkdirs() }
    private val lruIndex = ConcurrentHashMap<Long, Long>()
    private val MAX_SIZE = 100 * 1024 * 1024L // 100MB

    init {
        // Initialize LRU index from existing files
        cacheDir.listFiles()?.forEach { file ->
            val id = file.nameWithoutExtension.toLongOrNull()
            if (id != null) {
                lruIndex[id] = file.lastModified()
            }
        }
    }

    suspend fun get(albumId: Long): File? = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "$albumId.webp")
        if (file.exists()) {
            val time = System.currentTimeMillis()
            lruIndex[albumId] = time
            file.setLastModified(time)
            file
        } else {
            null
        }
    }

    suspend fun put(albumId: Long, bytes: ByteArray): File? = withContext(Dispatchers.IO) {
        try {
            val file = File(cacheDir, "$albumId.webp")
            FileOutputStream(file).use { it.write(bytes) }
            val time = System.currentTimeMillis()
            lruIndex[albumId] = time
            file.setLastModified(time)
            evictIfNeeded()
            file
        } catch (e: Exception) {
            null
        }
    }

    suspend fun put(albumId: Long, bitmap: Bitmap): File? = withContext(Dispatchers.IO) {
        try {
            val file = File(cacheDir, "$albumId.webp")
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            val time = System.currentTimeMillis()
            lruIndex[albumId] = time
            file.setLastModified(time)
            evictIfNeeded()
            file
        } catch (e: Exception) {
            null
        }
    }

    private fun evictIfNeeded() {
        var currentSize = cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
        if (currentSize <= MAX_SIZE) return

        val files = cacheDir.listFiles()?.toList() ?: return
        val sortedFiles = files.sortedBy { lruIndex[it.nameWithoutExtension.toLongOrNull() ?: 0L] ?: 0L }

        for (file in sortedFiles) {
            val size = file.length()
            if (file.delete()) {
                currentSize -= size
                lruIndex.remove(file.nameWithoutExtension.toLongOrNull() ?: 0L)
            }
            if (currentSize <= MAX_SIZE) break
        }
    }
}
