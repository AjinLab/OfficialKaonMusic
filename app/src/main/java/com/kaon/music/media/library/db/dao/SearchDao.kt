package com.kaon.music.media.library.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.kaon.music.media.library.db.entity.SearchResultProjection
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchDao {
    @Query("""
        SELECT id, 'SONG' as type, title, (SELECT name FROM artists WHERE artists.id = songs.artistId) as subtitle, albumId, 100 as score
        FROM songs WHERE title LIKE '%' || :query || '%'
        UNION ALL
        SELECT id, 'ALBUM' as type, title, (SELECT name FROM artists WHERE artists.id = albums.artistId) as subtitle, id as albumId, 80 as score
        FROM albums WHERE title LIKE '%' || :query || '%'
        UNION ALL
        SELECT id, 'ARTIST' as type, name as title, null as subtitle, null as albumId, 60 as score
        FROM artists WHERE name LIKE '%' || :query || '%'
        ORDER BY score DESC, title ASC
    """)
    fun search(query: String): Flow<List<SearchResultProjection>>
}
