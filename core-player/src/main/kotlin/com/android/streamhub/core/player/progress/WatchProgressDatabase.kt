package com.android.streamhub.core.player.progress

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [WatchProgressEntity::class], version = 1, exportSchema = false)
abstract class WatchProgressDatabase : RoomDatabase() {
    abstract fun watchProgressDao(): WatchProgressDao
}
