package com.kaon.music.core.data.model

import android.net.Uri

/**
 * Domain model representing a playable track in Kaon Music.
 *
 * @property id Kaon-owned stable track identifier.
 * @property mediaStoreId Underlying Android MediaStore ID used for sync matching and URI resolution.
 * @property title Track title.
 * @property artist Artist name.
 * @property album Album name.
 * @property durationMs Duration in milliseconds.
 * @property sizeBytes File size in bytes.
 * @property dateModified Timestamp of last modification on disk.
 * @property contentUri Resolvable Android content URI for audio streaming/playback.
 * @property albumId MediaStore album ID used for artwork resolution.
 * @property isFavorite Whether the track has been marked as a favorite by the user.
 * @property isMissing True if the file has vanished from MediaStore but user data is retained.
 */
data class Track(
    val id: Long,
    val mediaStoreId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateModified: Long,
    val contentUri: Uri,
    val albumId: Long,
    val isFavorite: Boolean = false,
    val isMissing: Boolean = false
) {
    val displayTitle: String get() = title.ifBlank { "Unknown Title" }
    val displayArtist: String get() = artist.ifBlank { "Unknown Artist" }
    val displayAlbum: String get() = album.ifBlank { "Unknown Album" }
}
