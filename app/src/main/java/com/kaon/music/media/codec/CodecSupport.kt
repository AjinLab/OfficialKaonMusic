package com.kaon.music.media.codec

import android.media.MediaCodecList

object CodecSupport {

    fun isMimeTypeSupported(mimeType: String): Boolean {
        try {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            for (codecInfo in codecList.codecInfos) {
                if (codecInfo.isEncoder) continue
                for (type in codecInfo.supportedTypes) {
                    if (type.equals(mimeType, ignoreCase = true)) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback to true if checking fails
        }
        
        // Basic fallback for standard codecs on typical Android devices
        val lowerMime = mimeType.lowercase()
        return lowerMime.contains("mpeg") || lowerMime.contains("mp3") || lowerMime.contains("flac") || 
               lowerMime.contains("wav") || lowerMime.contains("wave") || lowerMime.contains("ogg") || 
               lowerMime.contains("opus") || lowerMime.contains("aac") || lowerMime.contains("3gpp") ||
               lowerMime.contains("amr")
    }
}
