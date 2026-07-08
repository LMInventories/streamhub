package com.android.streamhub.feature.iptv.data.favorites

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [FavoriteChannelEntity::class], version = 1, exportSchema = false)
abstract class FavoriteChannelDatabase : RoomDatabase() {
    abstract fun favoriteChannelDao(): FavoriteChannelDao
}
