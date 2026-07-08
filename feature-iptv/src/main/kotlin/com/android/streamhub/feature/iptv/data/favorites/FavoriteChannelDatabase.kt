package com.android.streamhub.feature.iptv.data.favorites

import androidx.room.Database
import androidx.room.RoomDatabase

// version 2: added epgChannelId (needed to resolve a favourited channel's EPG grid data
// correctly). No migration path - fallbackToDestructiveMigration in the Hilt module instead,
// since losing a locally-cached favourites list on this one upgrade is a trivial, re-addable
// cost against writing a real Migration for a personal-use app's first schema change.
@Database(entities = [FavoriteChannelEntity::class], version = 2, exportSchema = false)
abstract class FavoriteChannelDatabase : RoomDatabase() {
    abstract fun favoriteChannelDao(): FavoriteChannelDao
}
