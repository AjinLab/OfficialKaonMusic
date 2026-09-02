package com.kaon.music.core.data.model

data class ArtistMetadata(
    val name: String,
    val photoUrl: String? = null,
    val backgroundUrl: String? = null,
    val logoUrl: String? = null,
    val bannerUrl: String? = null,
    val biography: String? = null,
    val biographySource: String? = null,
    val genres: List<String> = emptyList(),
    val similarArtists: List<String> = emptyList(),
    val topTracks: List<String> = emptyList()
)
