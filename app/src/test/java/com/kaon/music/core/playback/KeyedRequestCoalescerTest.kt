package com.kaon.music.core.playback

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class KeyedRequestCoalescerTest {

    @Test
    fun `concurrent requests for the same key share one work execution`() = runTest {
        val coalescer = KeyedRequestCoalescer<String, Int>(backgroundScope)
        var executions = 0
        val gate = CompletableDeferred<Int>()

        val a = async { coalescer.execute("k") { executions++; gate.await() } }
        yield() // A registers and its shared work is scheduled
        val b = async { coalescer.execute("k") { executions++; gate.await() } }
        yield() // B joins the existing in-flight request

        gate.complete(42)
        assertEquals(42, a.await())
        assertEquals(42, b.await())
        assertEquals(1, executions)
        assertEquals(0, coalescer.pendingCount())
    }

    @Test
    fun `a cancelled caller does not cancel the shared work`() = runTest {
        val coalescer = KeyedRequestCoalescer<String, Int>(backgroundScope)
        var executions = 0
        val gate = CompletableDeferred<Int>()

        val leader = launch {
            coalescer.execute("k") { executions++; gate.await() }
        }
        yield() // leader registered, shared work queued behind it
        leader.cancel()
        leader.join()

        // A new caller joins the still-registered in-flight request; the shared work
        // survives the leader's cancellation and serves it.
        val joined = async { coalescer.execute("k") { executions++; gate.await() } }
        gate.complete(7)
        assertEquals(7, joined.await())
        assertEquals(1, executions)
        assertEquals(0, coalescer.pendingCount())
    }

    @Test
    fun `failed work propagates to waiters and the key is reusable`() = runTest {
        val coalescer = KeyedRequestCoalescer<String, Int>(backgroundScope)
        var executions = 0

        val failed = async {
            runCatching { coalescer.execute("k") { executions++; throw IllegalStateException("boom") } }
        }
        yield()
        assertTrue(failed.await().exceptionOrNull() is IllegalStateException)
        assertEquals(0, coalescer.pendingCount())

        val recovered = async { coalescer.execute("k") { executions++; 5 } }
        assertEquals(5, recovered.await())
        assertEquals(2, executions)
    }

    @Test
    fun `waiter cancellation on a real dispatcher leaves the shared resolution intact`() = runTest {
        // Mirrors the resolver scenario: a caller on a real dispatcher cancels while the
        // app-owned work (its own IO scope) is suspended mid-network.
        val coalescer = KeyedRequestCoalescer<String, Int>(kotlinx.coroutines.CoroutineScope(Dispatchers.IO))
        val entered = CountDownLatch(1)
        val gate = CompletableDeferred<Int>()
        var executions = 0

        val waiter = launch(Dispatchers.IO) {
            coalescer.execute("k") { executions++; entered.countDown(); gate.await() }
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        waiter.cancel()
        waiter.join()

        val done = CompletableDeferred<Int>()
        val becameLeader = CountDownLatch(1)
        launch(Dispatchers.IO) {
            done.complete(
                coalescer.execute("k") { executions++; becameLeader.countDown(); gate.await() }
            )
        }
        // The second caller must join as a waiter, not start a second execution.
        assertFalse(becameLeader.await(500, TimeUnit.MILLISECONDS))
        gate.complete(11)

        assertEquals(11, done.await())
        assertEquals(1, executions)
    }
}
