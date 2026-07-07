package com.android.streamhub.feature.iptv.di

import android.content.Context
import androidx.room.Room
import com.android.streamhub.feature.iptv.data.epg.EpgDao
import com.android.streamhub.feature.iptv.data.epg.EpgDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EpgDatabaseModule {

    @Provides
    @Singleton
    fun provideEpgDatabase(@ApplicationContext context: Context): EpgDatabase =
        Room.databaseBuilder(context, EpgDatabase::class.java, "epg.db").build()

    @Provides
    fun provideEpgDao(database: EpgDatabase): EpgDao = database.epgDao()
}
