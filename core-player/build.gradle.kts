plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.android.streamhub.core.player"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = false
        // Media3's Format/TrackGroup/Tracks touch a couple of android.* framework calls in
        // static init; without a real device/Robolectric, plain JUnit needs default stub
        // values instead of "not mocked" exceptions.
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core-common"))

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    // @Inject-constructor classes here still need Dagger/Hilt's annotation processor to
    // generate their factories, even though this module never applies the Hilt Gradle plugin
    // itself (that only runs where @AndroidEntryPoint/@HiltAndroidApp live, i.e. :app).
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit)
}
