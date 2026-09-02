package com.kaon.music.core.online

/**
 * Audio codec/container type preference for online YouTube playback.
 *
 * Implements Metrolist audio format preferences:
 * - AUTO: Balances bitrate and codec support across all networks.
 * - OPUS: Prioritizes Opus audio streams (WebM container, itag 251/250/249).
 * - AAC: Prioritizes AAC/MP4A audio streams (M4A container, itag 140/141).
 */
enum class AudioType(val displayName: String, val codecIdentifier: String) {
    AUTO("Auto (Best Format)", "auto"),
    OPUS("Opus (WebM)", "opus"),
    AAC("AAC (M4A)", "mp4a")
}
