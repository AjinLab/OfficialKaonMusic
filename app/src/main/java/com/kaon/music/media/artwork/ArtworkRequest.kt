package com.kaon.music.media.artwork

import com.kaon.music.media.model.Song
import java.security.MessageDigest

data class ArtworkRequest(
    val albumId: Long,
    val size: ArtworkSize,
    val song: Song? = null
) {
    val baseKey: String by lazy {
        val rawKey = song?.let {
            val idKey = it.artworkHash ?: it.artworkPath ?: it.id.toString()
            val timeKey = if (it.dateModified > 0) it.dateModified else it.dateAdded
            "${idKey}_${timeKey}"
        } ?: "album_$albumId"
        try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(rawKey.toByteArray(Charsets.UTF_8))
            digest.toHexString()
        } catch (e: Exception) {
            rawKey.hashCode().toUInt().toString(16)
        }
    }

    val cacheKey: String by lazy {
        "${baseKey}_${size.pixels}"
    }
}

private val hexChars = "0123456789abcdef".toCharArray()

private fun ByteArray.toHexString(): String {
    val result = CharArray(size * 2)
    for (index in indices) {
        val value = this[index].toInt() and 0xFF
        result[index * 2] = hexChars[value ushr 4]
        result[index * 2 + 1] = hexChars[value and 0x0F]
    }
    return String(result)
}
