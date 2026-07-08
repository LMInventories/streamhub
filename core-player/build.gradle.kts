plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.android.streamhub.core.player"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // PlaybackItem (core-common) and ExoPlayer/Player (media3) appear in PlayerController's
    // public signature, so downstream modules need them on their own compile classpath too -
    // `api`, not `implementation`.
    api(project(":core-common"))
    api(libs.androidx.media3.exoplayer)
    api(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer.hls)
    api(libs.androidx.media3.ui)
    // Core Media3 doesn't ship AC3/EAC3 (Dolby) software decoding for licensing reasons - it
    // relies on the device's hardware decoder, which plenty of phones/boxes don't have, and
    // playback then goes silent with no error. This is a prebuilt Maven artifact from the
    // Jellyfin project (GPL-3.0, fine for a personal-use app) - Google's own FFmpeg extension
    // isn't published to Maven at all and would need building from source with the NDK.
    implementation(libs.jellyfin.media3.ffmpeg.decoder)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit)
    // Format/TrackGroup/Tracks touch android.* framework internals during construction;
    // Robolectric shims those so the test JVM doesn't need a real device.
    testImplementation(libs.robolectric)
}
