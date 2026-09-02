package com.kaon.music.core.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFormatTest {

    @Test
    fun `media3MimeType maps standard and variant MIME types accurately`() {
        // FLAC
        assertEquals("audio/flac", AudioFormat.media3MimeType("audio/flac"))
        assertEquals("audio/flac", AudioFormat.media3MimeType("audio/x-flac"))
        assertEquals("audio/flac", AudioFormat.media3MimeType("application/flac"))
        assertEquals("audio/flac", AudioFormat.media3MimeType("application/x-flac"))

        // MP3
        assertEquals("audio/mpeg", AudioFormat.media3MimeType("audio/mpeg"))
        assertEquals("audio/mpeg", AudioFormat.media3MimeType("audio/mp3"))
        assertEquals("audio/mpeg", AudioFormat.media3MimeType("audio/x-mp3"))
        assertEquals("audio/mpeg", AudioFormat.media3MimeType("audio/x-mpeg"))

        // M4A & AAC & ALAC
        assertEquals("audio/mp4", AudioFormat.media3MimeType("audio/mp4"))
        assertEquals("audio/mp4", AudioFormat.media3MimeType("audio/x-m4a"))
        assertEquals("audio/mp4", AudioFormat.media3MimeType("audio/m4a"))
        assertEquals("audio/aac", AudioFormat.media3MimeType("audio/aac"))
        assertEquals("audio/aac", AudioFormat.media3MimeType("audio/aacp"))
        assertEquals("audio/alac", AudioFormat.media3MimeType("audio/alac"))
        assertEquals("audio/alac", AudioFormat.media3MimeType("audio/x-alac"))

        // OGG & OPUS
        assertEquals("audio/ogg", AudioFormat.media3MimeType("audio/ogg"))
        assertEquals("audio/ogg", AudioFormat.media3MimeType("application/ogg"))
        assertEquals("audio/ogg", AudioFormat.media3MimeType("audio/vorbis"))
        assertEquals("audio/opus", AudioFormat.media3MimeType("audio/opus"))
        assertEquals("audio/opus", AudioFormat.media3MimeType("audio/x-opus+ogg"))

        // WAV & AIFF
        assertEquals("audio/wav", AudioFormat.media3MimeType("audio/wav"))
        assertEquals("audio/wav", AudioFormat.media3MimeType("audio/x-wav"))
        assertEquals("audio/wav", AudioFormat.media3MimeType("audio/wave"))
        assertEquals("audio/aiff", AudioFormat.media3MimeType("audio/aiff"))
        assertEquals("audio/aiff", AudioFormat.media3MimeType("audio/x-aiff"))

        // Other common formats
        assertEquals("audio/x-ms-wma", AudioFormat.media3MimeType("audio/x-ms-wma"))
        assertEquals("audio/x-ms-wma", AudioFormat.media3MimeType("audio/wma"))
        assertEquals("audio/x-matroska", AudioFormat.media3MimeType("audio/x-matroska"))
        assertEquals("audio/webm", AudioFormat.media3MimeType("audio/webm"))
        assertEquals("audio/amr", AudioFormat.media3MimeType("audio/amr"))
        assertEquals("audio/3gpp", AudioFormat.media3MimeType("audio/3gpp"))
        assertEquals("audio/x-ape", AudioFormat.media3MimeType("audio/ape"))
        assertEquals("audio/x-dsd", AudioFormat.media3MimeType("audio/dsf"))
        assertEquals("audio/midi", AudioFormat.media3MimeType("audio/midi"))
    }

    @Test
    fun `media3MimeType falls back to extension when mime is null or generic`() {
        assertEquals("audio/flac", AudioFormat.media3MimeType(null, pathOrDisplayName = "symphony.flac"))
        assertEquals("audio/flac", AudioFormat.media3MimeType("application/octet-stream", pathOrDisplayName = "track.flac"))
        assertEquals("audio/opus", AudioFormat.media3MimeType(null, pathOrDisplayName = "podcast.opus"))
        assertEquals("audio/wav", AudioFormat.media3MimeType(null, pathOrDisplayName = "master.wav"))
        assertEquals("audio/alac", AudioFormat.media3MimeType(null, pathOrDisplayName = "song.alac"))
        assertEquals("audio/aiff", AudioFormat.media3MimeType(null, pathOrDisplayName = "recording.aiff"))
        assertEquals("audio/mp4", AudioFormat.media3MimeType(null, pathOrDisplayName = "album.m4a"))
        assertEquals("audio/mpeg", AudioFormat.media3MimeType(null, pathOrDisplayName = "hit.mp3"))
    }

    @Test
    fun `label formats labels accurately`() {
        assertEquals("FLAC", AudioFormat.label("audio/flac"))
        assertEquals("FLAC", AudioFormat.label("audio/x-flac"))
        assertEquals("FLAC", AudioFormat.label(null, pathOrDisplayName = "song.flac"))

        assertEquals("MP3", AudioFormat.label("audio/mpeg"))
        assertEquals("MP3", AudioFormat.label("audio/mp3"))

        assertEquals("M4A", AudioFormat.label("audio/mp4"))
        assertEquals("M4A", AudioFormat.label("audio/x-m4a"))

        assertEquals("AAC", AudioFormat.label("audio/aac"))
        assertEquals("ALAC", AudioFormat.label("audio/alac"))
        assertEquals("OGG", AudioFormat.label("audio/ogg"))
        assertEquals("OPUS", AudioFormat.label("audio/opus"))
        assertEquals("WAV", AudioFormat.label("audio/wav"))
        assertEquals("AIFF", AudioFormat.label("audio/aiff"))
        assertEquals("WMA", AudioFormat.label("audio/x-ms-wma"))
        assertEquals("MKA", AudioFormat.label("audio/x-matroska"))
        assertEquals("WEBM", AudioFormat.label("audio/webm"))
        assertEquals("AMR", AudioFormat.label("audio/amr"))
        assertEquals("3GP", AudioFormat.label("audio/3gpp"))
        assertEquals("APE", AudioFormat.label("audio/x-ape"))
        assertEquals("DSD", AudioFormat.label("audio/x-dsd"))
        assertEquals("MIDI", AudioFormat.label("audio/midi"))
    }

    @Test
    fun `isLossless accurately identifies lossless formats`() {
        assertTrue(AudioFormat.isLossless("audio/flac"))
        assertTrue(AudioFormat.isLossless("audio/wav"))
        assertTrue(AudioFormat.isLossless("audio/alac"))
        assertTrue(AudioFormat.isLossless("audio/aiff"))
        assertTrue(AudioFormat.isLossless("audio/x-ape"))
        assertTrue(AudioFormat.isLossless("audio/x-dsd"))
        assertTrue(AudioFormat.isLossless(null, pathOrDisplayName = "beethoven.flac"))
        assertTrue(AudioFormat.isLossless(null, pathOrDisplayName = "studio.wav"))

        assertFalse(AudioFormat.isLossless("audio/mpeg"))
        assertFalse(AudioFormat.isLossless("audio/mp4"))
        assertFalse(AudioFormat.isLossless("audio/aac"))
        assertFalse(AudioFormat.isLossless("audio/opus"))
        assertFalse(AudioFormat.isLossless("audio/ogg"))
        assertFalse(AudioFormat.isLossless("audio/x-ms-wma"))
    }

    @Test
    fun `qualityBadge produces formatted strings`() {
        assertEquals("FLAC • Lossless", AudioFormat.qualityBadge("audio/flac"))
        assertEquals("WAV • Lossless", AudioFormat.qualityBadge("audio/wav"))
        assertEquals("ALAC • Lossless", AudioFormat.qualityBadge("audio/alac"))
        assertEquals("MP3", AudioFormat.qualityBadge("audio/mpeg"))
        assertEquals("OPUS", AudioFormat.qualityBadge("audio/opus"))
        assertEquals("YouTube Music", AudioFormat.qualityBadge("audio/webm", isOnline = true))
    }
}
