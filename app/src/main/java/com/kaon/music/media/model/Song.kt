package com.kaon.music.media.model

import androidx.compose.runtime.Immutable

@Immutable
data class Song(
    val id: Long,
    val mediaStoreId: Long,
    val uri: String,
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String?,
    val albumId: Long,
    val artistId: Long,
    val genre: String?,
    val composer: String?,
    val year: String?,
    val track: Int,
    val disc: Int,
    val duration: Long,
    val bitrate: Int?,
    val sampleRate: Int?,
    val mimeType: String?,
    val size: Long,
    val dateAdded: Long,
    val dateModified: Long,
    val lastScanned: Long,
    val artworkPath: String?,
    val artworkHash: String? = null,
    val favorite: Boolean = false
) {
    val searchTitle: String = title.lowercase()
    val searchArtist: String = artist.lowercase()
    val searchAlbum: String = album.lowercase()
}