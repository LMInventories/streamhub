plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.android.streamhub.feature.jellyfin"
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
    api(project(":core-common"))
    api(project(":core-design"))
    api(project(":core-player"))
    implementation(project(":core-ui-phone"))
    implementation(project(":core-ui-tv"))

    // Handles its own HTTP client (Ktor/OkHttp engine) internally - no separate Retrofit stack
    // needed here the way feature-iptv needs one for its own hand-rolled Xtream/M3U clients.
    // okhttp itself is still a direct dependency below so JellyfinDebugInterceptor can plug into
    // the SDK's OkHttpFactory rather than relying on whatever jellyfin-core exposes transitively.
    implementation(libs.jellyfin.sdk.core)
    implementation(libs.okhttp)
    // jellyfin-core references org.slf4j.LoggerFactory at runtime (via Ktor/one of its own
    // dependencies) but only depends on slf4j-api at compile time, not runtime - without these,
    // sign-in crashes with NoClassDefFoundError: Failed resolution of: Lorg/slf4j/LoggerFactory
    // the moment the SDK's HTTP client is used. slf4j-android is a maintained Android-compatible
    // binding (routes to android.util.Log) - confirmed as the fix by Findroid, a real production
    // Jellyfin Android app using the same SDK, which needs the identical pair for the same reason.
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)

    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.tv.foundation)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
