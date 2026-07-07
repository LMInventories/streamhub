package com.android.streamhub.feature.iptv.data.epg

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EpgDao {
    @Query("DELETE FROM programmes")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(programmes: List<ProgrammeEntity>)

    @Query(
        "SELECT * FROM programmes WHERE channelId IN (:channelIds) " +
            "AND endAtEpochSeconds >= :fromEpochSeconds AND startAtEpochSeconds <= :toEpochSeconds " +
            "ORDER BY channelId, startAtEpochSeconds",
    )
    suspend fun getProgrammes(channelIds: List<String>, fromEpochSeconds: Long, toEpochSeconds: Long): List<ProgrammeEntity>

    @Query("SELECT COUNT(*) FROM programmes")
    suspend fun count(): Int
}
