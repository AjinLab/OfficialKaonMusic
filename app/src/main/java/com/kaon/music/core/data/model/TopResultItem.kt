package com.kaon.music.core.data.model

import android.net.Uri

enum class TopResultType {
    SONG,
    VIDEO,
    ALBUM,
    ARTIST,
    PLAYLIST
}

/**
 * Domain model representing the featured Top Result card from YouTube Music search.
 */
data class TopResultItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: TopResultType,
    val thumbnailUri: Uri? = null,
    val track: Track? = null,
    val album: Album? = null,
    val artist: Artist? = null,
    val playlist: OnlinePlaylist? = null
)
