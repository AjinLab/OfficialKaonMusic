package com.kaon.music.core.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one transient-message channel (Phase 6A §13): messages posted from anywhere are received by
 * the single root collector, and a burst is not silently dropped.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserMessageBusTest {

    @Test
    fun `a posted message reaches the collector`() = runTest {
        val bus = UserMessageBus()
        val received = mutableListOf<UserMessageBus.UserMessage>()
        val job = launch(Dispatchers.Unconfined) { bus.messages.collect { received += it } }

        bus.post("Playback failed: Song")

        advanceUntilIdle()
        job.cancel()
        assertEquals(listOf("Playback failed: Song"), received.map { it.text })
    }

    @Test
    fun `a burst of messages is buffered, not dropped`() = runTest {
        val bus = UserMessageBus()
        val received = mutableListOf<UserMessageBus.UserMessage>()
        val job = launch(Dispatchers.Unconfined) { bus.messages.collect { received += it } }

        // More than the buffer would hold without a suspending emitter — tryEmit + capacity keeps
        // them all because the collector here drains as fast as emissions arrive.
        repeat(12) { bus.post("message $it") }

        advanceUntilIdle()
        job.cancel()
        assertEquals(12, received.size)
    }

    @Test
    fun `typed errors post their wording and kind`() = runTest {
        val bus = UserMessageBus()
        val received = mutableListOf<UserMessageBus.UserMessage>()
        val job = launch(Dispatchers.Unconfined) { bus.messages.collect { received += it } }

        bus.post(KaonError.PlaybackFailed(trackTitle = "Song"))
        bus.post(KaonError.Offline())

        advanceUntilIdle()
        job.cancel()
        assertEquals(KaonError.Kind.PLAYBACK_FAILED, received[0].kind)
        assertEquals("Unable to play: Song", received[0].text)
        assertEquals(KaonError.Kind.OFFLINE, received[1].kind)
        assertTrue(received[1].text.isNotBlank())
    }

    @Test
    fun `messages are not replayed after a new collector subscribes`() = runTest {
        val bus = UserMessageBus()
        bus.post("seen once")

        val received = mutableListOf<UserMessageBus.UserMessage>()
        val job = launch(Dispatchers.Unconfined) { bus.messages.collect { received += it } }
        advanceUntilIdle()
        job.cancel()

        // No replay: a message shows once, not again after rotation or re-collection.
        assertEquals(0, received.size)
    }
}
