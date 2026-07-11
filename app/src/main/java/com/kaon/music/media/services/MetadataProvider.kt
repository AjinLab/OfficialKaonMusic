package com.kaon.music.media.services

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.kaon.music.media.model.AudioMetadata
import com.kaon.music.media.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MetadataProvider(private val context: Context) {
    private val failedArtworkAlbumIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    suspend fun read(song: Song): AudioMetadata = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(song.uri))
            
            val rawTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val title = if (rawTitle.isNullOrBlank() || rawTitle.equals("<unknown>", ignoreCase = true)) {
                if (song.title.equals("<unknown>", ignoreCase = true)) "Unknown Title" else song.title
            } else rawTitle

            val rawArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val artist = if (rawArtist.isNullOrBlank() || rawArtist.equals("<unknown>", ignoreCase = true)) {
                if (song.artist.equals("<unknown>", ignoreCase = true)) "Unknown Artist" else song.artist
            } else rawArtist

            val rawAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val album = if (rawAlbum.isNullOrBlank() || rawAlbum.equals("<unknown>", ignoreCase = true)) {
                if (song.album.equals("<unknown>", ignoreCase = true)) "Unknown Album" else song.album
            } else rawAlbum

            val rawAlbumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            val albumArtist = if (rawAlbumArtist.isNullOrBlank() || rawAlbumArtist.equals("<unknown>", ignoreCase = true)) null else rawAlbumArtist

            val rawGenre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            val genre = if (rawGenre.isNullOrBlank() || rawGenre.equals("<unknown>", ignoreCase = true)) null else rawGenre

            val rawYear = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            val year = if (rawYear.isNullOrBlank() || rawYear.equals("<unknown>", ignoreCase = true)) null else {
                val match = Regex("\\b\\d{4}\\b").find(rawYear)
                match?.value ?: rawYear
            }

            val rawComposer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
            val composer = if (rawComposer.isNullOrBlank() || rawComposer.equals("<unknown>", ignoreCase = true)) null else rawComposer

            val rawBitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val bitrate = rawBitrate?.toIntOrNull()

            val rawSampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
            val sampleRate = rawSampleRate?.toIntOrNull()

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: song.duration

            val trackNumberStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            val trackNumber = parseTrackNumber(trackNumberStr) ?: song.track

            val discNumberStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
            val discNumber = parseTrackNumber(discNumberStr) ?: song.disc

            val artwork = if (failedArtworkAlbumIds.contains(song.albumId)) {
                null
            } else {
                val art = retriever.embeddedPicture
                if (art == null) {
                    val fallbackArt = loadAlbumArtFromMediaStore(context, song.albumId)
                    if (fallbackArt == null) {
                        failedArtworkAlbumIds.add(song.albumId)
                    }
                    fallbackArt
                } else {
                    art
                }
            }
            
            val artworkUri = song.artworkPath?.let { 
                if (it.startsWith("/")) Uri.fromFile(java.io.File(it)) else Uri.parse(it) 
            }

            AudioMetadata(
                title = title,
                artist = artist,
                album = album,
                albumArtist = albumArtist,
                genre = genre,
                composer = composer,
                year = year,
                duration = duration,
                trackNumber = trackNumber,
                discNumber = discNumber,
                bitrate = bitrate,
                sampleRate = sampleRate,
                artwork = artwork,
                artworkUri = artworkUri
            )
        } catch (e: Exception) {
            AudioMetadata(
                title = if (song.title.equals("<unknown>", ignoreCase = true)) "Unknown Title" else song.title,
                artist = if (song.artist.equals("<unknown>", ignoreCase = true)) "Unknown Artist" else song.artist,
                album = if (song.album.equals("<unknown>", ignoreCase = true)) "Unknown Album" else song.album,
                albumArtist = null,
                genre = null,
                composer = null,
                year = null,
                duration = song.duration,
                trackNumber = song.track,
                discNumber = song.disc,
                bitrate = null,
                sampleRate = null,
                artwork = null,
                artworkUri = song.artworkPath?.let { 
                    if (it.startsWith("/")) Uri.fromFile(java.io.File(it)) else Uri.parse(it) 
                }
            )
        } finally {
            retriever.release()
        }
    }

    private fun loadAlbumArtFromMediaStore(context: Context, albumId: Long): ByteArray? {
        val artworkUri = android.content.ContentUris.withAppendedId(
            android.net.Uri.parse("content://media/external/audio/albumart"),
            albumId
        )
        return try {
            context.contentResolver.openInputStream(artworkUri)?.use { inputStream ->
                inputStream.readBytes()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseTrackNumber(trackStr: String?): Int? {
        if (trackStr.isNullOrEmpty()) return null
        return try {
            val parts = trackStr.split("/")
            parts[0].trim().toIntOrNull()
        } catch (e: Exception) {
            null
        }
    }
}