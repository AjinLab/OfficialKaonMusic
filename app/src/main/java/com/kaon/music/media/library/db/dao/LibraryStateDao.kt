package com.kaon.music.media.library.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kaon.music.media.library.db.entity.LibraryStateEntity

@Dao
interface LibraryStateDao {
    @Query("SELECT * FROM library_state WHERE id = 0")
    suspend fun getState(): LibraryStateEntity?

    @Upsert
    suspend fun upsertState(state: LibraryStateEntity)
}
