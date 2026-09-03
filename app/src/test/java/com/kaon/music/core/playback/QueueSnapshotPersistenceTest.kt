package com.kaon.music.core.playback

import com.kaon.music.core.data.db.dao.QueueSnapshotDao
import com.kaon.music.core.data.db.entity.QueueSnapshotEntity
import com.kaon.music.core.data.model.QueueEntry
import com.kaon.music.core.data.model.QueueSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Queue snapshot persistence.
 *
 * The regression these pin: the snapshot used to store bare track ids, and restore looked every id up
 * in Room. Streamed tracks have no Room row (ARCHITECTURE.md §6), so an entire YouTube queue was
 * silently dropped on process death. Entries now carry the provider id, which is also what lets a cold
 * start resolve a streamed track without re-searching for it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QueueSnapshotPersistenceTest {

    private lateinit var dao: FakeDao
    private lateinit var manager: QueueSnapshotManager

    @Before
    fun setUp() {
        dao = FakeDao()
        manager = QueueSnapshotManager(dao, TestScope(StandardTestDispatcher()))
    }

    @Test
    fun `remote entries survive a save and load round trip`() = runTest {
        val snapshot = QueueSnapshot(
            entries = listOf(
                QueueEntry(trackId = 11L),
                QueueEntry(trackId = 22L, youtubeVideoId = "dQw4w9WgXcQ"),
                QueueEntry(trackId = 33L, youtubeVideoId = "aBc-1_2XyZ0")
            ),
            currentIndex = 1,
            currentPositionMs = 45_000L,
            isShuffleEnabled = true,
            repeatMode = 2
        )

        manager.saveSnapshotDirectly(snapshot)
        val restored = manager.loadSnapshot()

        assertNotNull(restored)
        assertEquals(snapshot.entries, restored!!.entries)
        assertEquals("dQw4w9WgXcQ", restored.entries[1].youtubeVideoId)
        assertTrue(restored.entries[1].isRemote)
        assertEquals(1, restored.currentIndex)
        assertEquals(45_000L, restored.currentPositionMs)
        assertTrue(restored.isShuffleEnabled)
        assertEquals(2, restored.repeatMode)
    }

    @Test
    fun `local entries carry no provider identity`() = runTest {
        manager.saveSnapshotDirectly(
            QueueSnapshot(
                entries = listOf(QueueEntry(trackId = 7L)),
                currentIndex = 0,
                currentPositionMs = 0L,
                isShuffleEnabled = false,
                repeatMode = 0
            )
        )

        val restored = manager.loadSnapshot()!!

        assertEquals(listOf(QueueEntry(trackId = 7L)), restored.entries)
        assertNull(restored.entries[0].youtubeVideoId)
        assertEquals(false, restored.entries[0].isRemote)
    }

    @Test
    fun `snapshots written by an older build still load`() = runTest {
        // The previous format was a bare comma-separated id list. Those rows must keep loading rather
        // than being discarded, or upgrading the app silently drops the user's queue.
        dao.storedEntity = QueueSnapshotEntity(
            id = 1,
            serializedTrackIds = "5,6,7",
            currentIndex = 2,
            currentPositionMs = 1_000L,
            isShuffleEnabled = false,
            repeatMode = 0
        )

        val restored = manager.loadSnapshot()!!

        assertEquals(listOf(5L, 6L, 7L), restored.trackIds)
        assertTrue("legacy entries have no provider id", restored.entries.none { it.isRemote })
        assertEquals(2, restored.currentIndex)
    }

    @Test
    fun `malformed tokens are skipped rather than failing the whole restore`() = runTest {
        dao.storedEntity = QueueSnapshotEntity(
            id = 1,
            serializedTrackIds = "10:abc,,notanumber,20:def,:orphan,30",
            currentIndex = 0,
            currentPositionMs = 0L,
            isShuffleEnabled = false,
            repeatMode = 0
        )

        val restored = manager.loadSnapshot()!!

        assertEquals(
            listOf(
                QueueEntry(10L, "abc"),
                QueueEntry(20L, "def"),
                QueueEntry(30L)
            ),
            restored.entries
        )
    }

    @Test
    fun `an out of range stored index is clamped`() = runTest {
        dao.storedEntity = QueueSnapshotEntity(
            id = 1,
            serializedTrackIds = "1,2",
            currentIndex = 99,
            currentPositionMs = 0L,
            isShuffleEnabled = false,
            repeatMode = 0
        )

        assertEquals(1, manager.loadSnapshot()!!.currentIndex)
    }

    @Test
    fun `an empty serialized queue loads as no snapshot`() = runTest {
        dao.storedEntity = QueueSnapshotEntity(
            id = 1,
            serializedTrackIds = "",
            currentIndex = 0,
            currentPositionMs = 0L,
            isShuffleEnabled = false,
            repeatMode = 0
        )

        assertNull(manager.loadSnapshot())
    }

    private class FakeDao : QueueSnapshotDao {
        var storedEntity: QueueSnapshotEntity? = null

        override suspend fun getQueueSnapshot(): QueueSnapshotEntity? = storedEntity

        override suspend fun saveQueueSnapshot(snapshot: QueueSnapshotEntity) {
            storedEntity = snapshot
        }

        override suspend fun clearQueueSnapshot() {
            storedEntity = null
        }
    }
}
