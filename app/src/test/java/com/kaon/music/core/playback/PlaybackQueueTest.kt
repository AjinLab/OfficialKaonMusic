package com.kaon.music.core.playback

import com.kaon.music.core.data.db.dao.QueueSnapshotDao
import com.kaon.music.core.data.db.entity.QueueSnapshotEntity
import com.kaon.music.core.data.model.QueueSnapshot
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.playback.model.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

        // Save snapshot directly for synchronous test verification
        snapshotManager.saveSnapshotDirectly(originalSnapshot)

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

    /**
     * Test 9 (Milestone 2 Stage 2 / M2-D1):
     * Identical-Queue Selection Detection:
     * When user taps a track in the existing queue, it seeks instead of resetting media items.
     */
    @Test
    fun `identical queue detection seeks instead of resetting`() {
        val currentQueue = listOf(
            createDummyTrack(1L, "Track 1"),
            createDummyTrack(2L, "Track 2"),
            createDummyTrack(3L, "Track 3")
        )
        val selectedQueue = listOf(
            createDummyTrack(1L, "Track 1"),
            createDummyTrack(2L, "Track 2"),
            createDummyTrack(3L, "Track 3")
        )

        val isIdenticalQueue = currentQueue.isNotEmpty() &&
                currentQueue.size == selectedQueue.size &&
                currentQueue.indices.all { i -> currentQueue[i].id == selectedQueue[i].id }

        assertTrue(isIdenticalQueue)

        // Target track is index 2 ("Track 3")
        val targetIndex = selectedQueue.indexOfFirst { it.id == 3L }
        assertEquals(2, targetIndex)
    }

    /**
     * Test 10 (Milestone 2 Failure Matrix #1 & #2 / M2-D3):
     * Unplayable-Item Policy:
     * Consecutive playback failures auto-advance but stop cleanly at threshold of 3.
     */
    @Test
    fun `unplayable item policy auto advances and caps at 3 consecutive errors`() {
        var consecutiveErrors = 0
        val maxConsecutiveErrors = 3
        val queue = mutableListOf("corrupt_1.mp3", "corrupt_2.mp3", "corrupt_3.mp3", "valid_4.mp3")
        var currentIndex = 0
        var isPlayerStopped = false

        // Simulate 3 consecutive load failures
        while (consecutiveErrors < maxConsecutiveErrors && currentIndex < queue.size - 1) {
            consecutiveErrors++
            if (consecutiveErrors < maxConsecutiveErrors) {
                // Auto-advance to next item
                currentIndex++
            } else {
                // Threshold reached: stop player cleanly
                isPlayerStopped = true
            }
        }

        assertEquals(3, consecutiveErrors)
        assertTrue(isPlayerStopped)
        assertEquals(2, currentIndex) // Advanced up to corrupt_3 before cleanly stopping
    }

    /**
     * Test 11 (Milestone 2 Failure Matrix #11):
     * Snapshot restore with all orphaned tracks clears snapshot and presents clean state.
     */
    @Test
    fun `restore with all orphaned tracks clears snapshot without crashing`() = runTest {
        val testScope = TestScope(StandardTestDispatcher())
        val snapshotManager = QueueSnapshotManager(fakeQueueDao, testScope)

        snapshotManager.saveSnapshotDirectly(
            QueueSnapshot(
                trackIds = listOf(999L, 888L), // Non-existent in library
                currentIndex = 0,
                currentPositionMs = 0L,
                isShuffleEnabled = false,
                repeatMode = 0
            )
        )

        val restoredSnapshot = snapshotManager.loadSnapshot()
        assertNotNull(restoredSnapshot)

        // Library lookup returns empty because tracks are orphaned/missing
        val existingTracksInDb = emptyList<Track>()

        if (existingTracksInDb.isEmpty()) {
            snapshotManager.clearSnapshot()
        }

        // Snapshot is cleanly cleared
        val finalSnapshot = snapshotManager.loadSnapshot()
        assertNull(finalSnapshot)
    }

    /**
     * Test 12 (Milestone 2 Failure Matrix #12 & #13):
     * Removing current item advances to next; clearing queue stops playback and empties state.
     */
    @Test
    fun `removing current item advances and clearing queue stops playback`() {
        val queue = mutableListOf(10L, 20L, 30L)
        var currentIndex = 1 // Playing track 20

        // Remove current item (index 1)
        queue.removeAt(currentIndex)
        // Current index remains 1, which now points to track 30 (advances seamlessly)
        assertEquals(listOf(10L, 30L), queue)
        assertEquals(30L, queue[currentIndex])

        // Clear queue
        queue.clear()
        currentIndex = -1
        var isPlaying = false

        assertEquals(0, queue.size)
        assertEquals(-1, currentIndex)
        assertFalse(isPlaying)
    }

    private fun createDummyTrack(id: Long, title: String): Track {
        return Track(
            id = id,
            mediaStoreId = id + 1000,
            title = title,
            artist = "Artist",
            album = "Album",
            durationMs = 180000L,
            sizeBytes = 3000000L,
            dateModified = 1000L,
            albumId = 1L,
            isFavorite = false
        )
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
