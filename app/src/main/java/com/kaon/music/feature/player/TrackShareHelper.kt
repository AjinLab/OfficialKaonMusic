package com.kaon.music.feature.player

import android.content.Intent
import com.kaon.music.core.data.model.Track

/**
 * Helper to construct share text and Intent for tracks.
 */
object TrackShareHelper {

    fun createShareText(track: Track): String {
        return if (track.source == "YOUTUBE" && !track.youtubeVideoId.isNullOrBlank()) {
            "Now listening to: ${track.displayTitle} by ${track.displayArtist}\nhttps://www.youtube.com/watch?v=${track.youtubeVideoId}"
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
