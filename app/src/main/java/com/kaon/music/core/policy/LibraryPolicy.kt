package com.kaon.music.core.policy

import com.kaon.music.core.data.model.Track
import kotlin.random.Random

/**
 * Pure library presentation policy.
 *
 * ARCHITECTURE.md §5.5: sorting, ranking and recommendation are pure functions rather than logic
 * embedded in a ViewModel `combine`. That makes them unit-testable without a coroutine harness and,
 * critically, makes them deterministic — the previous inline `shuffled()` in HomeViewModel produced a
 * different result on every recomposition of the flow, so the state object was never `equals` and the
 * home feed reshuffled itself twice per second during playback.
 *
 * These functions move to `:core:model` in migration phase 4, once that module exists.
 */
object LibraryPolicy {

    /** Number of tracks in the generated mix when falling back to the whole library. */
    const val MIX_SIZE = 50

    /**
     * Builds the "Your Mix" feed.
     *
     * [seed] makes the shuffle reproducible: callers pass a value that changes only when the mix
     * should legitimately change (a session token, a manual refresh counter), never a clock or an
     * unseeded RNG. Identical inputs therefore produce an identical list, so the enclosing UI state
     * compares equal and does not re-emit.
     */
    fun buildYourMix(
        mostPlayed: List<Track>,
        allTracks: List<Track>,
        seed: Long,
        size: Int = MIX_SIZE
    ): List<Track> {
        val source = if (mostPlayed.isNotEmpty()) mostPlayed else allTracks
        if (source.isEmpty()) return emptyList()
        return source.shuffled(Random(seed)).take(size)
    }

    /** Deterministic shuffle for explicit user-initiated "shuffle play" actions. */
    fun shuffled(tracks: List<Track>, seed: Long): List<Track> =
        if (tracks.isEmpty()) emptyList() else tracks.shuffled(Random(seed))
}
