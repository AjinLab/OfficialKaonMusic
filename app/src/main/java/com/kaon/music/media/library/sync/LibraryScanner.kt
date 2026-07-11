package com.kaon.music.media.library.sync

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibraryScanner(private val context: Context) {
    private companion object {
        private val recognizedExtensions = setOf("mp3", "aac", "m4a", "flac", "wav", "ogg", "opus", "amr", "3gp", "aiff", "alac")
        private val recognizedMimePrefixes = listOf(
            "audio/mpeg", "audio/mp3", "audio/aac", "audio/mp4", "audio/m4a", "audio/x-m4a",
            "audio/flac", "audio/x-flac", "audio/wav", "audio/x-wav", "audio/wave",
            "audio/ogg", "audio/opus", "audio/amr", "audio/3gpp", "audio/x-aiff", "audio/aiff",
            "audio/alac", "audio/x-alac"
        )
    }

    private val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.ARTIST_ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.DATE_MODIFIED,
        MediaStore.Audio.Media.MIME_TYPE,
        MediaStore.Audio.Media.TRACK
    )

    private val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR " +
            "${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%' OR " +
            "${MediaStore.Audio.Media.MIME_TYPE} LIKE 'application/ogg'"

    suspend fun scanMediaStore(): List<MediaStoreSong> = scanMediaStoreInternal(null, null)

    suspend fun scanMediaStore(generationThreshold: Long): List<MediaStoreSong> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && generationThreshold > 0) {
            val genSelection = "($selection) AND (${MediaStore.Audio.Media.GENERATION_ADDED} > ? OR ${MediaStore.Audio.Media.GENERATION_MODIFIED} > ?)"
            val selectionArgs = arrayOf(generationThreshold.toString(), generationThreshold.toString())
            scanMediaStoreInternal(genSelection, selectionArgs)
        } else {
            scanMediaStoreInternal(null, null)
        }
    }

    suspend fun scanActiveMediaStoreIds(): Set<Long> = withContext(Dispatchers.IO) {
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID),
            selection,
            null,
            null
        )?.use { cursor ->
            val ids = HashSet<Long>(cursor.count.coerceAtLeast(16))
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            while (cursor.moveToNext()) {
                ids.add(cursor.getLong(idIdx))
            }
            ids
        } ?: emptySet()
    }

    private suspend fun scanMediaStoreInternal(
        customSelection: String?,
        selectionArgs: Array<String>?
    ): List<MediaStoreSong> = withContext(Dispatchers.IO) {
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            customSelection ?: selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val songs = ArrayList<MediaStoreSong>(cursor.count.coerceAtLeast(16))
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val pathIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val albumIdIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val artistIdIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val titleIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val addedIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val modifiedIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val mimeTypeIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val trackIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)

            while (cursor.moveToNext()) {
                val mediaStoreId = cursor.getLong(idIdx)
                val path = cursor.getString(pathIdx) ?: ""
                val ext = path.substringAfterLast('.').lowercase()
                val mimeType = cursor.getString(mimeTypeIdx)?.lowercase()
                
                if (isRecognizedFormat(mimeType, ext)) {
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaStoreId)
                    songs.add(
                        MediaStoreSong(
                            mediaStoreId = mediaStoreId,
                            uri = uri.toString(),
                            path = path,
                            title = cursor.getString(titleIdx) ?: "Unknown Title",
                            artist = cursor.getString(artistIdx) ?: "Unknown Artist",
                            album = cursor.getString(albumIdx) ?: "Unknown Album",
                            albumId = cursor.getLong(albumIdIdx),
                            artistId = cursor.getLong(artistIdIdx),
                            duration = cursor.getLong(durationIdx),
                            size = cursor.getLong(sizeIdx),
                            dateAdded = cursor.getLong(addedIdx),
                            dateModified = cursor.getLong(modifiedIdx),
                            mimeType = cursor.getString(mimeTypeIdx),
                            track = cursor.getInt(trackIdx)
                        )
                    )
                }
            }
            songs
        } ?: emptyList()
    }

    private fun isRecognizedFormat(mimeType: String?, ext: String): Boolean {
        if (ext in recognizedExtensions) return true
        
        if (mimeType == null) return false
        return recognizedMimePrefixes.any { mimeType.startsWith(it) }
    }
}
