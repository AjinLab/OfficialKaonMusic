package com.kaon.music.core.data.model

import android.net.Uri

/**
 * Domain model representing a playable track in Kaon Music.
 *
 * @property id Kaon-owned stable track identifier.
 * @property mediaStoreId Underlying Android MediaStore ID used for sync matching and URI resolution.
 * @property title Track title.
 * @property artist Artist name.
 * @property artistId MediaStore artist ID.
 * @property album Album name.
 * @property albumId MediaStore album ID used for artwork resolution.
 * @property trackNumber Track index within album.
 * @property discNumber Disc index within album.
 * @property year Release year.
 * @property durationMs Duration in milliseconds.
 * @property sizeBytes File size in bytes.
 * @property dateModified Timestamp of last modification on disk.
 * @property contentUri Resolvable Android content URI for audio streaming/playback.
 * @property isFavorite Whether the track has been marked as a favorite by the user.
 * @property isMissing True if the file has vanished from MediaStore but user data is retained.
 */
data class Track(
    val id: Long,
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
    val contentUri: Uri? = null,
    val isFavorite: Boolean = false,
    val isMissing: Boolean = false,
    val source: String = "LOCAL",
    val youtubeVideoId: String? = null
) {
    val displayTitle: String get() = title.ifBlank { "Unknown Title" }
    val displayArtist: String get() = artist.ifBlank { "Unknown Artist" }
    val displayAlbum: String get() = album.ifBlank { "Unknown Album" }
    val isOnline: Boolean get() = source == "YOUTUBE"
}
