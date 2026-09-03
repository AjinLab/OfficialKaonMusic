package com.kaon.music.core.playback

/**
 * Keys for the metadata Kaon attaches to a Media3 [androidx.media3.common.MediaItem].
 *
 * Shared rather than private to [PlaybackFacade] because the playback service reads the same extras
 * when it snapshots and restores the queue. Two copies of these strings had already drifted once:
 * the service's restore path built items without extras at all, so a restored streamed track lost its
 * provider identity and fell back to URI-scheme sniffing.
 */
internal object MediaItemExtras {
    const val MEDIA_STORE_ID = "kaon.media_store_id"
    const val SOURCE = "kaon.source"
    const val YOUTUBE_VIDEO_ID = "kaon.youtube_video_id"
    const val MIME_TYPE = "kaon.mime_type"
    const val ALBUM_ID = "kaon.album_id"
    const val DURATION_MS = "kaon.duration_ms"
    const val SIZE_BYTES = "kaon.size_bytes"

    const val SOURCE_YOUTUBE = "YOUTUBE"
    const val SOURCE_LOCAL = "LOCAL"
}
