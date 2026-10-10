package com.kaon.music.core.model

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Error-mapping boundary rules (Phase 6A §10, §11).
 *
 * The invariant under test: exceptions become *typed* errors at the boundary, cancellation is never
 * an error, and no low-level type or message string reaches the user.
 */
class ErrorMapperTest {

    @Test
    fun `socket timeout maps to Timeout`() {
        assertEquals(KaonError.Kind.TIMEOUT, ErrorMapper.fromThrowable(java.net.SocketTimeoutException("t")).kind)
    }

    @Test
    fun `unknown host and refused connection map to Offline`() {
        assertEquals(KaonError.Kind.OFFLINE, ErrorMapper.fromThrowable(java.net.UnknownHostException("x")).kind)
        assertEquals(KaonError.Kind.OFFLINE, ErrorMapper.fromThrowable(java.net.ConnectException("x")).kind)
    }

    @Test
    fun `generic IO maps to Network`() {
        assertEquals(KaonError.Kind.NETWORK, ErrorMapper.fromThrowable(java.io.IOException("reset")).kind)
    }

    @Test
    fun `resolver failures map to their typed errors`() {
        val rate = ErrorMapper.fromThrowable(com.kaon.music.core.playback.RateLimitException("limit"))
        assertEquals(KaonError.Kind.RATE_LIMITED, rate.kind)

        val rejected = ErrorMapper.fromThrowable(
            com.kaon.music.core.playback.StreamResolutionException("no stream")
        )
        assertEquals(KaonError.Kind.PROVIDER_REJECTED, rejected.kind)
    }

    @Test
    fun `cancellation propagates as cancellation and is never an error`() {
        val cancelled = CancellationException("user left the screen")
        try {
            ErrorMapper.fromThrowable(cancelled)
            org.junit.Assert.fail("cancellation must be rethrown, not mapped")
        } catch (expected: CancellationException) {
            // The same instance, not a wrapper: cancellation context survives.
            org.junit.Assert.assertSame(cancelled, expected)
        }
    }

    @Test
    fun `unexpected exceptions map to Unexpected and the cause description stays internal`() {
        val error = ErrorMapper.fromThrowable(IllegalStateException("db cursor closed at 0x1f"))
        assertTrue(error is KaonError.Unexpected)
        assertEquals(KaonError.Kind.UNEXPECTED, error.kind)

        // The user message contains no exception internals; only Unexpected.causeDescription does,
        // and that field is for the log at the boundary, never for the UI.
        assertTrue(!error.userMessage.contains("cursor"))
        assertTrue(!error.userMessage.contains("0x1f"))
    }

    @Test
    fun `every error carries user-facing wording`() {
        val errors = listOf(
            KaonError.Offline(),
            KaonError.Timeout(),
            KaonError.Network(),
            KaonError.ProviderRejected(),
            KaonError.NoPlayableFormat(),
            KaonError.SourceExpired(),
            KaonError.RateLimited(),
            KaonError.PlaybackFailed("Song"),
            KaonError.PermissionDenied(),
            KaonError.LibraryFailed(),
            KaonError.Unexpected()
        )
        errors.forEach { error ->
            assertTrue("userMessage must be non-blank for ${error::class.simpleName}", error.userMessage.isNotBlank())
        }
    }
}
