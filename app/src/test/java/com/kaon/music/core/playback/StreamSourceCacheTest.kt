package com.kaon.music.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSourceCacheTest {

    private var now = 1_000_000L
    private fun cache(maxEntries: Int = 10) = StreamSourceCache<String>(maxEntries = maxEntries, currentTimeMillis = { now })

    @Test
    fun `put then get returns value`() {
        val c = cache()
        c.put("k", "v", expiresAtMillis = now + 1_000)
        assertEquals("v", c.get("k"))
    }

    @Test
    fun `expired entry is purged and generation advances`() {
        val c = cache()
        c.put("k", "v", expiresAtMillis = now + 1_000)
        now += 2_000
        assertNull(c.get("k"))
        assertEquals(1L, c.generation("k"))
    }

    @Test
    fun `stale put is rejected after invalidation bumps generation`() {
        val c = cache()
        val generation = c.generation("k")
        c.put("k", "v1", expiresAtMillis = now + 1_000, expectedGeneration = generation)
        c.invalidatePrefix("k")

        // A resolution that started before the invalidation must not repopulate the cache.
        assertFalse(c.put("k", "stale", expiresAtMillis = now + 1_000, expectedGeneration = generation))
        assertNull(c.get("k"))

        // The fresh generation accepts writes.
        assertTrue(c.put("k", "fresh", expiresAtMillis = now + 1_000, expectedGeneration = c.generation("k")))
        assertEquals("fresh", c.get("k"))
    }

    @Test
    fun `invalidatePrefix only affects matching keys`() {
        val c = cache()
        c.put("abc|HIGH", "v1", expiresAtMillis = now + 1_000)
        c.put("abcd|HIGH", "v2", expiresAtMillis = now + 1_000)

        c.invalidatePrefix("abc|")

        assertNull(c.get("abc|HIGH"))
        assertEquals("v2", c.get("abcd|HIGH"))
    }

    @Test
    fun `lru bound evicts least recently used`() {
        val c = cache(maxEntries = 2)
        c.put("a", "va", expiresAtMillis = now + 1_000)
        c.put("b", "vb", expiresAtMillis = now + 1_000)
        c.get("a") // touch a so b becomes the LRU entry
        c.put("c", "vc", expiresAtMillis = now + 1_000)

        assertNull(c.get("b"))
        assertEquals("va", c.get("a"))
        assertEquals("vc", c.get("c"))
    }

    @Test
    fun `clear removes everything`() {
        val c = cache()
        c.put("a", "va", expiresAtMillis = now + 1_000)
        c.clear()
        assertNull(c.get("a"))
    }
}
