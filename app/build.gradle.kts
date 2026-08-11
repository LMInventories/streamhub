plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.android.streamhub"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.android.streamhub"
        minSdk = 26
        targetSdk = 36
        versionCode = 26
        versionName = "0.4.22"
    }

    signingConfigs {
        // A stable, committed keystore rather than AGP's implicit per-machine debug.keystore -
        // CI runs on a fresh runner every time with no ~/.android/debug.keystore, so without
        // this each build was signed with a brand new random key, and Android refuses to
        // install-update an APK signed with a different key than what's already installed
        // ("package conflicts with an existing package"). This keystore is a plain debug-only
        // key (standard "android"/"androiddebugkey" credentials) - never used for release/Play
        // Store signing, safe to commit.
        create("debugStable") {
            storeFile = file("../keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debugStable")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debugStable")
        }
    }

    buildFeatures {
        compose = true
        // BuildConfig.VERSION_CODE/VERSION_NAME - read by AppUpdateRepository to compare against
        // the latest GitHub Release's tag.
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-player"))
    implementation(project(":core-ui-phone"))
    implementation(project(":core-ui-tv"))
    implementation(project(":feature-player-screen"))
    implementation(project(":feature-iptv"))
    implementation(project(":feature-jellyfin"))
    implementation(project(":feature-emby"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    // MainActivity extends FragmentActivity, not plain ComponentActivity - the Live TV Cast
    // button's MediaRouteButton.showDialog() walks up the Context chain looking for a
    // FragmentActivity to host its device-picker DialogFragment, and crashes when it doesn't
    // find one.
    implementation(libs.androidx.fragment.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.tv.foundation)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // AppUpdateRepository's GitHub Releases API call - feature-iptv already depends on this, but
    // only as `implementation`, which doesn't expose OkHttp's types to :app's own compile
    // classpath.
    implementation(libs.okhttp)

    // Backs AppUiSettingsRepository (theme mode + text size) - same DataStore-as-JSON pattern
    // feature-jellyfin/feature-iptv already use for their own settings.
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    // CastOptionsProvider lives here since AndroidManifest.xml's OPTIONS_PROVIDER_CLASS_NAME
    // meta-data needs it on the app module's own classpath - the Cast button/session-driving
    // logic itself lives in feature-iptv instead, next to what it actually casts.
    implementation(libs.play.services.cast.framework)
    // Theme.StreamHub (res/values/themes.xml) extends Theme.AppCompat.NoActionBar - the actual
    // activity theme needs to be AppCompat-derived for the Cast button's device-picker dialog to
    // inflate without crashing, not just the button itself (see CastButton.kt's own comment).
    implementation(libs.androidx.appcompat)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
