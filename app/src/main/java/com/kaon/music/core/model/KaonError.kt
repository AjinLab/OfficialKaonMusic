package com.kaon.music.core.model

/**
 * Kaon's typed error model (ARCHITECTURE.md §5.1).
 *
 * Errors cross layer boundaries as values, not exceptions. Every variant carries the user-facing
 * wording in [userMessage] — one place per error for what the user is told — while the *type* is
 * what code branches on. A failure must never render as "nothing exists": UI distinguishes
 * [Kind.LOADING_FAILED] from genuinely empty results.
 *
 * Not every internal exception becomes a user-visible error. [fromThrowable] keeps genuine
 * cancellation as cancellation, and callers decide whether a typed error is surfaced, retried
 * internally, or dropped as noise. The boundary is documented in ErrorMapper.
 */
sealed interface KaonError {

    enum class Kind {
        OFFLINE,
        TIMEOUT,
        NETWORK,
        PROVIDER_REJECTED,
        NO_PLAYABLE_FORMAT,
        SOURCE_EXPIRED,
        RATE_LIMITED,
        PLAYBACK_FAILED,
        PERMISSION_DENIED,
        LIBRARY_FAILED,
        UNEXPECTED
    }

    /** What the user is told. Wording lives here so it cannot drift per screen. */
    val userMessage: String

    /** Coarse category for UI decisions (retry buttons, offline banners). */
    val kind: Kind

    // ---- Network ----

    data class Offline(override val userMessage: String = "You're offline — showing what's on your device") :
        KaonError {
        override val kind get() = Kind.OFFLINE
    }

    data class Timeout(override val userMessage: String = "The request took too long. Try again.") :
        KaonError {
        override val kind get() = Kind.TIMEOUT
    }

    data class Network(override val userMessage: String = "Network error. Check your connection.") :
        KaonError {
        override val kind get() = Kind.NETWORK
    }

    // ---- Resolution / provider ----

    data class ProviderRejected(
        override val userMessage: String = "This track was rejected by the provider."
    ) : KaonError {
        override val kind get() = Kind.PROVIDER_REJECTED
    }

    data class NoPlayableFormat(
        override val userMessage: String = "No playable audio format found for this track."
    ) : KaonError {
        override val kind get() = Kind.NO_PLAYABLE_FORMAT
    }

    data class SourceExpired(override val userMessage: String = "This stream expired — retrying.") :
        KaonError {
        override val kind get() = Kind.SOURCE_EXPIRED
    }

    data class RateLimited(override val userMessage: String = "Too many requests — waiting a moment.") :
        KaonError {
        override val kind get() = Kind.RATE_LIMITED
    }

    // ---- Playback ----

    data class PlaybackFailed(
        val trackTitle: String,
        override val userMessage: String = "Unable to play: $trackTitle"
    ) : KaonError {
        override val kind get() = Kind.PLAYBACK_FAILED
    }

    // ---- Library ----

    data class PermissionDenied(
        override val userMessage: String = "Storage permission is needed to scan your library."
    ) : KaonError {
        override val kind get() = Kind.PERMISSION_DENIED
    }

    data class LibraryFailed(
        override val userMessage: String = "Library sync failed. Your existing library is safe."
    ) : KaonError {
        override val kind get() = Kind.LIBRARY_FAILED
    }

    // ---- Unexpected ----

    data class Unexpected(
        override val userMessage: String = "Something went wrong. Please try again.",
        /** Never rendered; kept for the log line at the boundary. */
        val causeDescription: String? = null
    ) : KaonError {
        override val kind get() = Kind.UNEXPECTED
    }
}
