package com.kaon.music.core.model

/**
 * Provider identity, shared by the model and extraction layers.
 *
 * This is a *temporary alias target*: Phase 4 introduced `com.kaon.music.core.data.model.ProviderId`
 * inside `:app`. Phase 5 needs a provider concept in `:core:extraction`, which cannot depend on
 * `:app`, so the type is mirrored here. `:app` bridges between them; when the Phase 4 follow-up
 * extracts a real `:core:model` module this file moves there and the mirror dies.
 */
@JvmInline
value class ProviderId(val value: String) {
    val isLocal: Boolean get() = value == LOCAL_VALUE

    override fun toString(): String = value

    companion object {
        private const val LOCAL_VALUE = "LOCAL"
        val LOCAL = ProviderId(LOCAL_VALUE)
        val YOUTUBE = ProviderId("YOUTUBE")
        fun of(raw: String?): ProviderId =
            if (raw.isNullOrBlank()) LOCAL else ProviderId(raw.trim().uppercase())
    }
}

/**
 * Where a track's audio lives, as far as extraction is concerned.
 *
 * Mirrors `core.data.model.MediaLocator` for the same reason as [ProviderId]: the resolver contract
 * speaks in locators, and extraction cannot see `:app`'s copy. Only the fields extraction needs are
 * represented.
 */
sealed interface MediaLocator {
    val provider: ProviderId
    val externalId: String?

    data class Remote(
        override val provider: ProviderId,
        override val externalId: String
    ) : MediaLocator
}
