package com.kaon.music.core.data.repository

import com.kaon.music.core.data.model.Track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MetadataRepositoryTest {

    @Test
    fun parseLrc_correctlyParsesLrcTimestampsAndLines() {
        val lrc = """
            [00:12.34] Look into my eyes
            [00:15.500] You will see
            [01:02.1] What you mean to me
            [02:05.00] Search your heart
        """.trimIndent()

        val lines = MetadataRepositoryImpl.parseLrc(lrc)
        assertEquals(4, lines.size)

        assertEquals(12340L, lines[0].timestampMs)
        assertEquals("Look into my eyes", lines[0].text)

        assertEquals(15500L, lines[1].timestampMs)
        assertEquals("You will see", lines[1].text)

        assertEquals(62100L, lines[2].timestampMs)
        assertEquals("What you mean to me", lines[2].text)

        assertEquals(125000L, lines[3].timestampMs)
        assertEquals("Search your heart", lines[3].text)
    }

    @Test
    fun parseLrc_handlesEmptyOrInvalidLinesGracefully() {
        val emptyLines = MetadataRepositoryImpl.parseLrc("")
        assertTrue(emptyLines.isEmpty())

        val nullLines = MetadataRepositoryImpl.parseLrc(null)
        assertTrue(nullLines.isEmpty())

        val invalid = """
            [ar: Artist]
            [al: Album]
            [00:05.00] Valid Line
            Not a timestamped line
        """.trimIndent()

        val lines = MetadataRepositoryImpl.parseLrc(invalid)
        assertEquals(1, lines.size)
        assertEquals(5000L, lines[0].timestampMs)
        assertEquals("Valid Line", lines[0].text)
    }

    @Test
    fun metadataRepository_instantiatesCleanly() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = MetadataRepositoryImpl(
            settingsRepository = null,
            ioDispatcher = testDispatcher
        )

        assertNotNull(repository)
    }

    @Test
    fun getAlbumCoverArtUrl_withBlankInputs_returnsNull() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = MetadataRepositoryImpl(
            settingsRepository = null,
            ioDispatcher = testDispatcher
        )

        val artworkUrl = repository.getAlbumCoverArtUrl("", "")
        assertNull(artworkUrl)
    }

    @Test
    fun getArtistPhotoUrl_withBlankInputs_returnsNull() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repository = MetadataRepositoryImpl(
            settingsRepository = null,
            ioDispatcher = testDispatcher
        )

        val photoUrl = repository.getArtistPhotoUrl("")
        assertNull(photoUrl)
    }
}
