package com.kaon.music.feature.player

import android.content.Intent
import com.kaon.music.core.data.model.ProviderId
import com.kaon.music.core.data.model.Track

/**
 * Helper to construct share text and Intent for tracks.
 */
object TrackShareHelper {

    fun createShareText(track: Track): String {
        val videoId = track.externalId?.takeIf { track.provider == ProviderId.YOUTUBE && it.isNotBlank() }
        return if (videoId != null) {
            "Now listening to: ${track.displayTitle} by ${track.displayArtist}\nhttps://www.youtube.com/watch?v=$videoId"
        } else {
            "Now listening to: ${track.displayTitle} by ${track.displayArtist} on Kaon Music"
        }
    }

    fun createShareIntent(track: Track): Intent {
        return Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, createShareText(track))
            type = "text/plain"
        }
    }
}
