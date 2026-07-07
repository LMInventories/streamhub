package com.android.streamhub.feature.iptv.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.iptvDataStore: DataStore<Preferences> by preferencesDataStore(name = "iptv_settings")

@Module
@InstallIn(SingletonComponent::class)
object IptvDataStoreModule {

    @Provides
    @Singleton
    fun provideIptvDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.iptvDataStore
}
