plugins {
    // AGP 9 has Kotlin support built in: applying org.jetbrains.kotlin.android is an error.
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    jacoco
}

android {
    namespace = "com.balancecalories.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Instruments the JVM unit tests so JaCoCo can write testDebugUnitTest.exec; the report that
        // SonarQube reads is jacocoDebugUnitTestReport, registered below.
        getByName("debug") {
            enableUnitTestCoverage = true
        }
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

// SonarQube reads this XML. AGP itself only writes the .exec file, so the report is a plain JacocoReport.
androidComponents {
    onVariants(selector().withBuildType("debug")) { _ ->
        tasks.register<JacocoReport>("jacocoDebugUnitTestReport") {
            dependsOn("testDebugUnitTest")
            executionData(
                layout.buildDirectory.file("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"),
            )
            // shortcut: this is AGP's built-in-Kotlin output directory, not a public API; move it when
            // AGP renames the intermediates.
            classDirectories.setFrom(
                fileTree(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")) {
                    exclude("**/R.class", "**/R\$*.class", "**/BuildConfig*", "**/*_Impl*")
                },
            )
            sourceDirectories.setFrom(files("src/main/kotlin"))
            reports {
                xml.required.set(true)
                html.required.set(false)
            }
        }
    }
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

    // JVM unit tests for the OpenFoodFacts client and parser: they exercise JSON parsing and the
    // retry/404 state machine, none of which needs a device. Android's org.json is a throwing stub on
    // the JVM, so the real implementation is a test-only dependency.
    testImplementation(libs.org.json)
    testImplementation(libs.junit4)
}
