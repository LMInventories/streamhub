package com.android.streamhub.feature.iptv.data.favorites

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteChannelDao {
    // Most-recently-added first, so pinning favourites to the top of a list has a stable,
    // sensible order rather than whatever order SQLite happens to return rows in.
    @Query("SELECT * FROM favorite_channels ORDER BY addedAtEpochSeconds DESC")
    fun observeAll(): Flow<List<FavoriteChannelEntity>>

    @Query("SELECT channelId FROM favorite_channels")
    fun observeFavoriteIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entity: FavoriteChannelEntity)

    @Query("DELETE FROM favorite_channels WHERE channelId = :channelId")
    suspend fun remove(channelId: String)
}
