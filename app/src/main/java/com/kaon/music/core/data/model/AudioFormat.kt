package com.kaon.music.core.data.model

import android.net.Uri

/**
 * Audio format detection, MIME type canonicalization for Media3/ExoPlayer,
 * lossless status classification, and human-readable badges for music playback.
 */
object AudioFormat {

    /**
     * Maps variant or vendor MIME types and file extensions to standard Media3 MIME types.
     */
    fun media3MimeType(
        mimeType: String?,
        uri: Uri? = null,
        pathOrDisplayName: String? = null
    ): String? {
        val mime = mimeType?.trim()?.lowercase().orEmpty()
        val mapped = when (mime) {
            "audio/flac", "audio/x-flac", "application/flac", "application/x-flac" -> "audio/flac"
            "audio/mpeg", "audio/mp3", "audio/x-mp3", "audio/x-mpeg", "audio/mpg" -> "audio/mpeg"
            "audio/mp4", "audio/x-m4a", "audio/m4a", "audio/mp4a-latm" -> "audio/mp4"
            "audio/aac", "audio/aacp", "audio/x-aac" -> "audio/aac"
            "audio/alac", "audio/x-alac" -> "audio/alac"
            "audio/ogg", "application/ogg", "audio/vorbis", "audio/x-vorbis+ogg" -> "audio/ogg"
            "audio/opus", "audio/x-opus+ogg" -> "audio/opus"
            "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave", "audio/x-pn-wav" -> "audio/wav"
            "audio/aiff", "audio/x-aiff", "audio/x-pn-aiff", "audio/aif" -> "audio/aiff"
            "audio/x-ms-wma", "audio/wma" -> "audio/x-ms-wma"
            "audio/x-matroska", "audio/matroska" -> "audio/x-matroska"
            "audio/webm" -> "audio/webm"
            "audio/amr", "audio/amr-wb" -> "audio/amr"
            "audio/3gpp", "audio/3gp", "audio/3gpp2" -> "audio/3gpp"
            "audio/ape", "audio/x-ape", "audio/monkeys-audio" -> "audio/x-ape"
            "audio/dsf", "audio/dff", "audio/x-dsd" -> "audio/x-dsd"
            "audio/midi", "audio/x-midi", "audio/mid", "audio/sp-midi" -> "audio/midi"
            else -> mime.takeIf { it.isNotEmpty() && it != "application/octet-stream" && it != "audio/*" }
        }

        if (mapped != null) {
            return mapped
        }

        // Fallback: Infer Media3 MIME type from extension
        val ext = extractExtension(uri, pathOrDisplayName)
        return when (ext) {
            "flac" -> "audio/flac"
            "mp3" -> "audio/mpeg"
            "m4a", "m4b", "m4p" -> "audio/mp4"
            "aac" -> "audio/aac"
            "alac" -> "audio/alac"
            "ogg", "oga" -> "audio/ogg"
            "opus" -> "audio/opus"
            "wav", "wave" -> "audio/wav"
            "aif", "aiff", "aifc" -> "audio/aiff"
            "wma" -> "audio/x-ms-wma"
            "mka" -> "audio/x-matroska"
            "webm" -> "audio/webm"
            "amr" -> "audio/amr"
            "3gp", "3gpp" -> "audio/3gpp"
            "ape" -> "audio/x-ape"
            "dsf", "dff" -> "audio/x-dsd"
            "mid", "midi" -> "audio/midi"
            else -> null
        }
    }

    /**
     * Resolves a clean, standardized human-readable label (e.g., "FLAC", "MP3", "WAV", "M4A", "OPUS").
     */
    fun label(
        mimeType: String?,
        uri: Uri? = null,
        pathOrDisplayName: String? = null
    ): String {
        val canonicalMime = media3MimeType(mimeType, uri, pathOrDisplayName).orEmpty()
        return when {
            canonicalMime == "audio/flac" -> "FLAC"
            canonicalMime == "audio/mpeg" -> "MP3"
            canonicalMime == "audio/mp4" -> "M4A"
            canonicalMime == "audio/aac" -> "AAC"
            canonicalMime == "audio/alac" -> "ALAC"
            canonicalMime == "audio/ogg" -> "OGG"
            canonicalMime == "audio/opus" -> "OPUS"
            canonicalMime == "audio/wav" -> "WAV"
            canonicalMime == "audio/aiff" -> "AIFF"
            canonicalMime == "audio/x-ms-wma" -> "WMA"
            canonicalMime == "audio/x-matroska" -> "MKA"
            canonicalMime == "audio/webm" -> "WEBM"
            canonicalMime == "audio/amr" || canonicalMime == "audio/amr-wb" -> "AMR"
            canonicalMime == "audio/3gpp" -> "3GP"
            canonicalMime == "audio/x-ape" -> "APE"
            canonicalMime == "audio/x-dsd" -> "DSD"
            canonicalMime == "audio/midi" -> "MIDI"
            else -> {
                val ext = extractExtension(uri, pathOrDisplayName)
                ext?.uppercase() ?: "AUDIO"
            }
        }
    }

    /**
     * Returns true if the audio format is lossless (FLAC, WAV, ALAC, AIFF, DSD, APE).
     */
    fun isLossless(
        mimeType: String?,
        uri: Uri? = null,
        pathOrDisplayName: String? = null
    ): Boolean {
        val lbl = label(mimeType, uri, pathOrDisplayName)
        return lbl in LOSSLESS_FORMATS
    }

    /**
     * Returns a formatted quality badge string (e.g. "FLAC • Lossless", "MP3", "YouTube Music").
     */
    fun qualityBadge(
        mimeType: String?,
        uri: Uri? = null,
        pathOrDisplayName: String? = null,
        isOnline: Boolean = false
    ): String {
        if (isOnline) return "YouTube Music"
        val formatLabel = label(mimeType, uri, pathOrDisplayName)
        return if (isLossless(mimeType, uri, pathOrDisplayName)) {
            "$formatLabel • Lossless"
        } else {
            formatLabel
        }
    }

    private val LOSSLESS_FORMATS = setOf("FLAC", "WAV", "ALAC", "AIFF", "DSD", "APE")

    private fun extractExtension(uri: Uri?, pathOrDisplayName: String?): String? {
        val fromPath = pathOrDisplayName
            ?.substringAfterLast('/', pathOrDisplayName)
            ?.substringAfterLast('.', "")
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() && it.length <= 6 && it.all { char -> char.isLetterOrDigit() } }

        if (fromPath != null) return fromPath

        val uriPath = uri?.path.orEmpty()
        val fromUriPath = uriPath
            .substringAfterLast('/', uriPath)
            .substringAfterLast('.', "")
            .trim()
            .lowercase()
            .takeIf { it.isNotEmpty() && it.length <= 6 && it.all { char -> char.isLetterOrDigit() } }

        if (fromUriPath != null) return fromUriPath

        val lastSegment = uri?.lastPathSegment.orEmpty()
        return lastSegment
            .substringAfterLast('.', "")
            .trim()
            .lowercase()
            .takeIf { it.isNotEmpty() && it.length <= 6 && it.all { char -> char.isLetterOrDigit() } }
    }
}
