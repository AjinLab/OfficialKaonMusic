package com.kaon.music.core.data.model

data class TrackMetadata(
    val title: String,
    val artist: String,
    val album: String? = null,
    val lyrics: LyricsResult? = null,
    val genres: List<String> = emptyList(),
    val releaseDate: String? = null,
    val label: String? = null,
    val isrc: String? = null,
    val credits: List<String> = emptyList(),
    val previewUrl: String? = null
)
