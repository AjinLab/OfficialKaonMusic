package com.kaon.music.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.ui.graphics.vector.ImageVector

enum class SettingsSection(
    val title: String,
    val description: String,
    val icon: ImageVector
) {
    AUDIO_PLAYBACK(
        title = "Audio & Playback",
        description = "Streaming quality, audio focus, silence skipping, and cache",
        icon = Icons.Default.GraphicEq
    ),
    LIBRARY_STORAGE(
        title = "Library & Storage",
        description = "MediaStore scanning, duration filters, and database maintenance",
        icon = Icons.Default.Storage
    ),
    APPEARANCE_THEME(
        title = "Appearance & Theme",
        description = "Dark themes, AMOLED pure black, accent colors, and format badges",
        icon = Icons.Default.Palette
    ),
    ONLINE_STREAMING(
        title = "Online & Streaming",
        description = "YouTube stream resolver, pre-resolution, and Wi-Fi streaming",
        icon = Icons.Default.Cloud
    ),
    HISTORY_PRIVACY(
        title = "History & Privacy",
        description = "Listening history recording, play count criteria, and logs",
        icon = Icons.Default.History
    ),
    ABOUT_INFO(
        title = "About Kaon Music",
        description = "Version, Media3/ExoPlayer engine, supported codecs, and credits",
        icon = Icons.Default.Info
    )
}
