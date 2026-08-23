package com.kaon.music.core.data.playlist

import com.kaon.music.core.data.db.dao.FavoriteDao
import com.kaon.music.core.data.db.dao.PlaylistDao
import com.kaon.music.core.data.db.dao.TrackDao
import com.kaon.music.core.data.db.entity.FavoriteTrackEntity
import com.kaon.music.core.data.db.entity.PlaylistEntity
import com.kaon.music.core.data.db.entity.PlaylistTrackEntity
import com.kaon.music.core.data.db.entity.TrackEntity
import com.kaon.music.core.data.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaylistRepositoryTest {

    private lateinit var fakePlaylistDao: FakePlaylistDao
    private lateinit var fakeTrackDao: FakeTrackDao
    private lateinit var fakeFavoriteDao: FakeFavoriteDao
    private lateinit var repository: PlaylistRepository

    @Before
    fun setup() {
        fakePlaylistDao = FakePlaylistDao()
        fakeTrackDao = FakeTrackDao()
        fakeFavoriteDao = FakeFavoriteDao()
        fakePlaylistDao.trackDaoRef = fakeTrackDao

        repository = PlaylistRepository(
            playlistDao = fakePlaylistDao,
            trackDao = fakeTrackDao,
            favoriteDao = fakeFavoriteDao
        )
    }

    private fun createTrack(id: Long, title: String, isMissing: Boolean = false): TrackEntity {
        return TrackEntity(
            trackId = id,
            mediaStoreId = 1000L + id,
            title = title,
            artist = "Test Artist",
            album = "Test Album",
            albumId = 1L,
            durationMs = 180000L,
            sizeBytes = 4000000L,
            dateModified = 1000L,
            dateAdded = 1000L,
            relativePath = "Music/$title.mp3",
            titleNormalized = title.lowercase(),
            artistNormalized = "test artist",
            albumNormalized = "test album",
            isMissing = isMissing,
            lastSeenTimestamp = System.currentTimeMillis()
        )
    }

    // ==================== Playlist CRUD Tests ====================

    @Test
    fun `create, get, rename, and delete playlist CRUD operations`() = runTest {
        val playlistId = repository.createPlaylist("Favorites Mix")
        assertTrue(playlistId > 0)

        val playlist = repository.getPlaylist(playlistId)
        assertNotNull(playlist)
        assertEquals("Favorites Mix", playlist!!.name)
        assertEquals(0, playlist.trackCount)

        repository.renamePlaylist(playlistId, "Roadtrip 2026")
        val renamed = repository.getPlaylist(playlistId)
        assertNotNull(renamed)
        assertEquals("Roadtrip 2026", renamed!!.name)

        repository.deletePlaylist(playlistId)
        val deleted = repository.getPlaylist(playlistId)
        assertNull(deleted)
    }

    // ==================== Track Membership & Duplicate Prevention Tests ====================

    @Test
    fun `add tracks to playlist and maintain position ordering`() = runTest {
        val pId = repository.createPlaylist("Chill")
        val t1 = createTrack(1L, "Song A")
        val t2 = createTrack(2L, "Song B")
        val t3 = createTrack(3L, "Song C")
        fakeTrackDao.storedTracks.addAll(listOf(t1, t2, t3))

        repository.addTrackToPlaylist(pId, 1L)
        repository.addTrackToPlaylist(pId, 2L)
        repository.addTrackToPlaylist(pId, 3L)

        val tracks = repository.getTracksForPlaylist(pId)
        assertEquals(3, tracks.size)
        assertEquals(1L, tracks[0].id)
        assertEquals(2L, tracks[1].id)
        assertEquals(3L, tracks[2].id)
    }

    @Test
    fun `duplicate track addition is rejected by composite primary key`() = runTest {
        val pId = repository.createPlaylist("Focus")
        val t1 = createTrack(1L, "Song A")
        fakeTrackDao.storedTracks.add(t1)

        val firstAdd = repository.addTrackToPlaylist(pId, 1L)
        assertTrue(firstAdd)

        val secondAdd = repository.addTrackToPlaylist(pId, 1L)
        assertFalse("Duplicate track should not be added", secondAdd)

        val tracks = repository.getTracksForPlaylist(pId)
        assertEquals(1, tracks.size)
    }

    // ==================== M5-D2 Orphan Hiding & Re-link Resurfacing ====================

    @Test
    fun `orphaned track is hidden from playlist queries but retained in playlist_tracks`() = runTest {
        val pId = repository.createPlaylist("Gym")
        val t1 = createTrack(1L, "Track 1", isMissing = false)
        val t2 = createTrack(2L, "Track 2", isMissing = false)
        fakeTrackDao.storedTracks.addAll(listOf(t1, t2))

        repository.addTrackToPlaylist(pId, 1L)
        repository.addTrackToPlaylist(pId, 2L)

        // Initially 2 tracks visible
        assertEquals(2, repository.getTracksForPlaylist(pId).size)

        // File for Track 2 goes missing (SD card unplugged / media store churn)
        fakeTrackDao.markTracksMissing(listOf(2L))
        assertTrue(fakeTrackDao.getTrackById(2L)!!.isMissing)

        // Query-level orphan filtering (WHERE t.is_missing = 0) hides Track 2
        val visibleTracks = repository.getTracksForPlaylist(pId)
        assertEquals(1, visibleTracks.size)
        assertEquals(1L, visibleTracks.first().id)

        // But raw playlist_tracks membership remains intact
        val rawEntries = fakePlaylistDao.getPlaylistTrackEntries(pId)
        assertEquals(2, rawEntries.size)
        assertTrue(rawEntries.any { it.trackId == 2L })
    }

    @Test
    fun `re-linked track automatically resurfaces in playlist at original position`() = runTest {
        val pId = repository.createPlaylist("Summer")
        val t1 = createTrack(1L, "Track 1")
        val t2 = createTrack(2L, "Track 2")
        val t3 = createTrack(3L, "Track 3")
        fakeTrackDao.storedTracks.addAll(listOf(t1, t2, t3))

        repository.addTrackToPlaylist(pId, 1L)
        repository.addTrackToPlaylist(pId, 2L)
        repository.addTrackToPlaylist(pId, 3L)

        // Track 2 goes missing
        fakeTrackDao.markTracksMissing(listOf(2L))
        assertEquals(2, repository.getTracksForPlaylist(pId).size)

        // Track 2 file returns and is re-linked
        fakeTrackDao.reLinkTrack(2L, 9999L)
        assertFalse(fakeTrackDao.getTrackById(2L)!!.isMissing)

        // Track 2 resurfaces automatically at original index position 1
        val resurfaced = repository.getTracksForPlaylist(pId)
        assertEquals(3, resurfaced.size)
        assertEquals(1L, resurfaced[0].id)
        assertEquals(2L, resurfaced[1].id)
        assertEquals(3L, resurfaced[2].id)
    }

    // ==================== M5-D5 Reordering Tests ====================

    @Test
    fun `drag to reorder updates track positions atomically`() = runTest {
        val pId = repository.createPlaylist("Workout")
        val t1 = createTrack(1L, "Track A")
        val t2 = createTrack(2L, "Track B")
        val t3 = createTrack(3L, "Track C")
        fakeTrackDao.storedTracks.addAll(listOf(t1, t2, t3))

        repository.addTrackToPlaylist(pId, 1L)
        repository.addTrackToPlaylist(pId, 2L)
        repository.addTrackToPlaylist(pId, 3L)

        // Initial order: [1, 2, 3]
        val initial = repository.getTracksForPlaylist(pId)
        assertEquals(listOf(1L, 2L, 3L), initial.map { it.id })

        // Reorder to: [3, 1, 2]
        repository.reorderTracks(pId, listOf(3L, 1L, 2L))

        val reordered = repository.getTracksForPlaylist(pId)
        assertEquals(3, reordered.size)
        assertEquals(3L, reordered[0].id)
        assertEquals(1L, reordered[1].id)
        assertEquals(2L, reordered[2].id)
    }

    @Test
    fun `remove track from playlist re-indexes remaining tracks without gaps`() = runTest {
        val pId = repository.createPlaylist("Mix")
        val t1 = createTrack(1L, "Track 1")
        val t2 = createTrack(2L, "Track 2")
        val t3 = createTrack(3L, "Track 3")
        fakeTrackDao.storedTracks.addAll(listOf(t1, t2, t3))

        repository.addTrackToPlaylist(pId, 1L)
        repository.addTrackToPlaylist(pId, 2L)
        repository.addTrackToPlaylist(pId, 3L)

        // Remove middle track (Track 2)
        repository.removeTrackFromPlaylist(pId, 2L)

        val remaining = repository.getTracksForPlaylist(pId)
        assertEquals(2, remaining.size)
        assertEquals(1L, remaining[0].id)
        assertEquals(3L, remaining[1].id)

        // Verify entries have positions 0 and 1
        val entries = fakePlaylistDao.getPlaylistTrackEntries(pId)
        assertEquals(0, entries[0].position)
        assertEquals(1, entries[1].position)
    }

    // ==================== Correction #3: Delete Playlist with Orphaned Tracks ====================

    @Test
    fun `deleting playlist containing orphaned tracks removes playlist_tracks and leaves tracks table unaffected`() = runTest {
        val pId = repository.createPlaylist("Special Playlist")
        val t1 = createTrack(1L, "Active Track", isMissing = false)
        val t2 = createTrack(2L, "Orphaned Track", isMissing = true)
        fakeTrackDao.storedTracks.addAll(listOf(t1, t2))

        repository.addTrackToPlaylist(pId, 1L)
        // Add orphaned track to playlist_tracks
        fakePlaylistDao.addTrackToPlaylist(
            PlaylistTrackEntity(playlistId = pId, trackId = 2L, position = 1, addedAt = 1000L)
        )

        assertEquals(2, fakePlaylistDao.getPlaylistTrackEntries(pId).size)

        // Delete the playlist
        repository.deletePlaylist(pId)

        // 1. Playlist is gone
        assertNull(repository.getPlaylist(pId))

        // 2. All playlist_tracks entries for this playlist are deleted (cascade)
        assertEquals(0, fakePlaylistDao.getPlaylistTrackEntries(pId).size)

        // 3. Underlying tracks table is completely unaffected (no foreign key cascade deletion of tracks)
        assertEquals(2, fakeTrackDao.storedTracks.size)
        assertFalse(fakeTrackDao.getTrackById(1L)!!.isMissing)
        assertTrue(fakeTrackDao.getTrackById(2L)!!.isMissing)
    }
}

