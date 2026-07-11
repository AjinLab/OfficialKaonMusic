package com.kaon.music.media.codec

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioMetadataExtractor {

    fun extract(context: Context, uriString: String, mimeTypeString: String?, path: String): AudioInfo {
        val uri = Uri.parse(uriString)
        val file = File(path)
        val format = AudioFormat.fromMimeType(mimeTypeString)
            .takeIf { it != AudioFormat.UNKNOWN }
            ?: AudioFormat.fromExtension(file.extension)

        var sampleRate: Int? = null
        var channels: Int? = null
        var bitDepth: Int? = null
        var bitrate: Int? = null

        // Try extracting using custom file parsers for lossless files (WAV, FLAC, AIFF)
        if (file.exists() && file.isFile) {
            try {
                FileInputStream(file).use { fis ->
                    when (format) {
                        AudioFormat.WAV -> {
                            val info = parseWavHeader(fis)
                            if (info != null) {
                                sampleRate = info.sampleRate
                                channels = info.channels
                                bitDepth = info.bitDepth
                            }
                        }
                        AudioFormat.FLAC -> {
                            val info = parseFlacHeader(fis)
                            if (info != null) {
                                sampleRate = info.sampleRate
                                channels = info.channels
                                bitDepth = info.bitDepth
                            }
                        }
                        AudioFormat.AIFF -> {
                            val info = parseAiffHeader(fis)
                            if (info != null) {
                                sampleRate = info.sampleRate
                                channels = info.channels
                                bitDepth = info.bitDepth
                            }
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                // Fail silently and fallback
            }
        }

        // Use MediaMetadataRetriever for bitrate and sampleRate fallback
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val rawBitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            if (rawBitrate != null) {
                bitrate = rawBitrate.toIntOrNull()
            }
            if (sampleRate == null) {
                val rawSampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                sampleRate = rawSampleRate?.toIntOrNull()
            }
        } catch (e: Exception) {
            // Ignore retriever errors
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {}
        }

        return AudioInfo(
            codec = format.displayName,
            bitrate = bitrate,
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            channels = channels,
            mimeType = mimeTypeString ?: format.mimeType
        )
    }

    private fun parseWavHeader(inputStream: InputStream): HeaderInfo? {
        val header = ByteArray(12)
        if (inputStream.read(header) != 12) return null
        if (header[0].toInt() != 'R'.code || header[1].toInt() != 'I'.code ||
            header[2].toInt() != 'F'.code || header[3].toInt() != 'F'.code) return null
        if (header[8].toInt() != 'W'.code || header[9].toInt() != 'A'.code ||
            header[10].toInt() != 'V'.code || header[11].toInt() != 'E'.code) return null

        val chunkHeader = ByteArray(8)
        while (inputStream.read(chunkHeader) == 8) {
            val chunkId = String(chunkHeader, 0, 4)
            val chunkSize = ByteBuffer.wrap(chunkHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (chunkId == "fmt ") {
                val fmtData = ByteArray(chunkSize)
                if (inputStream.read(fmtData) != chunkSize) return null
                val buffer = ByteBuffer.wrap(fmtData).order(ByteOrder.LITTLE_ENDIAN)
                val formatCode = buffer.short // 1 for PCM, 3 for IEEE Float
                val channels = buffer.short.toInt()
                val sampleRate = buffer.int
                buffer.position(buffer.position() + 6) // skip byteRate(4) and blockAlign(2)
                val bitDepth = buffer.short.toInt()
                return HeaderInfo(sampleRate, channels, bitDepth)
            } else {
                inputStream.skip(chunkSize.toLong())
            }
        }
        return null
    }

    private fun parseFlacHeader(inputStream: InputStream): HeaderInfo? {
        val magic = ByteArray(4)
        if (inputStream.read(magic) != 4) return null
        if (magic[0].toInt() != 'f'.code || magic[1].toInt() != 'L'.code ||
            magic[2].toInt() != 'a'.code || magic[3].toInt() != 'C'.code) return null

        val blockHeader = ByteArray(4)
        if (inputStream.read(blockHeader) != 4) return null
        val type = blockHeader[0].toInt() and 0x7F
        val size = ((blockHeader[1].toInt() and 0xFF) shl 16) or
                   ((blockHeader[2].toInt() and 0xFF) shl 8) or
                   (blockHeader[3].toInt() and 0xFF)

        if (type == 0 && size >= 34) {
            val streamInfo = ByteArray(size)
            if (inputStream.read(streamInfo) != size) return null

            val b10 = streamInfo[10].toInt() and 0xFF
            val b11 = streamInfo[11].toInt() and 0xFF
            val b12 = streamInfo[12].toInt() and 0xFF
            val b13 = streamInfo[13].toInt() and 0xFF

            val sampleRate = (b10 shl 12) or (b11 shl 4) or (b12 ushr 4)
            val channels = ((b12 and 0x0E) ushr 1) + 1
            val bitDepth = (((b12 and 0x01) shl 4) or (b13 ushr 4)) + 1

            return HeaderInfo(sampleRate, channels, bitDepth)
        }
        return null
    }

    private fun parseAiffHeader(inputStream: InputStream): HeaderInfo? {
        val header = ByteArray(12)
        if (inputStream.read(header) != 12) return null
        if (header[0].toInt() != 'F'.code || header[1].toInt() != 'O'.code ||
            header[2].toInt() != 'R'.code || header[3].toInt() != 'M'.code) return null
        val type = String(header, 8, 4)
        if (type != "AIFF" && type != "AIFC") return null

        val chunkHeader = ByteArray(8)
        while (inputStream.read(chunkHeader) == 8) {
            val chunkId = String(chunkHeader, 0, 4)
            val chunkSize = ByteBuffer.wrap(chunkHeader, 4, 4).order(ByteOrder.BIG_ENDIAN).int
            if (chunkId == "COMM") {
                val commData = ByteArray(chunkSize)
                if (inputStream.read(commData) != chunkSize) return null
                val buffer = ByteBuffer.wrap(commData).order(ByteOrder.BIG_ENDIAN)
                val channels = buffer.short.toInt()
                val numSampleFrames = buffer.int
                val bitDepth = buffer.short.toInt()

                val sampleRateBytes = ByteArray(10)
                buffer.get(sampleRateBytes)
                val sampleRate = parseExtendedFloat(sampleRateBytes).toInt()

                return HeaderInfo(sampleRate, channels, bitDepth)
            } else {
                inputStream.skip(chunkSize.toLong())
            }
        }
        return null
    }

    private fun parseExtendedFloat(bytes: ByteArray): Double {
        val exponent = ((bytes[0].toInt() and 0x7F) shl 8) or (bytes[1].toInt() and 0xFF)
        var mantissa = 0L
        for (i in 2..9) {
            mantissa = (mantissa shl 8) or (bytes[i].toLong() and 0xFF)
        }
        if (exponent == 0 && mantissa == 0L) return 0.0
        val exp = exponent - 16383
        return mantissa.toDouble() * java.lang.Math.pow(2.0, (exp - 63).toDouble())
    }

    private data class HeaderInfo(
        val sampleRate: Int,
        val channels: Int,
        val bitDepth: Int
    )
}
