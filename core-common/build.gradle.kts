plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Flow, used by MediaSource.observeRecentlyViewed() - a pure Kotlin/JVM dependency, no
    // Android-specific coroutines dispatcher needed at this layer.
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
