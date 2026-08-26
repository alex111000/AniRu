pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "AniRu"

include(
    ":app-mobile",
    ":data",
    ":shared-ktx",
    ":shared-android-ktx",
    ":searchbar",
    ":app-tv",
    ":shared-app",
    ":quill-di",
    ":media-mobile",
    ":taiwa",
    ":envoy",
    ":animevost-sdk"
)
