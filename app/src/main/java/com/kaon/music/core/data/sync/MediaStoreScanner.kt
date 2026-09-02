package com.kaon.music.core.data.sync

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.kaon.music.core.data.model.AudioFormat
import timber.log.Timber

/**
 * Raw item read directly from Android MediaStore cursor.
 */
data class MediaStoreAudioItem(
    val mediaStoreId: Long,
    val title: String,
    val artist: String,
    val artistId: Long = 0L,
    val album: String,
    val albumId: Long,
    val trackNumber: Int = 0,
    val discNumber: Int = 1,
    val year: Int = 0,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateModified: Long,
    val dateAdded: Long = 0L,
    val relativePath: String,
    val contentUri: Uri? = null,
    val mimeType: String? = null
)

open class MediaStoreScanner(private val context: Context) {

    private val contentResolver: ContentResolver get() = context.contentResolver

    open fun hasStoragePermission(): Boolean {
        return try {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                android.Manifest.permission.READ_MEDIA_AUDIO
            } else {
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            }
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                permission
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    open fun scanAudioFiles(): List<MediaStoreAudioItem> {
        return scanAudioFiles(minDurationMs = 5000L)
    }

    open fun scanAudioFiles(minDurationMs: Long): List<MediaStoreAudioItem> {
        val items = mutableListOf<MediaStoreAudioItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DISPLAY_NAME
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Audio.Media.DATA)
            }
        }.toTypedArray()

        // Query audio collection filtered by duration threshold (excludes short notification chimes/ringtones)
        val selection = "${MediaStore.Audio.Media.DURATION} >= $minDurationMs"

        try {
            // Cursor is closed by `use`; lint's Recycle check does not follow it here.
            @Suppress("Recycle")
            contentResolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val artistIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST_ID)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val trackCol = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
                val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val dateModifiedCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
                val dateAddedCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val mimeTypeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val displayNameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else {
                    @Suppress("DEPRECATION")
                    cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                }

                if (idCol < 0) return@use

                while (cursor.moveToNext()) {
                    val mediaStoreId = cursor.getLong(idCol)
                    val title = if (titleCol >= 0) cursor.getString(titleCol) ?: "Unknown Title" else "Unknown Title"
                    val artist = if (artistCol >= 0) cursor.getString(artistCol) ?: "Unknown Artist" else "Unknown Artist"
                    val artistId = if (artistIdCol >= 0) cursor.getLong(artistIdCol) else 0L
                    val album = if (albumCol >= 0) cursor.getString(albumCol) ?: "Unknown Album" else "Unknown Album"
                    val albumId = if (albumIdCol >= 0) cursor.getLong(albumIdCol) else 0L
                    val rawTrack = if (trackCol >= 0) cursor.getInt(trackCol) else 0
                    val year = if (yearCol >= 0) cursor.getInt(yearCol) else 0

                    val discNumber = if (rawTrack >= 1000) rawTrack / 1000 else 1
                    val trackNumber = if (rawTrack >= 1000) rawTrack % 1000 else rawTrack

                    val durationMs = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                    val sizeBytes = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val dateModified = if (dateModifiedCol >= 0) cursor.getLong(dateModifiedCol) else 0L
                    val dateAdded = if (dateAddedCol >= 0) cursor.getLong(dateAddedCol) else 0L
                    val path = if (pathCol >= 0) cursor.getString(pathCol) ?: "" else ""
                    val displayName = if (displayNameCol >= 0) cursor.getString(displayNameCol) else null
                    val rawMimeType = if (mimeTypeCol >= 0) cursor.getString(mimeTypeCol)?.trim()?.lowercase() else null

                    // Canonicalize or infer MIME type across all audio formats
                    val mimeType = AudioFormat.media3MimeType(
                        mimeType = rawMimeType,
                        pathOrDisplayName = displayName ?: path
                    ) ?: rawMimeType

                    val contentUri = ContentUris.withAppendedId(collection, mediaStoreId)

                    items.add(
                        MediaStoreAudioItem(
                            mediaStoreId = mediaStoreId,
                            title = title.trim(),
                            artist = artist.trim(),
                            artistId = artistId,
                            album = album.trim(),
                            albumId = albumId,
                            trackNumber = trackNumber,
                            discNumber = discNumber,
                            year = year,
                            durationMs = durationMs,
                            sizeBytes = sizeBytes,
                            dateModified = dateModified,
                            dateAdded = dateAdded,
                            relativePath = path,
                            contentUri = contentUri,
                            mimeType = mimeType
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Timber.tag("MediaStoreScanner").e(e, "Error scanning MediaStore audio files")
        }

        return items
    }
}
