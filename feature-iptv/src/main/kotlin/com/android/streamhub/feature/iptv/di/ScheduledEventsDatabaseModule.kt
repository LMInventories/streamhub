package com.android.streamhub.feature.iptv.di

import android.content.Context
import androidx.room.Room
import com.android.streamhub.feature.iptv.data.scheduled.ScheduledEventsDao
import com.android.streamhub.feature.iptv.data.scheduled.ScheduledEventsDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ScheduledEventsDatabaseModule {

    @Provides
    @Singleton
    fun provideScheduledEventsDatabase(@ApplicationContext context: Context): ScheduledEventsDatabase =
        Room.databaseBuilder(context, ScheduledEventsDatabase::class.java, "scheduled_events.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideScheduledEventsDao(database: ScheduledEventsDatabase): ScheduledEventsDao = database.scheduledEventsDao()
}
