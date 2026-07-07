package com.android.streamhub.feature.iptv.data.epg

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ProgrammeEntity::class], version = 1, exportSchema = false)
abstract class EpgDatabase : RoomDatabase() {
    abstract fun epgDao(): EpgDao
}
