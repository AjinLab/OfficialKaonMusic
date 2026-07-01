package com.kaon.music.media.model

data class AudioMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String?,
    val genre: String?,
    val composer: String?,
    val year: String?,
    val duration: Long,
    val trackNumber: Int,
    val discNumber: Int,
    val bitrate: Int?,
    val sampleRate: Int?,
    val artwork: ByteArray?,
    val artworkUri: android.net.Uri? = null
)
