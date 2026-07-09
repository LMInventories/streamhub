package com.android.streamhub.feature.jellyfin.di

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
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
import org.jellyfin.sdk.model.DeviceInfo
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
        // Explicit rather than left to the SDK's own auto-population from context: that pulls the
        // device name from Settings.Global.DEVICE_NAME, a user-customizable field that can contain
        // anything (emoji, non-Latin scripts) - which then goes straight into the Authorization
        // header's Device="..." field. HTTP header values are supposed to be ASCII; OkHttp doesn't
        // enforce that on the way out, but a server's header parser choking on a non-ASCII value
        // unhandled is a very plausible way to get an opaque 500 back. Build.MODEL is manufacturer-
        // set and always plain ASCII, so it can't carry this failure mode.
        deviceInfo = DeviceInfo(id = androidId(context), name = Build.MODEL.toAsciiSafe())
        // Wraps the SDK's default OkHttp-backed client with a debug interceptor so a failed
        // sign-in can surface exactly what was sent/received - see JellyfinDebugInterceptor.
        apiClientFactory = OkHttpFactory(
            base = OkHttpClient.Builder()
                .addInterceptor(JellyfinDebugInterceptor)
                .build(),
        )
    }

    @SuppressLint("HardwareIds")
    private fun androidId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

    // Belt-and-suspenders alongside avoiding Settings.Global.DEVICE_NAME above - Build.MODEL is
    // manufacturer-set and practically always plain ASCII, but this guarantees it regardless of
    // OEM quirks, since a non-ASCII byte here is exactly what's suspected of crashing the server's
    // header parser.
    private fun String.toAsciiSafe(): String = filter { it.code in 0x20..0x7E }.ifBlank { "Android" }
}
