plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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
}
