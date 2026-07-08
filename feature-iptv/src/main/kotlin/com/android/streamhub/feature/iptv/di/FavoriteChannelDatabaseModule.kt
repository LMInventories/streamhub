package com.android.streamhub.feature.iptv.di

import android.content.Context
import androidx.room.Room
import com.android.streamhub.feature.iptv.data.favorites.FavoriteChannelDao
import com.android.streamhub.feature.iptv.data.favorites.FavoriteChannelDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FavoriteChannelDatabaseModule {

    @Provides
    @Singleton
    fun provideFavoriteChannelDatabase(@ApplicationContext context: Context): FavoriteChannelDatabase =
        Room.databaseBuilder(context, FavoriteChannelDatabase::class.java, "favorite_channels.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideFavoriteChannelDao(database: FavoriteChannelDatabase): FavoriteChannelDao = database.favoriteChannelDao()
}
