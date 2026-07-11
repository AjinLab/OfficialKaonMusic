package com.kaon.music.media.codec

data class AudioInfo(
    val codec: String,
    val bitrate: Int?,
    val sampleRate: Int?,
    val bitDepth: Int?,
    val channels: Int?,
    val mimeType: String
)
