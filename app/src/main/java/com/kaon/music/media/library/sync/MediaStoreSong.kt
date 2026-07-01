package com.kaon.music.media.library.sync

data class MediaStoreSong(
    val mediaStoreId: Long,
    val uri: String,
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val artistId: Long,
    val duration: Long,
    val size: Long,
    val dateAdded: Long,
    val dateModified: Long,
    val mimeType: String?,
    val track: Int
)
