package com.kaon.music.media.search

data class SearchResult(
    val id: Long,
    val type: String, // "SONG", "ALBUM", "ARTIST"
    val title: String,
    val subtitle: String?,
    val albumId: Long?,
    val score: Int
)
