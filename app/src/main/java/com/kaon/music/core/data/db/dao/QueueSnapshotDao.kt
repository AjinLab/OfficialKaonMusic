package com.kaon.music.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kaon.music.core.data.db.entity.QueueSnapshotEntity

@Dao
interface QueueSnapshotDao {

    @Query("SELECT * FROM queue_snapshot WHERE id = 1 LIMIT 1")
    suspend fun getQueueSnapshot(): QueueSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveQueueSnapshot(snapshot: QueueSnapshotEntity)

    @Query("DELETE FROM queue_snapshot WHERE id = 1")
    suspend fun clearQueueSnapshot()
}
