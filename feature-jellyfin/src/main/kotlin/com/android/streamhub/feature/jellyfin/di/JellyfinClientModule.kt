package com.android.streamhub.feature.jellyfin.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object JellyfinClientModule {

    // One Jellyfin instance app-wide, not per-server-config - it's the SDK's factory/registry
    // object (device info, client identity), not tied to a specific server address or session.
    // JellyfinSourceConfigRepository (added when the settings screen lands) drives which server
    // jellyfin.createApi(baseUrl = ...) actually points at.
    @Provides
    @Singleton
    fun provideJellyfin(@ApplicationContext context: Context): Jellyfin = createJellyfin {
        this.context = context
        clientInfo = ClientInfo(name = "StreamHub", version = "1.0")
        // Wraps the SDK's default OkHttp-backed client with a debug interceptor so a failed
        // sign-in can surface exactly what was sent/received - see JellyfinDebugInterceptor.
        apiClientFactory = OkHttpFactory(
            base = OkHttpClient.Builder()
                .addInterceptor(JellyfinDebugInterceptor)
                .build(),
        )
    }
}
