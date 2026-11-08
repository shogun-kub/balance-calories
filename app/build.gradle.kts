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
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
