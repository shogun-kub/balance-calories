plugins {
    // AGP 9 has Kotlin support built in: applying org.jetbrains.kotlin.android is an error.
    // Compose is switched on through buildFeatures.compose below.
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.balancecalories.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))

    // Exposed to :app because the permission contract is part of this module's surface.
    api(libs.androidx.health.connect.client)
}
