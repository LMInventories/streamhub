package com.android.streamhub.feature.iptv.di

import android.content.Context
import androidx.room.Room
import com.android.streamhub.feature.iptv.data.recent.RecentChannelDao
import com.android.streamhub.feature.iptv.data.recent.RecentChannelDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RecentChannelDatabaseModule {

    @Provides
    @Singleton
    fun provideRecentChannelDatabase(@ApplicationContext context: Context): RecentChannelDatabase =
        Room.databaseBuilder(context, RecentChannelDatabase::class.java, "recent_channels.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideRecentChannelDao(database: RecentChannelDatabase): RecentChannelDao = database.recentChannelDao()
}
