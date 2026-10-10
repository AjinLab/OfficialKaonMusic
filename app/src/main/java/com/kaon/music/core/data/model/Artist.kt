package com.kaon.music.core.data.model

/**
 * Domain model representing a derived Artist grouped by artist name / artistId.
 */
data class Artist(
    val artistId: Long,
    val name: String,
    // Aggregate counts default to 0: detail destinations reconstruct an Artist from a navigation
    // id+name pair (Phase 6A §4), where the browse aggregates are not loaded yet.
    val albumCount: Int = 0,
    val trackCount: Int = 0
) {
    val displayName: String get() = name.ifBlank { "Unknown Artist" }
}
