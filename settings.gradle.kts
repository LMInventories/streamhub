pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "StreamHub"

include(
    ":app",
    ":core-common",
    ":core-design",
    ":core-player",
    ":core-tmdb",
    ":core-ui-phone",
    ":core-ui-tv",
    ":feature-player-screen",
    ":feature-person",
    ":feature-iptv",
    ":feature-jellyfin",
    ":feature-emby",
)
