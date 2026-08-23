package com.kaon.music.core.data.model

/**
 * Domain model representing a derived Album grouped by MediaStore albumId.
 */
data class Album(
    val albumId: Long,
    val title: String,
    val artist: String,
    val artistId: Long = 0L,
    val year: Int = 0,
    val trackCount: Int,
    val totalDurationMs: Long = 0L
) {
    val displayTitle: String get() = title.ifBlank { "Unknown Album" }
    val displayArtist: String get() = artist.ifBlank { "Unknown Artist" }
}
