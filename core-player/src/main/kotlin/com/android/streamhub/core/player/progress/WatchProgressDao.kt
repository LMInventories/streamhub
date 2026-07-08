package com.android.streamhub.core.player.progress

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WatchProgressDao {
    @Query("SELECT * FROM watch_progress WHERE sourceType = :sourceType AND itemId = :itemId")
    suspend fun get(sourceType: String, itemId: String): WatchProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchProgressEntity)
}
