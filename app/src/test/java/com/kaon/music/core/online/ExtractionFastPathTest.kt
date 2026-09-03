package com.kaon.music.core.online

import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.strategy.ContentAwareFallbackStrategy
import com.metrolist.innertubex.extraction.strategy.ClientSelectionRequest
import com.metrolist.innertubex.extraction.strategy.JavaScriptRequirement
import com.metrolist.innertubex.extraction.strategy.PlaybackClientCatalog
import com.metrolist.innertubex.extraction.strategy.PlaybackTransport
import com.metrolist.innertubex.extraction.strategy.PlaybackTransportPreference
import com.metrolist.innertubex.extraction.strategy.PoTokenRequirement
import com.metrolist.innertubex.extraction.strategy.WebViewRequirement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the cheap-first extraction contract.
 *
 * The fast path Kaon depends on is a property of InnerTubeX's client catalog, not of Kaon code: the
 * first client it tries must need no signature cipher, no n-parameter transform, no WebView, and no
 * PoToken. That is what removes `player.js` retrieval, QuickJS execution, and BotGuard attestation
 * from a normal track start.
 *
 * Because it lives in a dependency, a version bump could silently reorder it and reintroduce hundreds
 * of milliseconds per cold resolve with no compile error and no test failure anywhere else. These
 * assertions fail loudly instead.
 */
class ExtractionFastPathTest {

    /** Kaon's real hints: progressive audio only — no HLS, no SABR (see YouTubeStreamExtractor). */
    private fun kaonHints(): ContentHints = ContentHints().withStreamCapabilities(
        allowHls = false,
        allowSabr = false,
        allowBoundedRange = true
    )

    /**
     * InnerTubeX types surface as platform types here (its Kotlin metadata comes from a newer
     * compiler than this module uses), so manifests are unwrapped explicitly.
     */
    private fun com.metrolist.innertubex.extraction.strategy.SelectedClient.manifestOrFail() =
        requireNotNull(manifest) { "selected client has no manifest" }

    private fun selection(
        javaScriptRuntimeAvailable: Boolean = true,
        webViewAvailable: Boolean = true
    ) = ContentAwareFallbackStrategy().selectClients(
        ClientSelectionRequest(
            hints = kaonHints(),
            authenticated = false,
            premium = false,
            availablePoTokenProviders = emptySet(),
            javaScriptRuntimeAvailable = javaScriptRuntimeAvailable,
            webViewAvailable = webViewAvailable,
            fastPathOnly = false,
            transportPreference = PlaybackTransportPreference.DIRECT,
            excludedClients = emptySet()
        )
    )

    @Test
    fun `the first client tried needs no cipher, no webview and no po token`() {
        val manifest = selection().candidates.first().manifestOrFail()
        val request = requireNotNull(manifest.request)

        assertEquals(
            "VISIONOS is the cheapest playback client; losing first place means paying for JS again",
            "VISIONOS_0_1",
            manifest.id
        )
        assertEquals(JavaScriptRequirement.NEVER, request.signatureCipher)
        assertEquals(JavaScriptRequirement.NEVER, request.nTransform)
        assertEquals(WebViewRequirement.NONE, request.webView)
        assertEquals(PoTokenRequirement.NONE, manifest.poTokens.player.requirement)
        assertEquals(PoTokenRequirement.NONE, manifest.poTokens.gvs.requirement)
        assertFalse("a signature timestamp implies a player.js fetch", request.signatureTimestamp)
        assertTrue(
            "the fast client must deliver a progressive URL Media3 can open directly",
            manifest.transports.contains(PlaybackTransport.DIRECT)
        )
    }

    @Test
    fun `the fast client is chosen even with no javascript runtime or webview`() {
        // Availability of the expensive capabilities must not change which client is tried first;
        // otherwise the fast path would be an accident of device capability rather than a policy.
        val withCapabilities = selection().candidates.first().manifestOrFail().id
        val withoutCapabilities =
            selection(javaScriptRuntimeAvailable = false, webViewAvailable = false)
                .candidates.first().manifestOrFail().id

        assertEquals(withCapabilities, withoutCapabilities)
        assertEquals("VISIONOS_0_1", withoutCapabilities)
    }

    @Test
    fun `cipher and po token clients exist but rank below the fast client`() {
        // Fallback capability must remain: VISIONOS is not guaranteed to work for every video.
        val candidates = selection().candidates
        val ids = candidates.map { it.manifestOrFail().id }

        assertTrue("fallback clients must remain available", candidates.size > 1)

        val expensive = candidates.filter {
            val manifest = it.manifestOrFail()
            manifest.request.signatureCipher != JavaScriptRequirement.NEVER ||
                manifest.poTokens.gvs.requirement != PoTokenRequirement.NONE
        }
        assertTrue("a cipher/PoToken fallback must still exist", expensive.isNotEmpty())
        assertTrue(
            "every expensive client must rank below the fast one: $ids",
            expensive.all { candidates.indexOf(it) > 0 }
        )
    }

    @Test
    fun `sabr clients rank last because Kaon cannot play them`() {
        // Kaon requests allowSabr=false and rejects a SABR bootstrap outright, so a SABR client
        // reaching the top of the list would waste a whole player request per track start.
        val candidates = selection().candidates
        val firstSabr = candidates.indexOfFirst {
            it.manifestOrFail().transports.contains(PlaybackTransport.SABR)
        }
        val lastDirect = candidates.indexOfLast {
            it.manifestOrFail().transports.contains(PlaybackTransport.DIRECT)
        }

        if (firstSabr >= 0 && lastDirect >= 0) {
            assertTrue(
                "SABR-only clients must not outrank progressive ones",
                firstSabr > lastDirect
            )
        }
    }

    @Test
    fun `the catalog still exposes the clients Kaon names`() {
        // YouTubeStreamExtractor excludes WEB_REMIX by id after a 403; a rename would make that a
        // silent no-op.
        val catalog = PlaybackClientCatalog
        assertTrue("WEB_REMIX is referenced by the 403 recovery path", catalog.find("WEB_REMIX") != null)
        assertTrue(catalog.find("VISIONOS_0_1") != null)
    }
}
