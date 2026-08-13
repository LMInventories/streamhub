import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

// TMDB_API_KEY is read from local.properties (gitignored, machine-local) rather than committed -
// see the "TMDB_API_KEY" line this build script expects the developer to add there. Falls back to
// a Gradle property of the same name (e.g. for CI, via -PTMDB_API_KEY=... or ORG_GRADLE_PROJECT_
// TMDB_API_KEY) so this doesn't hard-require local.properties. An empty key still compiles fine -
// TmdbRepository wraps every call in runCatching, so a missing key just means actor info silently
// stays unavailable instead of crashing.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val tmdbApiKey: String =
    (localProperties.getProperty("TMDB_API_KEY") ?: providers.gradleProperty("TMDB_API_KEY").orNull ?: "")

android {
    namespace = "com.android.streamhub.core.tmdb"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
    }

    buildFeatures {
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

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    testImplementation(libs.junit)
}
