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
    // @Inject/@Singleton on FullscreenOverlayState - plain JSR-330 annotations, not Hilt-specific.
    // core-common has no Hilt/Android plugin of its own; Hilt's graph in :app discovers this
    // class fine as long as the annotations themselves are on this module's classpath.
    implementation(libs.javax.inject)

    testImplementation(libs.junit)
}
