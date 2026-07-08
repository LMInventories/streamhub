package com.android.streamhub.core.player.di

import android.content.Context
import androidx.room.Room
import com.android.streamhub.core.common.domain.WatchProgressRepository
import com.android.streamhub.core.player.progress.WatchProgressDao
import com.android.streamhub.core.player.progress.WatchProgressDatabase
import com.android.streamhub.core.player.progress.WatchProgressRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WatchProgressDatabaseModule {
    @Provides
    @Singleton
    fun provideWatchProgressDatabase(@ApplicationContext context: Context): WatchProgressDatabase =
        Room.databaseBuilder(context, WatchProgressDatabase::class.java, "watch_progress.db").build()

    @Provides
    fun provideWatchProgressDao(database: WatchProgressDatabase): WatchProgressDao = database.watchProgressDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WatchProgressBindingModule {
    @Binds
    abstract fun bindWatchProgressRepository(impl: WatchProgressRepositoryImpl): WatchProgressRepository
}
