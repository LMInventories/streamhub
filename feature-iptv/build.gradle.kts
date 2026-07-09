plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.android.streamhub.feature.iptv"
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

    // NotificationCompat/NotificationManagerCompat/ContextCompat for the EPG reminder feature.
    implementation(libs.androidx.core.ktx)
    // rememberLauncherForActivityResult for the POST_NOTIFICATIONS runtime permission request.
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)

    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.tv.foundation)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Cast-to-device button on the Live TV mini-player preview. Phone-only feature (casting
    // makes sense from a handheld to a TV, not from an Android TV device to itself), but the
    // dependency lives at the module level since LiveTvScreenPhone/Tv share this module.
    implementation(libs.play.services.cast.framework)
    implementation(libs.androidx.mediarouter)
    // MediaRouteButton reads AppCompat-only theme attributes (mediaRouteButtonStyle etc.) that
    // the app's own platform Theme.Material doesn't define - not used for anything else, only to
    // give CastButton's ContextThemeWrapper an AppCompat theme to pull those from.
    implementation(libs.androidx.appcompat)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
