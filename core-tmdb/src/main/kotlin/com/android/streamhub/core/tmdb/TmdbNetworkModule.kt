package com.android.streamhub.core.tmdb

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

// feature-iptv's IptvNetworkModule and feature-emby's EmbyNetworkModule already provide their own
// unqualified OkHttpClient/Json into the same app-wide Hilt SingletonComponent - an unqualified
// binding here would be a Dagger duplicate-binding compile error, same reasoning as EmbyOkHttpClient/
// EmbyJson.
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TmdbOkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TmdbJson

@Module
@InstallIn(SingletonComponent::class)
internal object TmdbNetworkModule {

    @Provides
    @Singleton
    @TmdbOkHttpClient
    fun provideTmdbOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    @Provides
    @Singleton
    @TmdbJson
    fun provideTmdbJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // TMDB's base URL is fixed (unlike Emby's runtime-configured server), so a single Retrofit/
    // TmdbApi instance suffices - no per-base-URL caching needed the way EmbyRemoteDataSource does.
    @Provides
    @Singleton
    fun provideTmdbRetrofit(@TmdbOkHttpClient client: OkHttpClient, @TmdbJson json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideTmdbApi(retrofit: Retrofit): TmdbApi = retrofit.create(TmdbApi::class.java)
}
