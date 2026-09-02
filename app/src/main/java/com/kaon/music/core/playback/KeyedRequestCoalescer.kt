package com.kaon.music.core.playback

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Coroutine-safe keyed in-flight request coalescer.
 *
 * Concurrent callers requesting the same [K] converge on a single execution of
 * [work]. The shared work runs in [scope] — owned by the host (application),
 * not by any caller — so:
 *  - a caller cancelling its wait never cancels the shared resolution other
 *    callers are depending on,
 *  - the initiating caller cancelling does not poison coalesced waiters,
 *  - completed (and exceptionally completed) entries are removed promptly.
 *
 * [work] must return normally (model failures in the return type, e.g. Result)
 * or complete the deferred exceptionally only for scope-level teardown.
 */
internal class KeyedRequestCoalescer<K : Any, V : Any>(private val scope: CoroutineScope) {

    private val inFlight = ConcurrentHashMap<K, CompletableDeferred<V>>()

    suspend fun execute(key: K, work: suspend () -> V): V {
        while (true) {
            val existing = inFlight[key]
            if (existing != null) return existing.await()

            val deferred = CompletableDeferred<V>()
            if (inFlight.putIfAbsent(key, deferred) != null) continue

            scope.launch {
                try {
                    deferred.complete(work())
                } catch (ce: CancellationException) {
                    // Only reachable when the owning scope is torn down; surface it
                    // so waiters unblock instead of hanging forever.
                    deferred.completeExceptionally(ce)
                } catch (t: Throwable) {
                    deferred.completeExceptionally(t)
                }
            }.invokeOnCompletion {
                inFlight.remove(key, deferred)
            }
            return deferred.await()
        }
    }

    fun pendingCount(): Int = inFlight.size
}
