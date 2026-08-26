import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

fun getDateTime(): String {
    val df = SimpleDateFormat("dd MMMMM yyyy")
    return "${df.format(Date())} г."
}

val localProperties = Properties().apply {
    rootProject.file("local.properties")
        .takeIf { it.isFile }
        ?.inputStream()
        ?.use { load(it) }
}
val releaseSigningProperties = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val hasReleaseSigningConfig = releaseSigningProperties.all {
    !localProperties.getProperty(it).isNullOrBlank()
}

android {
    namespace = "ru.radiationx.anilibria"

    compileSdk = libs.versions.app.compile.sdk.version.get().toInt()

    defaultConfig {
        applicationId = "com.aniru.tv"
        minSdk = libs.versions.tv.min.sdk.version.get().toInt()
        targetSdk = libs.versions.app.target.sdk.version.get().toInt()
        versionCode = 18
        versionName = "1.2.6"
        buildConfigField("String", "BUILD_DATE", "\"${getDateTime()}\"")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(localProperties.getProperty("storeFile"))
                storePassword = localProperties.getProperty("storePassword")
                keyAlias = localProperties.getProperty("keyAlias")
                keyPassword = localProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions += listOf("type")
    productFlavors {
        create("app") {
            dimension = "type"
            buildConfigField("boolean", "FOR_RUSTORE", "false")
        }

        create("rustore") {
            dimension = "type"
            buildConfigField("boolean", "FOR_RUSTORE", "true")
            versionName = "1.2.6-rustore"
        }
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val inputApkName = buildString {
            append("${project.name}-")
            variant.productFlavors.forEach { (dimension, flavor) ->
                append("${flavor}-")
            }
            variant.buildType?.also { append(it) }
            append(".apk")
        }
        val inputApkPath = buildString {
            append("outputs/apk/")
            variant.flavorName?.also { append("$it/") }
            variant.buildType?.also { append("$it/") }
            append(inputApkName)
        }
        val inputPath = project.layout.buildDirectory.file(inputApkPath).get().asFile

        val appName = "AniRu_TV"
        val versionName = variant.outputs[0].versionName.get()
        val outputApkName = "${appName}_v${versionName}.apk"

        val buildName = variant.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        tasks.register<Copy>("copy${buildName}Apk") {
            description = "Copies $buildName APK to another folder"
            group = "custom"
            from(inputPath) {
                rename { outputApkName }
            }
            into("${rootProject.rootDir}/release-apks/")
            dependsOn(tasks.named("assemble$buildName"))
        }
    }
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.version.get().toInt())
}

dependencies {
    implementation(libs.kotlin.stdlib)

    implementation(project(":data"))
    implementation(project(":animevost-sdk"))
    implementation(project(":shared-android-ktx"))
    implementation(project(":shared-app"))
    implementation(project(":quill-di"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.leanback)
    implementation(libs.androidx.leanback.preference)
    implementation(libs.google.material)
    implementation(libs.androidx.constraintlayout)

    implementation(libs.cicerone)

    compileOnly(libs.toothpick)
    ksp(libs.toothpick.compiler)

    implementation(libs.media3.ui.leanback)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.okhttp)
    implementation("org.jsoup:jsoup:1.18.3")

    implementation(libs.mintpermissions)
    implementation(libs.mintpermissions.flows)

    implementation(libs.viewbindingpropertydelegate)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.3.0")
}
