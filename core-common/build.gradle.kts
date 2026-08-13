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
    // api (not implementation): LibraryTitleFinder's @JellyfinLibraryFinder/@EmbyLibraryFinder
    // qualifier annotations are defined here and consumed by other modules (feature-jellyfin,
    // feature-emby, feature-person), so javax.inject.Qualifier needs to be on their compile
    // classpath too, not just this module's own.
    api(libs.javax.inject)

    testImplementation(libs.junit)
}