class FakePlaylistDao : PlaylistDao {
    val playlists = mutableListOf<PlaylistEntity>()
    val playlistTracks = mutableListOf<PlaylistTrackEntity>()
    var trackDaoRef: FakeTrackDao? = null
    private var nextId = 1L

    private val _playlistsFlow = MutableStateFlow<List<PlaylistDao.PlaylistSummary>>(emptyList())

    private fun updateFlow() {
        val summaries = playlists.map { p ->
            val trackIds = playlistTracks.filter { it.playlistId == p.playlistId }.map { it.trackId }
            val activeCount = trackDaoRef?.storedTracks?.count { !it.isMissing && trackIds.contains(it.trackId) } ?: 0
            PlaylistDao.PlaylistSummary(
                playlist_id = p.playlistId,
                name = p.name,
                created_at = p.createdAt,
                updated_at = p.updatedAt,
                track_count = activeCount
            )
        }.sortedBy { it.name.lowercase() }
        _playlistsFlow.value = summaries
    }

    override fun observeAllPlaylistsWithCount(): Flow<List<PlaylistDao.PlaylistSummary>> {
        updateFlow()
        return _playlistsFlow
    }

    override suspend fun getPlaylistById(playlistId: Long): PlaylistEntity? {
        return playlists.find { it.playlistId == playlistId }
    }

