package com.kaon.music.media.library.db.entity

data class SearchResultProjection(
    val id: Long,
    val type: String, // "SONG", "ALBUM", "ARTIST"
    val title: String,
    val subtitle: String?,
    val albumId: Long?,
    val score: Int
)
