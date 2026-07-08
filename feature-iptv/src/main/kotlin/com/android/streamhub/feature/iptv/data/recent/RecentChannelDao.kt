package com.android.streamhub.feature.iptv.data.recent

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentChannelDao {
    @Query("SELECT * FROM recent_channels ORDER BY lastViewedAtEpochSeconds DESC LIMIT 10")
    fun observeRecent(): Flow<List<RecentChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecentChannelEntity)

    // Queries already LIMIT 10, but without this the table itself would grow unbounded over
    // time (every distinct channel ever viewed, forever) instead of actually staying capped.
    @Query("DELETE FROM recent_channels WHERE channelId NOT IN (SELECT channelId FROM recent_channels ORDER BY lastViewedAtEpochSeconds DESC LIMIT 10)")
    suspend fun trimToLimit()
}
