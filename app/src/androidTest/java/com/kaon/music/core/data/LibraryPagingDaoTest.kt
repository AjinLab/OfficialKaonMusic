package com.kaon.music.core.data

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaon.music.core.data.db.KaonDatabase
import com.kaon.music.core.data.db.dao.TrackRow
import com.kaon.music.core.data.db.entity.FavoriteTrackEntity
import com.kaon.music.core.data.db.entity.PlayEventEntity
import com.kaon.music.core.data.db.entity.PlaylistEntity
import com.kaon.music.core.data.db.entity.PlaylistTrackEntity
import com.kaon.music.core.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 3 bounded-access tests, against real SQLite.
 *
 * These have to be instrumented rather than JVM tests: the properties under test are SQL semantics
 * (`ORDER BY` with a `LEFT JOIN`, `EXISTS` favorite resolution, `LIMIT` behaviour, index-backed
 * paging), and Robolectric-free JVM tests run against `unitTests.isReturnDefaultValues`, where there
 * is no query planner to be right or wrong.
 */
@RunWith(AndroidJUnit4::class)
class LibraryPagingDaoTest {

    private lateinit var db: KaonDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            KaonDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() = db.close()

    private fun track(
        id: Long,
        title: String,
        artist: String = "Artist",
        album: String = "Album",
        dateAdded: Long = id,
        isMissing: Boolean = false
    ) = TrackEntity(
        trackId = id,
        mediaStoreId = 1000L + id,
        title = title,
        artist = artist,
        artistId = 1L,
        album = album,
        albumId = 1L,
        durationMs = 200_000L,
        sizeBytes = 4_000_000L,
        dateModified = 1L,
        dateAdded = dateAdded,
        relativePath = "Music/$title.mp3",
        titleNormalized = title.lowercase(),
        artistNormalized = artist.lowercase(),
        albumNormalized = album.lowercase(),
        isMissing = isMissing
    )

