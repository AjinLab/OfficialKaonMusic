package com.kaon.music.core.data.model

/**
 * Domain model representing a user playlist.
 *
 * @property id Unique playlist ID.
 * @property name Playlist display name.
 * @property trackCount Number of active (non-orphaned) tracks in the playlist.
 * @property createdAt Timestamp of creation.
 * @property updatedAt Timestamp of last update.
 */
data class Playlist(
    val id: Long,
    val name: String,
    val trackCount: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
