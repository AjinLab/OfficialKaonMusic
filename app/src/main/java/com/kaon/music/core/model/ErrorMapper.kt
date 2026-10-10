package com.kaon.music.core.model

import kotlinx.coroutines.CancellationException

/**
 * The boundary where low-level exceptions become [KaonError].
 *
 * ARCHITECTURE.md §5.1: exceptions do not cross layer boundaries untyped. [fromThrowable] is the one
 * translation point — ViewModel and UI code never pattern-match on `IOException` or provider
 * exception classes, and no exception *message* string-matching appears anywhere (message text is
 * not a contract).
 *
 * Mapping policy:
 * - **Cancellation is never an error.** A `CancellationException` passes through untouched; callers
 *   up the stack rethrow it (the standard Kotlin contract). Converting it would turn a screen the
 *   user left into a fake failure.
 * - **Timeouts are distinct** from general I/O: retry affordances differ.
 * - **Resolver/provider failures arrive pre-typed** (`RateLimitException`, `StreamResolutionException`)
 *   and map to their KaonError counterparts; everything else provider-side collapses to
 *   [KaonError.Unexpected] with a cause description for the log, not for the UI.
 */
object ErrorMapper {

    fun fromThrowable(t: Throwable): KaonError = when (t) {
        // Never converted: cancellation must stay cancellation (ARCHITECTURE.md §5.1). This branch
        // exists so a caller that forgets to rethrow still cannot render a cancellation as a failure.
        is CancellationException -> throw t

        is java.net.SocketTimeoutException -> KaonError.Timeout()
        is java.net.UnknownHostException,
        is java.net.ConnectException -> KaonError.Offline()

        is java.io.IOException -> KaonError.Network()

        is com.kaon.music.core.playback.RateLimitException -> KaonError.RateLimited()

        is com.kaon.music.core.playback.StreamResolutionException -> KaonError.ProviderRejected()

        is IllegalStateException -> KaonError.Unexpected(causeDescription = t.message)
        is IllegalArgumentException -> KaonError.Unexpected(causeDescription = t.message)

        else -> KaonError.Unexpected(causeDescription = "${t::class.simpleName}: ${t.message}")
    }
}
