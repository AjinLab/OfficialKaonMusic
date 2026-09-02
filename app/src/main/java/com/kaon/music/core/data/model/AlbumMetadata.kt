package com.kaon.music.core.data.model

data class AlbumMetadata(
    val title: String,
    val artist: String,
    val coverArtUrl: String? = null,
    val backCoverUrl: String? = null,
    val cdArtUrl: String? = null,
    val bookletUrl: String? = null,
    val genres: List<String> = emptyList(),
    val label: String? = null,
    val releaseDate: String? = null,
    val releaseType: String? = null,
    val country: String? = null,
    val trackCount: Int? = null
)
