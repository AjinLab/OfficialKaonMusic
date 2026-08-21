package com.kaon.music.core.artwork

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

enum class SizeBucket(val sizePx: Int) {
    THUMBNAIL(256),
    FULL(1024)
}

/**
 * Platform-aware artwork loader that isolates version-specific logic.
 *
 * Implements ARCHITECTURE_ATTRIBUTED.md §9 / k3.md D7:
 * - API 29+: Uses ContentResolver.loadThumbnail for memory efficiency.
 * - API 26-28: Uses legacy content://media/external/audio/albumart Uri.
 * - Requests are size-aware to prevent OOM/jank in large track lists.
 */
class ArtworkResolver(private val context: Context) {

    private val legacyAlbumArtBaseUri = Uri.parse("content://media/external/audio/albumart")

    /**
     * Builds the artwork cache key used by Coil.
     */
    fun buildArtworkKey(albumId: Long, bucket: SizeBucket): String {
        return "artwork_${albumId}_${bucket.name}"
    }

    /**
     * Obtains the legacy artwork Uri for a given album.
     */
    fun getLegacyAlbumArtUri(albumId: Long): Uri {
        return ContentUris.withAppendedId(legacyAlbumArtBaseUri, albumId)
    }

    /**
     * Synchronously loads a sized Bitmap thumbnail from MediaStore on API 29+.
     * Returns null if loading fails, allowing callers to display standard placeholders.
     */
    @WorkerThread
    suspend fun loadArtworkBitmap(albumId: Long, bucket: SizeBucket): Bitmap? = withContext(Dispatchers.IO) {
        if (albumId <= 0) return@withContext null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val albumUri = ContentUris.withAppendedId(
                    android.provider.MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                    albumId
                )
                val targetSize = Size(bucket.sizePx, bucket.sizePx)
                context.contentResolver.loadThumbnail(albumUri, targetSize, null)
            } catch (e: Exception) {
                // Return null on failure — never throw or interrupt playback
                null
            }
        } else {
            // On legacy API 26-28, return null here; Coil handles the content URI directly
            null
        }
    }
}
