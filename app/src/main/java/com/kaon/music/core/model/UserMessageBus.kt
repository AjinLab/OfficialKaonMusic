package com.kaon.music.core.model

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The one app-level channel for transient user messages (ARCHITECTURE.md §5.2).
 *
 * Owned by [com.kaon.music.app.di.AppContainer], collected exactly once by the root scaffold. A
 * message can originate anywhere — a screen ViewModel, the playback facade outliving the screen that
 * started playback, a sync worker — and reaches the user regardless of which destination is visible.
 *
 * Buffering: `extraBufferCapacity = 8` with no replay. Extra capacity means a message emitted while
 * the collector is busy composing is *not* silently dropped (spec §13); no replay means a message is
 * shown once, not again after rotation. Messages are data (`UserMessage`), never strings keyed by
 * the emitting site.
 */
class UserMessageBus {

    data class UserMessage(
        val text: String,
        /** Coarse category; UI may style retry-able errors differently. */
        val kind: KaonError.Kind = KaonError.Kind.UNEXPECTED
    )

    private val _messages = MutableSharedFlow<UserMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UserMessage> = _messages.asSharedFlow()

    /** Posts a message. Cheap and non-suspending; drops-oldest is unacceptable, hence the buffer. */
    fun post(text: String, kind: KaonError.Kind = KaonError.Kind.UNEXPECTED) {
        _messages.tryEmit(UserMessage(text = text, kind = kind))
    }

    /** Posts a typed [KaonError]'s user-facing wording with its kind. */
    fun post(error: KaonError) {
        post(text = error.userMessage, kind = error.kind)
    }
}
