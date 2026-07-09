package com.android.streamhub.di

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

// Own DataStore file, not a feature module's - app-wide UI preferences (theme, text size) aren't
// any one source's concern, same "each owner keeps its own file" reasoning as feature-jellyfin's
// JellyfinDataStoreModule.
private val Context.appUiDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_ui_settings")

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppUiDataStore

@Module
@InstallIn(SingletonComponent::class)
object AppUiDataStoreModule {

    @Provides
    @Singleton
    @AppUiDataStore
    fun provideAppUiDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.appUiDataStore
}
