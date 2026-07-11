package com.kaon.music.media.artwork

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.kaon.music.media.cache.AlbumArtCache
import com.kaon.music.media.model.Song
import com.kaon.music.media.services.MetadataProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

import com.kaon.music.core.diagnostics.DiagnosticsProvider
import com.kaon.music.core.diagnostics.DiagnosticValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

data class FailedDecode(
    val timestamp: Long,
    val reason: Throwable?
)

class ArtworkRepository(
    private val context: Context,
    private val diskCache: AlbumArtCache,
    private val metadataReader: MetadataProvider
) : DiagnosticsProvider {
    companion object {
        private val folderArtworkNames = setOf(
            "cover.jpg", "cover.jpeg", "cover.png",
            "folder.jpg", "folder.jpeg", "folder.png",
            "albumart.jpg", "albumart.jpeg", "albumart.png"
        )
    }

    override val id: String = "artwork"
    override val displayName: String = "Artwork Cache"

    private val totalRequests = AtomicLong(0)
    private val memoryHits = AtomicLong(0)
    private val memoryEvictions = AtomicLong(0)
    private val diskHits = AtomicLong(0)
    private val diskWrites = AtomicLong(0)
    private val diskReads = AtomicLong(0)
    private val negativeCacheHits = AtomicLong(0)
    private val failedDecodes = AtomicLong(0)
    private val decodeCount = AtomicLong(0)
    private val paletteCacheHits = AtomicLong(0)
    private val paletteExtractions = AtomicLong(0)
    private val pendingJobDeduplications = AtomicLong(0)
    private val totalDecodeTimeNs = AtomicLong(0)
    private val totalPaletteTimeNs = AtomicLong(0)
    private val lastDecodeTimestamp = AtomicLong(0)

    private val _diagnostics = MutableStateFlow<Map<String, DiagnosticValue>>(emptyMap())
    override val diagnostics: StateFlow<Map<String, DiagnosticValue>> = _diagnostics.asStateFlow()

    init {
        updateStats()
    }

    private fun updateStats() {
        val reqs = totalRequests.get()
        val mem = memoryHits.get()
        val dsk = diskHits.get()
        val dec = decodeCount.get()
        val pal = paletteExtractions.get()

        val memRate = if (reqs > 0) mem.toDouble() / reqs else 0.0
        val dskRate = if (reqs > 0) dsk.toDouble() / reqs else 0.0
        val missRate = if (reqs > 0) (reqs - mem - dsk).toDouble() / reqs else 0.0

        val avgDec = if (dec > 0) (totalDecodeTimeNs.get().toDouble() / dec) / 1_000_000.0 else 0.0
        val avgPal = if (pal > 0) (totalPaletteTimeNs.get().toDouble() / pal) / 1_000_000.0 else 0.0

        _diagnostics.value = mapOf(
            "totalRequests" to DiagnosticValue.Number(reqs),
            "memoryHits" to DiagnosticValue.Number(mem),
            "memoryEvictions" to DiagnosticValue.Number(memoryEvictions.get()),
            "diskHits" to DiagnosticValue.Number(dsk),
            "diskWrites" to DiagnosticValue.Number(diskWrites.get()),
            "diskReads" to DiagnosticValue.Number(diskReads.get()),
            "negativeCacheHits" to DiagnosticValue.Number(negativeCacheHits.get()),
            "failedDecodes" to DiagnosticValue.Number(failedDecodes.get()),
            "decodeCount" to DiagnosticValue.Number(dec),
            "paletteCacheHits" to DiagnosticValue.Number(paletteCacheHits.get()),
            "paletteExtractions" to DiagnosticValue.Number(pal),
            "pendingJobDeduplications" to DiagnosticValue.Number(pendingJobDeduplications.get()),
            "averageDecodeMs" to DiagnosticValue.Number(avgDec),
            "averagePaletteExtractionMs" to DiagnosticValue.Number(avgPal),
            "memoryHitRate" to DiagnosticValue.Percentage(memRate),
            "diskHitRate" to DiagnosticValue.Percentage(dskRate),
            "decodeMissRate" to DiagnosticValue.Percentage(missRate),
            "lastDecodeTimestamp" to DiagnosticValue.Timestamp(lastDecodeTimestamp.get())
        )
    }

    // Dynamically sized memory cache based on memoryClass / 8
    private val memoryCache = run {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryClass = activityManager.memoryClass // in MB
        val cacheSizeKb = (memoryClass / 8) * 1024
        object : LruCache<String, Bitmap>(cacheSizeKb) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
            override fun entryRemoved(evicted: Boolean, key: String?, oldValue: Bitmap?, newValue: Bitmap?) {
                if (evicted) {
                    memoryEvictions.incrementAndGet()
                    updateStats()
                }
            }
        }
    }

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pendingJobs = ConcurrentHashMap<String, Deferred<Bitmap?>>()
    private val failedDecodesMap = ConcurrentHashMap<String, FailedDecode>()

    fun getCacheKey(request: ArtworkRequest): String = request.baseKey

    fun getCachedArtwork(request: ArtworkRequest): Bitmap? {
        totalRequests.incrementAndGet()
        val bitmap = memoryCache.get(request.cacheKey)
        if (bitmap != null) {
            memoryHits.incrementAndGet()
        }
        updateStats()
        return bitmap
    }

    fun preload(request: ArtworkRequest) {
        if (memoryCache.get(request.cacheKey) != null) return
        repositoryScope.launch {
            load(request)
        }
    }

    suspend fun load(request: ArtworkRequest): Bitmap? = withContext(Dispatchers.IO) {
        totalRequests.incrementAndGet()
        val key = request.baseKey
        val cacheKey = request.cacheKey
        
        // 1. Quick memory check without lock/deferred
        memoryCache.get(cacheKey)?.let { 
            memoryHits.incrementAndGet()
            updateStats()
            return@withContext it 
        }

        // 2. Request deduplication using pendingJobs
        val deferred = pendingJobs.computeIfAbsent(cacheKey) { _ ->
            repositoryScope.async {
                try {
                    // Check negative cache (30 seconds)
                    val failed = failedDecodesMap[cacheKey]
                    if (failed != null && System.currentTimeMillis() - failed.timestamp < 30000) {
                        negativeCacheHits.incrementAndGet()
                        updateStats()
                        return@async null
                    }

                    val bitmap = loadAndDecodeArtwork(key, request.albumId, request.size, request.song)
                    if (bitmap == null) {
                        failedDecodesMap[cacheKey] = FailedDecode(System.currentTimeMillis(), RuntimeException("Artwork decode returned null"))
                    }
                    bitmap
                } catch (e: Exception) {
                    failedDecodesMap[cacheKey] = FailedDecode(System.currentTimeMillis(), e)
                    null
                } finally {
                    pendingJobs.remove(cacheKey)
                }
            }
        }

        if (deferred.isActive) {
            // This is a new request we are waiting for or a deduplicated check
        } else {
            pendingJobDeduplications.incrementAndGet()
            updateStats()
        }

        deferred.await()
    }

    private suspend fun loadAndDecodeArtwork(key: String, albumId: Long, size: ArtworkSize, song: Song?): Bitmap? {
        // 3. Disk Cache
        val cachedFile = diskCache.get(key)
        if (cachedFile != null) {
            diskHits.incrementAndGet()
            diskReads.incrementAndGet()
            val startTime = System.nanoTime()
            val bitmap = decodeAndDownsample(cachedFile, size)
            val elapsed = System.nanoTime() - startTime
            if (bitmap != null) {
                decodeCount.incrementAndGet()
                totalDecodeTimeNs.addAndGet(elapsed)
                lastDecodeTimestamp.set(System.currentTimeMillis())
                memoryCache.put("${key}_${size.pixels}", bitmap)
                updateStats()
                return bitmap
            } else {
                failedDecodes.incrementAndGet()
                updateStats()
            }
        }

        if (song == null) return null

        // 4. Metadata Extraction
        val metadata = metadataReader.read(song)
        if (metadata.artwork != null) {
            diskWrites.incrementAndGet()
            val savedFile = diskCache.put(key, metadata.artwork)
            if (savedFile != null) {
                diskReads.incrementAndGet()
                val startTime = System.nanoTime()
                val bitmap = decodeAndDownsample(savedFile, size)
                val elapsed = System.nanoTime() - startTime
                if (bitmap != null) {
                    decodeCount.incrementAndGet()
                    totalDecodeTimeNs.addAndGet(elapsed)
                    lastDecodeTimestamp.set(System.currentTimeMillis())
                    memoryCache.put("${key}_${size.pixels}", bitmap)
                    updateStats()
                    return bitmap
                } else {
                    failedDecodes.incrementAndGet()
                    updateStats()
                }
            }
        }

        // 5. Folder Art Fallback
        findFolderArtwork(song.path)?.let { folderArtFile ->
            try {
                val bytes = folderArtFile.readBytes()
                diskWrites.incrementAndGet()
                diskCache.put(key, bytes)?.let { savedFile ->
                    diskReads.incrementAndGet()
                    val startTime = System.nanoTime()
                    val bitmap = decodeAndDownsample(savedFile, size)
                    val elapsed = System.nanoTime() - startTime
                    if (bitmap != null) {
                        decodeCount.incrementAndGet()
                        totalDecodeTimeNs.addAndGet(elapsed)
                        lastDecodeTimestamp.set(System.currentTimeMillis())
                        memoryCache.put("${key}_${size.pixels}", bitmap)
                        updateStats()
                        return bitmap
                    } else {
                        failedDecodes.incrementAndGet()
                        updateStats()
                    }
                }
            } catch (e: Exception) {
                // Ignore folder art errors
            }
        }

        // 6. Placeholder Generation
        val placeholderBitmap = PlaceholderGenerator.generate(
            title = song.title,
            album = song.album,
            artist = song.artist
        )
        diskWrites.incrementAndGet()
        diskCache.put(key, placeholderBitmap)?.let { savedFile ->
            diskReads.incrementAndGet()
            val startTime = System.nanoTime()
            val bitmap = decodeAndDownsample(savedFile, size)
            val elapsed = System.nanoTime() - startTime
            if (bitmap != null) {
                decodeCount.incrementAndGet()
                totalDecodeTimeNs.addAndGet(elapsed)
                lastDecodeTimestamp.set(System.currentTimeMillis())
                memoryCache.put("${key}_${size.pixels}", bitmap)
                updateStats()
                return bitmap
            } else {
                failedDecodes.incrementAndGet()
                updateStats()
            }
        }

        return null
    }

    private fun decodeAndDownsample(file: File, size: ArtworkSize): Bitmap? {
        val targetSize = size.pixels
        if (targetSize <= 0) return BitmapFactory.decodeFile(file.absolutePath)
        
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)

            var inSampleSize = 1
            if (options.outHeight > targetSize || options.outWidth > targetSize) {
                val height = options.outHeight
                val width = options.outWidth
                while (height / inSampleSize > targetSize || width / inSampleSize > targetSize) {
                    inSampleSize *= 2
                }
            }

            options.inSampleSize = inSampleSize
            options.inJustDecodeBounds = false
            
            // Thumbnail & MiniPlayer (<= 256px) -> RGB_565, Player & Original (> 256px) -> ARGB_8888
            options.inPreferredConfig = if (targetSize <= 256) {
                Bitmap.Config.RGB_565
            } else {
                Bitmap.Config.ARGB_8888
            }
            
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: Exception) {
            null
        }
    }

    private fun findFolderArtwork(songPath: String): File? {
        val parent = File(songPath).parentFile ?: return null
        if (!parent.exists() || !parent.isDirectory) return null

        return parent.listFiles()?.firstOrNull { 
            it.isFile && folderArtworkNames.contains(it.name.lowercase()) 
        }
    }
}
