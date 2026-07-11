package com.kaon.music.media.artwork

@JvmInline
value class ArtworkSize(val pixels: Int)

object ArtworkSizes {
    val Thumbnail = ArtworkSize(128)
    val MiniPlayer = ArtworkSize(256)
    val Player = ArtworkSize(512)
    val Original = ArtworkSize(2048)
}
