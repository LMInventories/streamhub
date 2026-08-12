plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.android.streamhub.core.ui.tv"
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
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core-common"))
    api(project(":core-design"))
    // TvSettingsTextField needs Material3's OutlinedTextField (tv-material3 has no text field of
    // its own - the same reason every settings sub-screen with input fields wraps a local plain
    // Material3 theme instead of relying on tv-material3's ambient one) and appColorScheme() to
    // theme it correctly - already the established shared home for that helper regardless of
    // platform (feature-iptv/feature-jellyfin's own settings screens already pull it from here).
    implementation(project(":core-ui-phone"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    // animateDpAsState for TvScaffold's expand-on-focus nav rail width.
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.tv.foundation)
}
