package com.kaon.music.core.data.model

import android.net.Uri

/**
 * Domain model representing an online playlist discovered via YouTube Music search.
 */
data class OnlinePlaylist(
    val playlistId: String,
    val title: String,
    val author: String = "YouTube Music",
    val songCountText: String? = null,
    val thumbnailUri: Uri? = null
)
