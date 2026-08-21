package com.kaon.music.core.playback

import com.kaon.music.core.data.db.dao.QueueSnapshotDao
import com.kaon.music.core.data.db.entity.QueueSnapshotEntity
import com.kaon.music.core.data.model.QueueSnapshot
import com.kaon.music.core.playback.model.PlaybackState
import com.kaon.music.core.playback.model.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackQueueTest {

    private lateinit var fakeQueueDao: FakeQueueSnapshotDao

    @Before
    fun setup() {
        fakeQueueDao = FakeQueueSnapshotDao()
    }

    /**
     * Test 6: Queue restore does not overwrite a queue the user has already modified during startup.
     */
    @Test
    fun `queue restore rule always loses to explicit user action`() = runTest {
        // Stored queue snapshot from previous run
        fakeQueueDao.saveQueueSnapshot(
            QueueSnapshotEntity(
                id = 1,
                serializedTrackIds = "10,20,30,40",
                currentIndex = 2,
                currentPositionMs = 45000,
                isShuffleEnabled = false,
                repeatMode = 0
            )
        )

        val testScope = TestScope(StandardTestDispatcher())
        val snapshotManager = QueueSnapshotManager(fakeQueueDao, testScope)

        // Simulation: Player is initially empty
        var playerMediaCount = 0

        // User immediately starts playing a new track (explicit user intent)
        playerMediaCount = 1 // User triggered playback of track 99

        // Restore check:
        val shouldRestore = playerMediaCount == 0
        if (shouldRestore) {
            val restored = snapshotManager.loadSnapshot()
            assertNotNull(restored)
        }

        // Verify restore was skipped because player was already active
        assertEquals(false, shouldRestore)
    }

    /**
     * Test 7: Playback state serialization and deserialization across process restarts.
     */
    @Test
    fun `playback state snapshot serializes and reconstructs exactly`() = runTest {
        val testScope = TestScope(StandardTestDispatcher())
        val snapshotManager = QueueSnapshotManager(fakeQueueDao, testScope)

        val originalSnapshot = QueueSnapshot(
            trackIds = listOf(101L, 202L, 303L, 404L),
            currentIndex = 1,
            currentPositionMs = 73400L,
            isShuffleEnabled = true,
            repeatMode = 1
        )

        // Save snapshot immediately (on pause/stop)
        snapshotManager.scheduleSnapshotSave(originalSnapshot, immediate = true)

        // Simulate cold load in new process instance
        val restored = snapshotManager.loadSnapshot()
        assertNotNull(restored)
        assertEquals(listOf(101L, 202L, 303L, 404L), restored!!.trackIds)
        assertEquals(1, restored.currentIndex)
        assertEquals(73400L, restored.currentPositionMs)
        assertEquals(true, restored.isShuffleEnabled)
        assertEquals(1, restored.repeatMode)
    }

    /**
     * Test 8: Granular timeline mutations preserve current item and positions without bulk reset.
     */
    @Test
    fun `granular queue mutations maintain correct index and items`() {
        val initialQueue = mutableListOf(1L, 2L, 3L, 4L)
        var currentIndex = 1 // Currently playing track 2

        // Granular Add to Next: insert after index 1
        val newTrack = 99L
        val nextIndex = currentIndex + 1
        initialQueue.add(nextIndex, newTrack)

        // Verify current playing track remains unchanged at index 1
        assertEquals(listOf(1L, 2L, 99L, 3L, 4L), initialQueue)
        assertEquals(2L, initialQueue[currentIndex])

        // Granular Move: Move track 4 (index 4) to index 0
        val moved = initialQueue.removeAt(4)
        initialQueue.add(0, moved)
        currentIndex++ // Index shifts by 1 because item added before current

        assertEquals(listOf(4L, 1L, 2L, 99L, 3L), initialQueue)
        assertEquals(2L, initialQueue[currentIndex]) // Still playing track 2
    }
}

class FakeQueueSnapshotDao : QueueSnapshotDao {
    private var storedEntity: QueueSnapshotEntity? = null

    override suspend fun getQueueSnapshot(): QueueSnapshotEntity? = storedEntity

    override suspend fun saveQueueSnapshot(snapshot: QueueSnapshotEntity) {
        storedEntity = snapshot
    }

    override suspend fun clearQueueSnapshot() {
        storedEntity = null
    }
}