    private suspend fun <V : Any> PagingSource<Int, V>.loadFirstPage(size: Int): List<V> {
        val result = load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = size, placeholdersEnabled = false)
        )
        return (result as PagingSource.LoadResult.Page).data
    }

    @Test
    fun pagedTracksReturnsOnlyTheRequestedPage() = runBlocking {
        db.trackDao().insertTracks((1..500L).map { track(it, "Track %03d".format(it)) })

        val firstPage = db.trackDao().pagingTracksByTitle("").loadFirstPage(50)

        // The load returns one page, not the table: this is the property the whole phase rests on.
        assertEquals(50, firstPage.size)
        assertEquals("Track 001", firstPage.first().title)
        assertEquals("Track 050", firstPage.last().title)
    }

    @Test
    fun pagedTracksExcludeMissingRows() = runBlocking {
        db.trackDao().insertTracks(
            listOf(
                track(1L, "Present"),
                track(2L, "Orphaned", isMissing = true)
            )
        )

        val page = db.trackDao().pagingTracksByTitle("").loadFirstPage(50)

        assertEquals(listOf("Present"), page.map { it.title })
    }

    @Test
    fun favoriteStateIsResolvedInSql() = runBlocking {
        db.trackDao().insertTracks(listOf(track(1L, "Loved"), track(2L, "Plain")))
        db.favoriteDao().addFavorite(FavoriteTrackEntity(trackId = 1L))

        val page = db.trackDao().pagingTracksByTitle("").loadFirstPage(50).associateBy { it.title }

        // Favorite state used to be joined in Kotlin from the complete favorite id list.
        assertTrue(page.getValue("Loved").is_favorite)
        assertFalse(page.getValue("Plain").is_favorite)
    }

    @Test
    fun sortOrdersAreAppliedBySql() = runBlocking {
        db.trackDao().insertTracks(
            listOf(
                track(1L, "Charlie", dateAdded = 300L),
                track(2L, "Alpha", dateAdded = 100L),
                track(3L, "Bravo", dateAdded = 200L)
            )
        )
        // Two plays for Bravo, one for Alpha, none for Charlie.
        db.playEventDao().insertEvent(PlayEventEntity(trackId = 3L, eventType = "PLAY", playedAt = 1L))
        db.playEventDao().insertEvent(PlayEventEntity(trackId = 3L, eventType = "PLAY", playedAt = 2L))
        db.playEventDao().insertEvent(PlayEventEntity(trackId = 2L, eventType = "PLAY", playedAt = 3L))

        val byTitle = db.trackDao().pagingTracksByTitle("").loadFirstPage(50).map { it.title }
        val byDate = db.trackDao().pagingTracksByDateAdded("").loadFirstPage(50).map { it.title }
        val byPlays = db.trackDao().pagingTracksByPlayCount("").loadFirstPage(50).map { it.title }

        assertEquals(listOf("Alpha", "Bravo", "Charlie"), byTitle)
        assertEquals(listOf("Charlie", "Bravo", "Alpha"), byDate)
        // Never-played tracks must survive the play-count sort (LEFT JOIN), ordered last.
        assertEquals(listOf("Bravo", "Alpha", "Charlie"), byPlays)
    }

    @Test
    fun searchFiltersInSqlAcrossTitleArtistAndAlbum() = runBlocking {
        db.trackDao().insertTracks(
            listOf(
                track(1L, "Midnight Echoes", artist = "Nova", album = "Dawn"),
                track(2L, "Sunrise", artist = "Midnight Choir", album = "Dawn"),
                track(3L, "Unrelated", artist = "Nova", album = "Midnight Album"),
                track(4L, "Nothing", artist = "Nova", album = "Dawn")
            )
        )

        val matches = db.trackDao().pagingTracksByTitle("midnight").loadFirstPage(50).map { it.track_id }

        assertEquals(listOf(1L, 2L, 3L), matches.sorted())
    }

    @Test
    fun countsMatchThePagedResultSet() = runBlocking {
        db.trackDao().insertTracks((1..120L).map { track(it, "Song %03d".format(it)) })
        db.favoriteDao().addFavorite(FavoriteTrackEntity(trackId = 5L))
        db.favoriteDao().addFavorite(FavoriteTrackEntity(trackId = 6L))

        assertEquals(120, db.trackDao().observeActiveTrackCount("").first())
        assertEquals(2, db.trackDao().observeFavoriteTrackCount("").first())
        // A filtered count must describe the filtered page set, not the table.
        assertEquals(1, db.trackDao().observeActiveTrackCount("song 007").first())
    }

    @Test
    fun queueIdsAreBoundedAndOrderedLikeTheList() = runBlocking {
        db.trackDao().insertTracks((1..500L).map { track(it, "Track %03d".format(it)) })

        val ids = db.trackDao().getActiveTrackIdsByTitle("", limit = 100)

        assertEquals(100, ids.size)
        assertEquals((1L..100L).toList(), ids)
    }

    @Test
    fun favoritePagingOrdersByWhenFavorited() = runBlocking {
        db.trackDao().insertTracks(listOf(track(1L, "First"), track(2L, "Second")))
        db.favoriteDao().addFavorite(FavoriteTrackEntity(trackId = 1L, addedAt = 100L))
        db.favoriteDao().addFavorite(FavoriteTrackEntity(trackId = 2L, addedAt = 200L))

        val page = db.trackDao().pagingFavoriteTracks("").loadFirstPage(50)

        assertEquals(listOf("Second", "First"), page.map { it.title })
        assertTrue(page.all { it.is_favorite })
    }

    @Test
    fun playlistPagingKeepsPositionOrderAndHidesOrphans() = runBlocking {
        db.trackDao().insertTracks(
            listOf(track(1L, "A"), track(2L, "B"), track(3L, "Gone", isMissing = true))
        )
        val playlistId = db.playlistDao().insertPlaylist(
            PlaylistEntity(name = "Mix", createdAt = 1L, updatedAt = 1L)
        )
        db.playlistDao().addTracksToPlaylist(
            listOf(
                PlaylistTrackEntity(playlistId = playlistId, trackId = 2L, position = 0),
                PlaylistTrackEntity(playlistId = playlistId, trackId = 3L, position = 1),
                PlaylistTrackEntity(playlistId = playlistId, trackId = 1L, position = 2)
            )
        )

        val page: List<TrackRow> = db.playlistDao().pagingTracksForPlaylist(playlistId).loadFirstPage(50)
        val stats = db.playlistDao().observePlaylistStats(playlistId).first()

        assertEquals(listOf("B", "A"), page.map { it.title })
        // Membership survives an orphaned track; the header count reflects only visible members.
        assertEquals(2, stats.track_count)
        assertEquals(400_000L, stats.total_duration_ms)
        assertEquals(listOf(2L, 1L), db.playlistDao().getOrderedTrackIds(playlistId))
    }

    @Test
    fun boundedSearchRespectsItsLimit() = runBlocking {
        db.trackDao().insertTracks((1..200L).map { track(it, "Echo %03d".format(it)) })

        val results = db.trackDao().searchTracks("echo", limit = 25).first()

        assertEquals(25, results.size)
    }

    @Test
    fun pagedQueriesUseTheCompositeIndexes() = runBlocking {
        db.trackDao().insertTracks((1..200L).map { track(it, "Track %03d".format(it)) })

        // A plan that says SCAN rather than SEARCH/USING INDEX means the v7 composite indexes are not
        // serving the ordering, which is the difference between paging a page and sorting 50k rows.
        val plan = db.openHelper.readableDatabase.query(
            "EXPLAIN QUERY PLAN SELECT track_id FROM tracks WHERE is_missing = 0 " +
                "ORDER BY title_normalized ASC, track_id ASC LIMIT 50"
        ).use { cursor ->
            buildString {
                while (cursor.moveToNext()) append(cursor.getString(cursor.columnCount - 1)).append('\n')
            }
        }

        assertTrue(
            "expected an index-backed plan, got: $plan",
            plan.contains("index_tracks_is_missing_title_normalized")
        )
    }
}