    override suspend fun insertPlaylist(playlist: PlaylistEntity): Long {
        val id = if (playlist.playlistId > 0) playlist.playlistId else nextId++
        val entity = playlist.copy(playlistId = id)
        playlists.add(entity)
        updateFlow()
        return id
    }

    override suspend fun updatePlaylist(playlist: PlaylistEntity) {
        val idx = playlists.indexOfFirst { it.playlistId == playlist.playlistId }
        if (idx >= 0) {
            playlists[idx] = playlist
            updateFlow()
        }
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        playlists.removeIf { it.playlistId == playlistId }
        // Cascade delete on playlist_id
        playlistTracks.removeIf { it.playlistId == playlistId }
        updateFlow()
    }

    override fun observeTracksForPlaylist(playlistId: Long): Flow<List<TrackEntity>> {
        val trackMap = trackDaoRef?.storedTracks?.associateBy { it.trackId } ?: emptyMap()
        val entries = playlistTracks.filter { it.playlistId == playlistId }.sortedBy { it.position }
        val result = entries.mapNotNull { trackMap[it.trackId] }.filter { !it.isMissing }
        return flowOf(result)
    }

    override suspend fun getTracksForPlaylist(playlistId: Long): List<TrackEntity> {
        val trackMap = trackDaoRef?.storedTracks?.associateBy { it.trackId } ?: emptyMap()
        val entries = playlistTracks.filter { it.playlistId == playlistId }.sortedBy { it.position }
        return entries.mapNotNull { trackMap[it.trackId] }.filter { !it.isMissing }
    }

