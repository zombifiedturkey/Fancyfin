plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes the version from the release tag: gradle assembleRelease -PversionName=0.1.0
val appVersion = (project.findProperty("versionName") as String?) ?: "0.0.0-dev"

/** 1.2.3 becomes 10203, so newer versions always have a higher code. */
fun versionCodeOf(version: String): Int {
    val parts = version.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    val code = parts.getOrElse(0) { 0 } * 10000 + parts.getOrElse(1) { 0 } * 100 + parts.getOrElse(2) { 0 }
    return maxOf(code, 1)
}

android {
    namespace = "com.fancyfin.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fancyfin.tv"
        minSdk = 24
        targetSdk = 34
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion
        // Where the app looks for new versions: this repository's releases tagged tv-v*.
        buildConfigField("String", "UPDATE_REPO", "\"zombifiedturkey/Fancyfin\"")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    // Release builds are signed with the key CI is given (see android/README.md). Without one,
    // the debug key is used so the project still builds, but such an APK can't update a real one.
    val keystorePath = System.getenv("ANDROID_KEYSTORE_FILE")
    if (!keystorePath.isNullOrBlank()) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose: the screens. The BOM keeps the Compose pieces on matching versions.
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Pictures.
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Playback (Media3 is the same engine the official Jellyfin TV app uses).
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
