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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        // Exported schemas become test assets so MigrationTestHelper can replay old versions.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
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

    // Room's schema and migration test helpers run on a device: they need a real SQLite, and the
    // Redmi/Honor pair is what the app actually ships to.
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
