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

    // endAtEpochSeconds (not startAtEpochSeconds) >= fromEpochSeconds so a currently-airing match
    // isn't excluded just because it started in the past - Search's "upcoming episodes" framing
    // still wants to surface something airing right now.
    @Query(
        "SELECT * FROM programmes WHERE title LIKE '%' || :query || '%' " +
            "AND endAtEpochSeconds >= :fromEpochSeconds ORDER BY startAtEpochSeconds LIMIT :limit",
    )
    suspend fun searchProgrammes(query: String, fromEpochSeconds: Long, limit: Int): List<ProgrammeEntity>

    @Query("SELECT COUNT(*) FROM programmes")
    suspend fun count(): Int
}
