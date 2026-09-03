package com.kaon.music.core.policy

import android.net.Uri
import com.kaon.music.core.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ARCHITECTURE.md §5.5: presentation policy is pure and deterministic.
 *
 * The regression these tests pin: `buildYourMix` used to call `shuffled()` with an unseeded RNG
 * inside a ViewModel `combine`, so the resulting UI state was never `equals` to the previous one.
 * Combined with a 500 ms playback tick as a combine source, the home feed re-emitted and reshuffled
 * twice per second during playback.
 */
class LibraryPolicyTest {

    private fun track(id: Long, title: String = "Track $id") = Track(
        id = id,
        mediaStoreId = id,
        title = title,
        artist = "Artist",
        album = "Album",
        albumId = 1L,
        durationMs = 180_000L,
        sizeBytes = 1_000L,
        dateModified = 0L,
        contentUri = null as Uri?
    )

    private val library = (1L..20L).map { track(it) }

    @Test
    fun buildYourMixIsDeterministicForTheSameSeed() {
        val first = LibraryPolicy.buildYourMix(mostPlayed = library, allTracks = emptyList(), seed = 42L)
        val second = LibraryPolicy.buildYourMix(mostPlayed = library, allTracks = emptyList(), seed = 42L)

        assertEquals(first.map { it.id }, second.map { it.id })
    }

    @Test
    fun buildYourMixChangesWhenTheSeedChanges() {
        val first = LibraryPolicy.buildYourMix(mostPlayed = library, allTracks = emptyList(), seed = 1L)
        val second = LibraryPolicy.buildYourMix(mostPlayed = library, allTracks = emptyList(), seed = 2L)

        assertNotEquals(first.map { it.id }, second.map { it.id })
    }

    @Test
    fun buildYourMixPrefersMostPlayedAndFallsBackOtherwise() {
        val mostPlayed = listOf(track(100L, "Most played"))
        val fallback = listOf(track(200L, "Fallback"))

        val fromMostPlayed = LibraryPolicy.buildYourMix(mostPlayed, fallback, seed = 7L)
        assertEquals(listOf(100L), fromMostPlayed.map { it.id })

        val fromFallback = LibraryPolicy.buildYourMix(emptyList(), fallback, seed = 7L)
        assertEquals(listOf(200L), fromFallback.map { it.id })
    }

    @Test
    fun buildYourMixIsBoundedAndLossless() {
        val large = (1L..500L).map { track(it) }
        val mix = LibraryPolicy.buildYourMix(mostPlayed = large, allTracks = emptyList(), seed = 3L)

        assertEquals(LibraryPolicy.MIX_SIZE, mix.size)
        assertEquals("Mix must not duplicate tracks", mix.size, mix.map { it.id }.toSet().size)
        assertTrue("Mix must draw only from the source", mix.all { it in large })
    }

    @Test
    fun buildYourMixHandlesEmptyInput() {
        assertTrue(LibraryPolicy.buildYourMix(emptyList(), emptyList(), seed = 1L).isEmpty())
    }

    @Test
    fun shuffledPreservesEveryTrack() {
        val shuffled = LibraryPolicy.shuffled(library, seed = 99L)

        assertEquals(library.size, shuffled.size)
        assertEquals(library.map { it.id }.toSet(), shuffled.map { it.id }.toSet())
        assertEquals(shuffled.map { it.id }, LibraryPolicy.shuffled(library, seed = 99L).map { it.id })
    }
}
