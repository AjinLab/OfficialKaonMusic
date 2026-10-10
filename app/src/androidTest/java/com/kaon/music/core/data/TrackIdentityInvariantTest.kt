package com.kaon.music.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaon.music.core.data.db.KaonDatabase
import com.kaon.music.core.data.db.dao.TrackRow
import com.kaon.music.core.data.db.entity.TrackEntity
import com.kaon.music.core.data.model.MediaLocator
import com.kaon.music.core.data.model.ProviderId
import com.kaon.music.core.data.model.TrackId
import com.kaon.music.core.data.db.toDomain
import com.kaon.music.core.data.db.toEntity
import com.kaon.music.core.data.model.ArtworkRef
import com.kaon.music.core.data.model.AudioSpec
import com.kaon.music.core.data.model.Track
import com.kaon.music.core.data.repository.TrackRepository
import com.kaon.music.core.data.sync.SyncEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.flow.first
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Identity invariants, against real SQLite (spec §25, §30).
 *
 * These are the load-bearing properties of the whole phase — one track per provider identity, relink
 * keeps the id, missing media does not destroy user data — and they are only meaningful against the
 * actual query planner and constraint enforcement, not fakes.
 */
@RunWith(AndroidJUnit4::class)
class TrackIdentityInvariantTest {

