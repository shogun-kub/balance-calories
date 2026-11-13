import java.util.Properties

plugins {
    // AGP 9 has Kotlin support built in: applying org.jetbrains.kotlin.android is an error, but the
    // Compose compiler plugin is still required once buildFeatures.compose is on.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.balancecalories.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.balancecalories"
        minSdk = 28
        targetSdk = 36
        // A release version is a reviewable diff, not a side effect of a build (AGENTS.md, hard rule 3).
        // 3 / 1.0.1 is the version the first F-Droid submission pointed at: 1.0.0 was tagged before the TalkBack
        // fixes and the torch fix landed, and a released tag is not moved - a new version says what is in it.
        // 4 / 1.0.2 exists for the self-hosted repository: the first build signed by the household's own key, so
        // that updates can arrive through an F-Droid client instead of `adb install -r`. The code is 1.0.1's.
        versionCode = 4
        versionName = "1.0.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // A keystore is the owner's, never the repository's (hard rule 2). When it is absent the release build is
    // simply unsigned, which is exactly what F-Droid wants: it signs with its own key, and a repository that
    // could not build without a private keystore could never be packaged there.
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties().apply {
        if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
    }
    val hasKeystore = keystorePropertiesFile.exists() && keystoreProperties.getProperty("storeFile") != null

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // On since 1.0.0: a shipped build is minified and its resources are shrunk, as every other
            // Android app on the planet does. The keep rules are in proguard-rules.pro and each one has a
            // reason next to it.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Scanning: CameraX for frames, zxing-cpp for decoding. One engine only - no flavour split until an
    // engine actually fails on a real barcode (ADR-017, amended).
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.zxing.cpp)

    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // Guards the two string files against drifting apart; runs on the JVM, not a device.
    testImplementation(libs.junit4)

    // The decoder test runs on a device: it loads the JNI library and decodes a generated barcode.
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
