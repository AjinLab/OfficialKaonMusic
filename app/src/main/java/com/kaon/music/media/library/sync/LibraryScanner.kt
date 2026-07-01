package com.kaon.music.media.library.sync

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibraryScanner(private val context: Context) {
    suspend fun scanMediaStore(): List<MediaStoreSong> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
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

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val songs = mutableListOf<MediaStoreSong>()

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            null
        )?.use { cursor ->
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
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaStoreId)
                
                songs.add(
                    MediaStoreSong(
                        mediaStoreId = mediaStoreId,
                        uri = uri.toString(),
                        path = cursor.getString(pathIdx) ?: "",
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
    }
}
