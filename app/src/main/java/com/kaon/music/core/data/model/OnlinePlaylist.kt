package com.kaon.music.core.data.model

/**
 * An online playlist discovered via YouTube Music search.
 *
 * [thumbnailUrl] is a provider URL, not an `android.net.Uri`: the domain model states what the image
 * *is* and the UI parses it at the Coil boundary (ARCHITECTURE.md §3.1).
 */
data class OnlinePlaylist(
    val playlistId: String,
    val title: String,
    val author: String = "YouTube Music",
    val songCountText: String? = null,
    val thumbnailUrl: String? = null
)
