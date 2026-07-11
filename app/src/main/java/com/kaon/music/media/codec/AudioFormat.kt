package com.kaon.music.media.codec

enum class AudioFormat(
    val mimeType: String,
    val displayName: String,
    val extensions: List<String>
) {
    MP3("audio/mpeg", "MP3", listOf("mp3")),
    AAC("audio/aac", "AAC", listOf("aac", "adts")),
    M4A("audio/mp4", "M4A", listOf("m4a")),
    FLAC("audio/flac", "FLAC", listOf("flac")),
    WAV("audio/wav", "WAV", listOf("wav", "wave")),
    OGG("audio/ogg", "OGG Vorbis", listOf("ogg")),
    OPUS("audio/opus", "Opus", listOf("opus")),
    AMR("audio/amr", "AMR", listOf("amr")),
    THREE_GP("audio/3gpp", "3GP Audio", listOf("3gp", "3gpp")),
    AIFF("audio/x-aiff", "AIFF", listOf("aiff", "aif")),
    ALAC("audio/x-alac", "ALAC", listOf("alac")),
    UNKNOWN("audio/unknown", "Unknown", emptyList());

    companion object {
        fun fromMimeType(mimeType: String?): AudioFormat {
            if (mimeType == null) return UNKNOWN
            val lowerMime = mimeType.lowercase()
            return entries.firstOrNull { format ->
                format.mimeType.lowercase() == lowerMime || 
                (format == M4A && lowerMime == "audio/x-m4a") ||
                (format == FLAC && lowerMime == "audio/x-flac") ||
                (format == WAV && (lowerMime == "audio/x-wav" || lowerMime == "audio/wave")) ||
                (format == OGG && (lowerMime == "audio/x-ogg" || lowerMime == "application/ogg")) ||
                (format == OPUS && lowerMime == "audio/x-opus") ||
                (format == AMR && lowerMime == "audio/amr-wb") ||
                (format == AIFF && lowerMime == "audio/aiff") ||
                (format == ALAC && lowerMime == "audio/alac")
            } ?: UNKNOWN
        }

        fun fromExtension(ext: String?): AudioFormat {
            if (ext == null) return UNKNOWN
            val lowerExt = ext.lowercase()
            return entries.firstOrNull { format ->
                format.extensions.contains(lowerExt)
            } ?: UNKNOWN
        }
    }
}
