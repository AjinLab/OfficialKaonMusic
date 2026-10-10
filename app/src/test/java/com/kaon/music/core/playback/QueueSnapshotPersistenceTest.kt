package com.kaon.music.core.playback

import com.kaon.music.core.data.db.dao.QueueSnapshotDao
import com.kaon.music.core.data.db.entity.QueueSnapshotEntity
import com.kaon.music.core.data.model.QueueEntry
import com.kaon.music.core.data.model.QueueSnapshot
import com.kaon.music.core.data.model.TrackId
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
 * Queue snapshot persistence under canonical identity.
 *
 * The regressions these pin, in order of when they happened:
 * 1. The snapshot stored bare ids and restore looked them up in Room. Streamed tracks had no row, so
 *    an entire YouTube queue was silently dropped on process death (fixed in Phase 2 by persisting
 *    provider identity in the serialized string).
 * 2. Phase 4 makes the *row* own the provider identity — a streamed track is persisted before it can
 *    be queued — so the snapshot is pure [TrackId]s again, and the legacy `hashId:videoId` tokens
 *    written by intermediate builds resolve through [QueueSnapshotManager.legacyIdentityResolver].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QueueSnapshotPersistenceTest {

    private lateinit var dao: FakeDao
    private lateinit var manager: QueueSnapshotManager
    private var resolvedLegacy: MutableList<Pair<String, String>> = mutableListOf()

    @Before
    fun setUp() {
        dao = FakeDao()
        resolvedLegacy = mutableListOf()
        manager = QueueSnapshotManager(
            queueSnapshotDao = dao,
            serviceScope = TestScope(StandardTestDispatcher()),
            // Stands in for TrackRepository.getTrackByExternalIdentity in the service wiring.
            legacyIdentityResolver = { provider, externalId ->
                resolvedLegacy += provider.value to externalId
                TrackId(9000L + resolvedLegacy.size)
            }
        )
    }

    private fun snapshotOf(ids: List<Long>, currentIndex: Int = 0) = QueueSnapshot(
        entries = ids.map { QueueEntry(trackId = TrackId(it)) },
        currentIndex = currentIndex,
        currentPositionMs = 45_000L,
        isShuffleEnabled = true,
        repeatMode = 2
    )

    @Test
    fun `a mixed queue of canonical ids survives a save and load round trip`() = runTest {
        val snapshot = snapshotOf(listOf(11L, 22L, 33L), currentIndex = 1)

        manager.saveSnapshotDirectly(snapshot)
        val restored = manager.loadSnapshot()

        assertNotNull(restored)
        // Identity only: provider/locator facts live in the referenced rows, so the round trip is
        // exactly the ids (spec §14 — one queue model for local + provider media).
        assertEquals(snapshot.entries, restored!!.entries)
        assertEquals(1, restored.currentIndex)
        assertEquals(45_000L, restored.currentPositionMs)
        assertTrue(restored.isShuffleEnabled)
        assertEquals(2, restored.repeatMode)
    }

    @Test
    fun `legacy hash-id video-id tokens resolve through the identity resolver`() = runTest {
        // Written by an intermediate build: `hashId:videoId`. The hash was never a row, so the
        // resolver looks the video up by external identity instead of trusting it.
        dao.storedEntity = QueueSnapshotEntity(
            id = 1,
            serializedTrackIds = "5,1234567:dQw4w9WgXcQ,7",
            currentIndex = 1,
            currentPositionMs = 0L,
            isShuffleEnabled = false,
            repeatMode = 0
        )

        val restored = manager.loadSnapshot()!!

        assertEquals(listOf(5L, 9001L, 7L), restored.trackIds.map { it.value })
        assertEquals(listOf("YOUTUBE" to "dQw4w9WgXcQ"), resolvedLegacy)
        assertEquals(1, restored.currentIndex)
    }

    @Test
    fun `snapshots written by the oldest build are bare id lists and still load`() = runTest {
        dao.storedEntity = QueueSnapshotEntity(
            id = 1,
            serializedTrackIds = "5,6,7",
            currentIndex = 2,
            currentPositionMs = 1_000L,
            isShuffleEnabled = false,
            repeatMode = 0
        )

        val restored = manager.loadSnapshot()!!

        assertEquals(listOf(5L, 6L, 7L), restored.trackIds.map { it.value })
        assertTrue(resolvedLegacy.isEmpty())
        assertEquals(2, restored.currentIndex)
    }

    @Test
    fun `unresolvable tokens are dropped and the saved index shifts with them`() = runTest {
        // `notanumber` is unparseable and `,,` is empty: both are unrecoverable positions. (The
        // `hashId:videoId` form with an empty hash half is still a *legal* legacy token — it resolves
        // by video id — so it is not a drop case.)
        dao.storedEntity = QueueSnapshotEntity(
            id = 1,
            serializedTrackIds = "10:abc,,notanumber,30",
            currentIndex = 2,
            currentPositionMs = 0L,
            isShuffleEnabled = false,
            repeatMode = 0
        )

        val restored = manager.loadSnapshot()!!

        // `abc` resolved; the two unrecoverable tokens were skipped and only one of them sat before
        // the saved index, so "30" moves from saved position 2 to restored position 1.
        assertEquals(listOf(9001L, 30L), restored.trackIds.map { it.value })
        assertEquals(1, restored.currentIndex)
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