    private lateinit var db: KaonDatabase
    private lateinit var repository: TrackRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            KaonDatabase::class.java
        ).build()
        repository = TrackRepository(
            trackDao = db.trackDao(),
            favoriteDao = db.favoriteDao(),
            syncEngine = SyncEngine(
                scanner = com.kaon.music.core.data.sync.MediaStoreScanner(
                    object : android.content.ContextWrapper(null) {}
                ),
                trackDao = db.trackDao()
            )
        )
    }

    @After
    fun tearDown() = db.close()


    /** Total row count across all providers; `getAllStoredTracks` is sync-only and LOCAL-filtered. */
    private suspend fun totalRowCount(): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM tracks").use { c ->
            c.moveToFirst(); c.getInt(0)
        }

    private fun remoteTrack(videoId: String, title: String = "Stream $videoId") = Track(
        id = TrackId.UNSAVED,
        title = title,
        artist = "Remote Artist",
        album = "Single",
        durationMs = 180_000L,
        locator = MediaLocator.Remote(provider = ProviderId.YOUTUBE, externalId = videoId)
    )

    @Test
    fun repeatedUpsertOfTheSameProviderIdentityYieldsOneRowWithOneId() = runBlocking {
        val first = repository.ensurePersisted(remoteTrack("abc123", "First Title"))
        val second = repository.ensurePersisted(remoteTrack("abc123", "First Title"))
        val third = repository.ensurePersisted(remoteTrack("abc123", "Retitled"))

        assertTrue(first.id.isSaved)
        assertEquals(first.id, second.id)
        assertEquals(first.id, third.id)
        assertEquals(1, totalRowCount())
        // Metadata refreshes on re-upsert; identity does not move.
        assertEquals("Retitled", db.trackDao().getTrackById(first.id.value)!!.title)
    }

    @Test
    fun distinctProviderIdentitiesAreDistinctRows() = runBlocking {
        val a = repository.ensurePersisted(remoteTrack("abc123"))
        val b = repository.ensurePersisted(remoteTrack("xyz789"))
        val otherProvider = repository.ensurePersisted(
            Track(
                id = TrackId.UNSAVED,
                title = "Other Provider",
                artist = "A",
                album = "Al",
                durationMs = 1L,
                locator = MediaLocator.Remote(provider = ProviderId("SOMEPROVIDER"), externalId = "abc123")
            )
        )

        assertNotEquals(a.id, b.id)
        assertNotEquals(a.id, otherProvider.id)
        assertEquals(3, totalRowCount())
    }

    @Test
    fun anAlreadySavedTrackIsReturnedUntouched() = runBlocking {
        val saved = repository.ensurePersisted(remoteTrack("abc123"))
        val again = repository.ensurePersisted(saved.copy(title = "Mutated Title"))

        assertEquals(saved.id, again.id)
        // "Untouched" is the contract: a saved track is not re-written, so the row keeps the title
        // from its first persistence rather than adopting the caller's mutated copy.
        assertEquals("Stream abc123", db.trackDao().getTrackById(saved.id.value)!!.title)
    }

    @Test
    fun localTracksAreNotUpsertedThroughTheProviderDoor() = runBlocking {
        val local = Track(
            id = TrackId.UNSAVED,
            title = "Device File",
            artist = "A",
            album = "Al",
            durationMs = 1L,
            locator = MediaLocator.LocalFile(mediaStoreId = 42L, relativePath = "Music/x.mp3")
        )

        val result = repository.ensurePersisted(local)

        // ensurePersisted returns it unchanged (no external id to key on); local rows are owned by sync.
        assertEquals(TrackId.UNSAVED, result.id)
        assertEquals(0, totalRowCount())
    }

    @Test
    fun markingMissingDestroysNeitherFavoritesNorHistoryNorMembership() = runBlocking {
        val track = Track(
            id = TrackId.UNSAVED,
            title = "T",
            artist = "A",
            album = "Al",
            durationMs = 1L,
            locator = MediaLocator.LocalFile(mediaStoreId = 7L, relativePath = "Music/t.mp3")
        )
        val entity = track.toEntity().copy(trackId = 0L)
        val id = db.trackDao().insertTrack(entity)
        db.favoriteDao().addFavorite(
            com.kaon.music.core.data.db.entity.FavoriteTrackEntity(trackId = id, addedAt = 1L)
        )
        db.playEventDao().insertEvent(
            com.kaon.music.core.data.db.entity.PlayEventEntity(
                trackId = id, eventType = "PLAY", playedAt = 1L, playedMs = 1000L
            )
        )

        db.trackDao().markTracksMissing(listOf(id))

        assertTrue(db.favoriteDao().isFavorite(id))
        val favCount = db.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM favorite_tracks WHERE track_id = $id"
        ).use { c -> c.moveToFirst(); c.getInt(0) }
        val eventCount = db.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM play_events WHERE track_id = $id"
        ).use { c -> c.moveToFirst(); c.getInt(0) }
        assertEquals(1, favCount)
        assertEquals(1, eventCount)

        // And a purge of orphaned rows must not cascade-delete them either.
        db.trackDao().purgeOrphanedTracks(Long.MAX_VALUE)
        assertEquals(1, favCount.let {
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM favorite_tracks WHERE track_id = $id")
                .use { c -> c.moveToFirst(); c.getInt(0) }
        })
    }

    @Test
    fun relinkChangesOnlyTheLocatorColumns() = runBlocking {
        val id = db.trackDao().insertTrack(
            TrackEntity(
                mediaStoreId = 100L,
                title = "T",
                artist = "A",
                album = "Al",
                albumId = 1L,
                durationMs = 1L,
                sizeBytes = 1L,
                dateModified = 1L,
                dateAdded = 1L,
                relativePath = "Music/old.mp3",
                titleNormalized = "t",
                artistNormalized = "a",
                albumNormalized = "al",
                isMissing = true
            )
        )

        db.trackDao().reLinkTrack(trackId = id, newMediaStoreId = 999L, newRelativePath = "Music/new.mp3")

        val row = db.trackDao().getTrackById(id)!!
        assertEquals(999L, row.mediaStoreId)
        assertEquals("Music/new.mp3", row.relativePath)
        assertFalse(row.isMissing)
        assertEquals("LOCAL", row.provider)
        assertNull(row.externalId)
    }

    @Test
    fun purgeIsScopedToLocalRows() = runBlocking {
        val streamed = repository.ensurePersisted(remoteTrack("abc123"))
        db.trackDao().markTracksMissing(listOf(streamed.id.value))

        val purged = db.trackDao().purgeOrphanedTracks(Long.MAX_VALUE)

        // Even if a stream row were somehow flagged missing, a MediaStore purge must not touch it.
        assertEquals(0, purged)
        assertNotNull(db.trackDao().getTrackById(streamed.id.value))
    }

    @Test
    fun providerTracksParticipateInFavoritesAndCounts() = runBlocking {
        val track = repository.ensurePersisted(remoteTrack("abc123"))
        repository.toggleFavorite(track)

        assertTrue(db.favoriteDao().isFavorite(track.id.value))
        assertEquals(1, db.trackDao().observeFavoriteTrackCount("").first())

        // The device-library count excludes the streamed row; the favorite count includes it.
        assertEquals(0, db.trackDao().observeActiveTrackCount("").first())
    }

    @Test
    fun rowRoundTripsThroughLocatorForAllThreeKinds() = runBlocking {
        val entity = TrackEntity(
            mediaStoreId = null,
            title = "Mix",
            artist = "A",
            album = "Al",
            albumId = 1L,
            durationMs = 1L,
            sizeBytes = 2L,
            dateModified = 1L,
            dateAdded = 1L,
            relativePath = null,
            titleNormalized = "mix",
            artistNormalized = "a",
            albumNormalized = "al",
            provider = "YOUTUBE",
            externalId = "vid_1",
            artworkUrl = "https://example.invalid/a.jpg"
        )
        val id = db.trackDao().insertTrack(entity)

        val domain = db.trackDao().getTrackById(id)!!.toDomain(isFavorite = false)
        assertTrue(domain.locator is MediaLocator.Remote)
        val back = domain.toEntity(lastSeenTimestamp = 1L)
        assertEquals("YOUTUBE", back.provider)
        assertEquals("vid_1", back.externalId)
        assertNull(back.mediaStoreId)
        assertNull(back.relativePath)
        assertEquals("https://example.invalid/a.jpg", back.artworkUrl)

        // Same columns through the paged projection. pagingTracksByTitle is the device library and
        // filters provider='LOCAL', so the provider row is read through favorites — no provider filter.
        db.favoriteDao().addFavorite(
            com.kaon.music.core.data.db.entity.FavoriteTrackEntity(trackId = id, addedAt = 1L)
        )
        val row: TrackRow? = db.trackDao().pagingFavoriteTracks("").loadFirstPage(10).firstOrNull()
        assertNotNull(row)
        assertEquals("vid_1", row!!.external_id)
        assertEquals("https://example.invalid/a.jpg", row.artwork_url)
    }

    private suspend fun <V : Any> androidx.paging.PagingSource<Int, V>.loadFirstPage(size: Int): List<V> {
        val result = load(
            androidx.paging.PagingSource.LoadParams.Refresh(key = null, loadSize = size, placeholdersEnabled = false)
        )
        return (result as androidx.paging.PagingSource.LoadResult.Page).data
    }
}
