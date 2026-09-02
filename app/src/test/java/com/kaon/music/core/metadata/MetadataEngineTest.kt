package com.kaon.music.core.metadata

import com.landofoz.musicmeta.ApiKeyConfig
import com.landofoz.musicmeta.EnrichmentEngine
import com.landofoz.musicmeta.EnrichmentIdentifiers
import com.landofoz.musicmeta.EnrichmentRequest
import com.landofoz.musicmeta.http.DefaultHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MetadataEngineTest {

    @Test
    fun testDefaultEngineInitialization() {
        val httpClient = DefaultHttpClient("KaonMusic/1.0.0 (contact@kaon.music)")
        val defaultEngine = EnrichmentEngine.Builder()
            .httpClient(httpClient)
            .withDefaultProviders()
            .build()
        assertEquals(9, defaultEngine.getProviders().size)

        val fullEngine = EnrichmentEngine.Builder()
            .httpClient(httpClient)
            .apiKeys(ApiKeyConfig(lastFmKey = "test_key", fanartTvProjectKey = "fanart_key", discogsPersonalToken = "discogs_key"))
            .withDefaultProviders()
            .build()
        assertEquals(12, fullEngine.getProviders().size)

        val providerIds = fullEngine.getProviders().map { it.id }.toSet()
        val expected = setOf(
            "musicbrainz", "coverartarchive", "wikidata", "wikipedia",
            "deezer", "deezer-similar-albums", "itunes", "listenbrainz",
            "lrclib", "lastfm", "fanarttv", "discogs"
        )
        assertEquals(expected, providerIds)
    }

    @Test
    fun testEnrichmentRequestConstruction() {
        val trackReq = EnrichmentRequest.ForTrack(
            identifiers = EnrichmentIdentifiers(),
            title = "Midnight Echoes",
            artist = "Solaris",
            album = "Deep Space",
            durationMs = 200000L
        )
        assertEquals("Midnight Echoes", trackReq.title)
        assertEquals("Solaris", trackReq.artist)
        assertEquals("Deep Space", trackReq.album)

        val albumReq = EnrichmentRequest.ForAlbum(
            identifiers = EnrichmentIdentifiers(),
            title = "Deep Space",
            artist = "Solaris",
            year = 2024,
            trackCount = 10
        )
        assertEquals("Deep Space", albumReq.title)
        assertEquals("Solaris", albumReq.artist)

        val artistReq = EnrichmentRequest.ForArtist(
            identifiers = EnrichmentIdentifiers(),
            name = "Solaris"
        )
        assertEquals("Solaris", artistReq.name)
    }
}
