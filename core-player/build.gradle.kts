plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
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

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)

    // @Inject-constructor classes here still need Dagger/Hilt's annotation processor to
    // generate their factories, even though this module never applies the Hilt Gradle plugin
    // itself (that only runs where @AndroidEntryPoint/@HiltAndroidApp live, i.e. :app).
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit)
    // Format/TrackGroup/Tracks touch android.* framework internals during construction;
    // Robolectric shims those so the test JVM doesn't need a real device.
    testImplementation(libs.robolectric)
}
