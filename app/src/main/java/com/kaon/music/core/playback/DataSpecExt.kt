package com.kaon.music.core.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec

/**
 * Applies a resolved stream to a [DataSpec].
 *
 * Some YouTube CDN responses reject a single unbounded GET and require ranged requests. When the
 * extractor reports that, each open is bounded to [ResolvedStreamData.rangeChunkSizeBytes] and
 * ExoPlayer re-opens for the following chunk. Without this the stream fails partway through with an
 * HTTP error that looks like an expired URL.
 *
 * Extractor headers take precedence over whatever the loader put on the spec: the extractor knows
 * which client the URL was minted for, and CDN URLs are rejected when the accompanying headers do
 * not match that client.
 */
@OptIn(UnstableApi::class)
internal fun DataSpec.withResolvedStream(stream: ResolvedStreamData): DataSpec {
    val resolved = buildUpon()
        .setUri(Uri.parse(stream.url))
        .setHttpRequestHeaders(httpRequestHeaders + stream.headers)
        .build()

    val needsChunking = stream.requireBoundedRange || stream.useRangeChunks
    if (!needsChunking || stream.rangeChunkSizeBytes <= 0L) return resolved

    val boundedLength = if (length == C.LENGTH_UNSET.toLong()) {
        stream.rangeChunkSizeBytes
    } else {
        minOf(length, stream.rangeChunkSizeBytes)
    }
    return resolved.subrange(0, boundedLength)
}
