package com.android.streamhub.update.di

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

// Own DataStore file - update-check state isn't any one source's concern, same "each owner keeps
// its own file" reasoning as AppUiDataStoreModule/JellyfinDataStoreModule (an unqualified binding
// here would collide with IptvDataStoreModule's own unqualified one).
private val Context.appUpdateDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_update_settings")

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppUpdateDataStore

@Module
@InstallIn(SingletonComponent::class)
object AppUpdateModule {

    @Provides
    @Singleton
    @AppUpdateDataStore
    fun provideAppUpdateDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.appUpdateDataStore
}