    override suspend fun getPlaylistTrackEntries(playlistId: Long): List<PlaylistTrackEntity> {
        return playlistTracks.filter { it.playlistId == playlistId }.sortedBy { it.position }
    }

    override suspend fun getMaxPosition(playlistId: Long): Int? {
        return playlistTracks.filter { it.playlistId == playlistId }.maxOfOrNull { it.position }
    }

    override suspend fun addTrackToPlaylist(entry: PlaylistTrackEntity): Long {
        if (playlistTracks.any { it.playlistId == entry.playlistId && it.trackId == entry.trackId }) {
            return -1L // Conflict ignored (composite PK)
        }
        playlistTracks.add(entry)
        updateFlow()
        return 1L
    }

    override suspend fun addTracksToPlaylist(entries: List<PlaylistTrackEntity>) {
        for (entry in entries) {
            if (!playlistTracks.any { it.playlistId == entry.playlistId && it.trackId == entry.trackId }) {
                playlistTracks.add(entry)
            }
        }
        updateFlow()
    }

    override suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        playlistTracks.removeIf { it.playlistId == playlistId && it.trackId == trackId }
        updateFlow()
    }

    override suspend fun removeTrackFromAllPlaylists(trackId: Long) {
        playlistTracks.removeIf { it.trackId == trackId }
        updateFlow()
    }

    override suspend fun insertOrUpdatePlaylistTracks(entries: List<PlaylistTrackEntity>) {
        for (entry in entries) {
            val idx = playlistTracks.indexOfFirst { it.playlistId == entry.playlistId && it.trackId == entry.trackId }
            if (idx >= 0) {
                playlistTracks[idx] = entry
            } else {
                playlistTracks.add(entry)
            }
        }
        updateFlow()
    }
}

class FakeFavoriteDao : FavoriteDao {
    val favorites = mutableSetOf<Long>()
    private val _flow = MutableStateFlow<List<Long>>(emptyList())

    override fun observeFavoriteTrackEntities(): Flow<List<TrackEntity>> = flowOf(emptyList())
    override suspend fun getFavoriteTrackEntities(): List<TrackEntity> = emptyList()

    override fun observeFavoriteTrackIds(): Flow<List<Long>> {
        _flow.value = favorites.toList()
        return _flow
    }

    override suspend fun getFavoriteTrackIds(): List<Long> = favorites.toList()

    override fun observeIsFavorite(trackId: Long): Flow<Boolean> = flowOf(favorites.contains(trackId))
    override suspend fun isFavorite(trackId: Long): Boolean = favorites.contains(trackId)

    override suspend fun addFavorite(favorite: FavoriteTrackEntity) {
        favorites.add(favorite.trackId)
        _flow.value = favorites.toList()
    }

    override suspend fun removeFavorite(trackId: Long) {
        favorites.remove(trackId)
        _flow.value = favorites.toList()
    }
}

class FakeTrackDao : TrackDao {
    val storedTracks = mutableListOf<TrackEntity>()

