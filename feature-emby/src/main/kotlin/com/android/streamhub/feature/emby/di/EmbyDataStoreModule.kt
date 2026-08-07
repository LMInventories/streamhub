package com.android.streamhub.feature.emby.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

// A separate DataStore file from feature-iptv's "iptv_settings"/feature-jellyfin's
// "jellyfin_settings" - each source's config is its own concern, and reusing one shared
// DataStore/Preferences instance across independent feature modules would recreate the same
// "who owns this key" coupling this app's architecture avoids by keeping feature modules from
// depending on each other.
private val Context.embyDataStore: DataStore<Preferences> by preferencesDataStore(name = "emby_settings")

// feature-iptv's IptvDataStoreModule and feature-jellyfin's JellyfinDataStoreModule also provide
// their own DataStore<Preferences> - since Hilt's SingletonComponent is assembled app-wide from
// every feature module :app depends on, an unqualified binding here would collide with those.
// This qualifier is what keeps them apart.
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EmbyDataStore

@Module
@InstallIn(SingletonComponent::class)
object EmbyDataStoreModule {

    @Provides
    @Singleton
    @EmbyDataStore
    fun provideEmbyDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.embyDataStore
}
