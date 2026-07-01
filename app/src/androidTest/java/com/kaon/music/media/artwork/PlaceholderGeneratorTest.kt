package com.kaon.music.media.artwork

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaceholderGeneratorTest {

    @Test
    fun testGeneratePlaceholder() {
        val bitmap = PlaceholderGenerator.generate(
            title = "Bohemian Rhapsody",
            album = "A Night at the Opera",
            artist = "Queen"
        )
        
        assertNotNull(bitmap)
        assertEquals(512, bitmap.width)
        assertEquals(512, bitmap.height)
    }

    @Test
    fun testGeneratePlaceholder_UnknownMetadata() {
        val bitmap = PlaceholderGenerator.generate(
            title = "<unknown>",
            album = "<unknown>",
            artist = "<unknown>"
        )
        
        assertNotNull(bitmap)
        assertEquals(512, bitmap.width)
        assertEquals(512, bitmap.height)
    }
}
