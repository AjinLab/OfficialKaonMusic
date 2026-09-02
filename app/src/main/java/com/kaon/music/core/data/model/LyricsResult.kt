package com.kaon.music.core.data.model

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

data class LyricsResult(
    val syncedLyrics: List<LyricLine> = emptyList(),
    val plainLyrics: String? = null,
    val isInstrumental: Boolean = false,
    val source: String = "LRCLIB"
) {
    val hasLyrics: Boolean get() = syncedLyrics.isNotEmpty() || !plainLyrics.isNullOrBlank()
}
