plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// Hilt 2.58's bundled kotlin-metadata-jvm reader caps out at metadata version 2.3.0, but a few
// very recently released dependencies (OkHttp 5.4.0, Coil 3.5.0 - both shipped right around
// Kotlin 2.4.0's release) carry 2.4.0 metadata. kotlin-metadata-jvm's reader is backward
// compatible, so forcing the newest reader everywhere is safe and avoids hunting down every
// offending library's last pre-2.4.0 release individually.
allprojects {
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-metadata-jvm:2.4.0")
        }
    }
}
