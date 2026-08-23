package com.kaon.music.core.data.model

/**
 * Domain model representing a derived Artist grouped by artist name / artistId.
 */
data class Artist(
    val artistId: Long,
    val name: String,
    val albumCount: Int,
    val trackCount: Int
) {
    val displayName: String get() = name.ifBlank { "Unknown Artist" }
}