    override fun observeAllActiveTracks(): Flow<List<TrackEntity>> = flowOf(storedTracks.filter { !it.isMissing })
    override suspend fun getAllActiveTracks(): List<TrackEntity> = storedTracks.filter { !it.isMissing }
    override suspend fun getAllStoredTracks(): List<TrackEntity> = storedTracks.toList()
    override suspend fun getTrackById(trackId: Long): TrackEntity? = storedTracks.find { it.trackId == trackId }
    override suspend fun getTracksByIds(trackIds: List<Long>): List<TrackEntity> = storedTracks.filter { trackIds.contains(it.trackId) }
    override suspend fun getTrackByMediaStoreId(mediaStoreId: Long): TrackEntity? = storedTracks.find { it.mediaStoreId == mediaStoreId }

    override fun searchTracks(query: String): Flow<List<TrackEntity>> = flowOf(emptyList())

    override fun observeRecentlyAddedTracks(limit: Int): Flow<List<TrackEntity>> =
        flowOf(storedTracks.filter { !it.isMissing }.sortedByDescending { it.dateAdded }.take(limit))

    override suspend fun getRecentlyAddedTracks(limit: Int): List<TrackEntity> =
        storedTracks.filter { !it.isMissing }.sortedByDescending { it.dateAdded }.take(limit)

    override fun observeAllAlbums(): Flow<List<TrackDao.AlbumSummary>> = flowOf(emptyList())
    override suspend fun getAlbumById(albumId: Long): TrackDao.AlbumSummary? = null
    override fun observeTracksForAlbum(albumId: Long): Flow<List<TrackEntity>> = flowOf(emptyList())
    override suspend fun getTracksForAlbum(albumId: Long): List<TrackEntity> = emptyList()

    override fun observeAllArtists(): Flow<List<TrackDao.ArtistSummary>> = flowOf(emptyList())
    override fun observeAlbumsForArtist(artistNormalized: String): Flow<List<TrackDao.AlbumSummary>> = flowOf(emptyList())
    override fun observeTracksForArtist(artistNormalized: String): Flow<List<TrackEntity>> = flowOf(emptyList())
    override suspend fun getTracksForArtist(artistNormalized: String): List<TrackEntity> = emptyList()

    override suspend fun findReLinkCandidates(minDurationMs: Long, maxDurationMs: Long, sizeBytes: Long): List<TrackEntity> {
        return storedTracks.filter {
            it.isMissing && it.durationMs in minDurationMs..maxDurationMs && it.sizeBytes == sizeBytes
        }
    }

    override suspend fun insertTracks(tracks: List<TrackEntity>): List<Long> {
        storedTracks.addAll(tracks)
        return tracks.map { it.trackId }
    }

    override suspend fun insertTrack(track: TrackEntity): Long {
        storedTracks.add(track)
        return track.trackId
    }

    override suspend fun updateTracks(tracks: List<TrackEntity>) {
        for (track in tracks) {
            updateTrack(track)
        }
    }

    override suspend fun updateTrack(track: TrackEntity) {
        val idx = storedTracks.indexOfFirst { it.trackId == track.trackId }
        if (idx >= 0) {
            storedTracks[idx] = track
        }
    }

    override suspend fun markTracksMissing(trackIds: List<Long>) {
        for (id in trackIds) {
            val idx = storedTracks.indexOfFirst { it.trackId == id }
            if (idx >= 0) {
                storedTracks[idx] = storedTracks[idx].copy(isMissing = true)
            }
        }
    }

    override suspend fun markTracksPresent(trackIds: List<Long>, timestamp: Long) {
        for (id in trackIds) {
            val idx = storedTracks.indexOfFirst { it.trackId == id }
            if (idx >= 0) {
                storedTracks[idx] = storedTracks[idx].copy(isMissing = false, lastSeenTimestamp = timestamp)
            }
        }
    }

    override suspend fun purgeOrphanedTracks(purgeCutoffTimestamp: Long): Int {
        val count = storedTracks.count { it.isMissing && it.lastSeenTimestamp < purgeCutoffTimestamp }
        storedTracks.removeIf { it.isMissing && it.lastSeenTimestamp < purgeCutoffTimestamp }
        return count
    }

    override suspend fun reLinkTrack(trackId: Long, newMediaStoreId: Long, timestamp: Long) {
        val idx = storedTracks.indexOfFirst { it.trackId == trackId }
        if (idx >= 0) {
            storedTracks[idx] = storedTracks[idx].copy(
                mediaStoreId = newMediaStoreId,
                isMissing = false,
                lastSeenTimestamp = timestamp
            )
        }
    }
}
