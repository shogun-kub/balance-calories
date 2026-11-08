plugins {
    // AGP 9 has Kotlin support built in: applying org.jetbrains.kotlin.android is an error.
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
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

ksp {
    // Schemas are committed, so a migration diff is reviewable (AGENTS.md, hard rule 8).
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    // Exposed to :app because the permission contract is part of this module's surface.
    api(libs.androidx.health.connect.client)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
}
