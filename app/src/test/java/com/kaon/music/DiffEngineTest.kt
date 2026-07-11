package com.kaon.music

import com.kaon.music.media.library.sync.DiffEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiffEngineTest {

    data class TestModel(val id: Long, val name: String, val version: Int)

    private val engine = DiffEngine<TestModel, Long>(
        keySelector = { it.id },
        contentEquals = { a, b -> a.name == b.name && a.version == b.version }
    )

    @Test
    fun testDiffAdded() {
        val scanned = listOf(TestModel(1L, "First", 1), TestModel(2L, "Second", 1))
        val existing = listOf(TestModel(1L, "First", 1))

        val result = engine.diff(scanned, existing)

        assertEquals(1, result.added.size)
        assertEquals(2L, result.added.first().id)
        assertEquals(0, result.updated.size)
        assertEquals(0, result.removed.size)
        assertEquals(1, result.unchanged)
    }

    @Test
    fun testDiffUpdated() {
        val scanned = listOf(TestModel(1L, "First - New", 2))
        val existing = listOf(TestModel(1L, "First", 1))

        val result = engine.diff(scanned, existing)

        assertEquals(0, result.added.size)
        assertEquals(1, result.updated.size)
        assertEquals("First - New", result.updated.first().name)
        assertEquals(0, result.removed.size)
        assertEquals(0, result.unchanged)
    }

    @Test
    fun testDiffRemoved() {
        val scanned = listOf(TestModel(1L, "First", 1))
        val existing = listOf(TestModel(1L, "First", 1), TestModel(2L, "Second", 1))

        val result = engine.diff(scanned, existing)

        assertEquals(0, result.added.size)
        assertEquals(0, result.updated.size)
        assertEquals(1, result.removed.size)
        assertEquals(2L, result.removed.first().id)
        assertEquals(1, result.unchanged)
    }

    @Test
    fun testDiffUnchanged() {
        val scanned = listOf(TestModel(1L, "First", 1))
        val existing = listOf(TestModel(1L, "First", 1))

        val result = engine.diff(scanned, existing)

        assertEquals(0, result.added.size)
        assertEquals(0, result.updated.size)
        assertEquals(0, result.removed.size)
        assertEquals(1, result.unchanged)
    }
}
