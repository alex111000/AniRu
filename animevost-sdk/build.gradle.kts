plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.animevost.sdk"
    compileSdk = libs.versions.app.compile.sdk.version.get().toInt()

    defaultConfig {
        minSdk = libs.versions.tv.min.sdk.version.get().toInt()
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jsoup:jsoup:1.18.3")
    testImplementation("junit:junit:4.13.2")
    // Existing SDK tests import kotlin.test.*; bridge them to JUnit 4 for AGP unit tests.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.3.0")
}
