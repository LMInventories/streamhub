package com.android.streamhub.feature.emby.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Qualifier
import javax.inject.Singleton

// feature-iptv's IptvNetworkModule already provides an unqualified OkHttpClient/Json into the
// same app-wide Hilt SingletonComponent (assembled from every feature module :app depends on) -
// an unqualified binding here would be a Dagger duplicate-binding compile error. Same class of
// fix JellyfinDataStoreModule's qualifier already applies to DataStore<Preferences> for the same
// reason.
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EmbyOkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EmbyJson

@Module
@InstallIn(SingletonComponent::class)
object EmbyNetworkModule {

    @Provides
    @Singleton
    @EmbyOkHttpClient
    fun provideEmbyOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    @Provides
    @Singleton
    @EmbyJson
    fun provideEmbyJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
}
