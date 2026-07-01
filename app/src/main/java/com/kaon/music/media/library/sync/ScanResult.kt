package com.kaon.music.media.library.sync

data class ScanResult(
    val added: Int,
    val updated: Int,
    val removed: Int,
    val unchanged: Int
)
