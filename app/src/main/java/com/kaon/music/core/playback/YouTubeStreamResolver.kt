package com.kaon.music.core.playback

import com.metrolist.innertube.NewPipeExtractor
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.response.PlayerResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Resolves YouTube audio streams on-demand with client fallback and signature deciphering.
 */
object YouTubeStreamResolver {

    private val fallbackClients = listOf(
        YouTubeClient.ANDROID_VR_1_65_10,
        YouTubeClient.VISIONOS,
        YouTubeClient.WEB_REMIX,
        YouTubeClient.TVHTML5
    )

    suspend fun resolveStreamUrl(videoId: String): Result<String> = withContext(Dispatchers.IO) {
        for (client in fallbackClients) {
            try {
                val playerResult = YouTube.player(videoId, client = client)
                val response = playerResult.getOrNull() ?: continue

                if (response.playabilityStatus.status == "OK" && response.streamingData != null) {
                    val streamUrl = findBestAudioStream(response.streamingData!!, videoId)
                    if (streamUrl != null) {
                        return@withContext Result.success(streamUrl)
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed resolving stream with client ${client.clientName} for video $videoId")
            }
        }

        // Fallback to default WEB_REMIX call
        try {
            val response = YouTube.player(videoId, client = YouTubeClient.WEB_REMIX).getOrNull()
            if (response?.streamingData != null) {
                val streamUrl = findBestAudioStream(response.streamingData!!, videoId)
                if (streamUrl != null) {
                    return@withContext Result.success(streamUrl)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Final fallback stream resolution failed for video $videoId")
        }

        Result.failure(IllegalStateException("No playable audio stream found for videoId: $videoId"))
    }

    private fun findBestAudioStream(
        streamingData: PlayerResponse.StreamingData,
        videoId: String
    ): String? {
        val allFormats = (streamingData.adaptiveFormats.orEmpty() + streamingData.formats.orEmpty())
        val audioFormats = allFormats.filter { it.mimeType.startsWith("audio/") }

        // Sort by audio bitrate descending (itag 251 Opus / itag 140 AAC preferred)
        val sortedAudio = audioFormats.sortedByDescending { it.bitrate ?: 0 }

        for (format in sortedAudio) {
            if (!format.url.isNullOrBlank()) {
                return format.url
            }
            if (!format.signatureCipher.isNullOrBlank()) {
                val deciphered = NewPipeExtractor.getStreamUrl(format, videoId)
                if (!deciphered.isNullOrBlank()) {
                    return deciphered
                }
            }
        }

        return null
    }
}
