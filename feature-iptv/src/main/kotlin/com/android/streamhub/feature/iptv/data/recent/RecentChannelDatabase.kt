package com.android.streamhub.feature.iptv.data.recent

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [RecentChannelEntity::class], version = 1, exportSchema = false)
abstract class RecentChannelDatabase : RoomDatabase() {
    abstract fun recentChannelDao(): RecentChannelDao
}
