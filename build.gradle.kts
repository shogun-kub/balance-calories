// Root build file. Plugin versions live in gradle/libs.versions.toml and are applied per module.
// AGP 9 supplies Kotlin itself, so neither kotlin-android nor the Compose compiler plugin appears
// here: only pure-JVM :core still needs the explicit Kotlin JVM plugin.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.sonarqube)
}

// Analysis runs against the household's own SonarQube Server on the LAN. The token is the owner's and
// lives in the git-ignored sonar.token beside keystore.properties; when the file is empty or absent the
// scan simply asks for a token instead of failing the build.
val sonarToken = rootProject.file("sonar.token").takeIf { it.isFile }?.readText()?.trim().orEmpty()

// Coverage comes from the two modules whose tests run on the JVM: :core's formulas and :data's parsers.
// Absolute paths because submodules inherit the property and would resolve it against their own directory.
// :app is Compose UI, measured by device tests, so it contributes nothing here and is left out.
val coverageReports = listOf(
    "core/build/reports/jacoco/test/jacocoTestReport.xml",
    "data/build/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml",
).map { layout.projectDirectory.file(it).asFile.absolutePath }

sonar {
    properties {
        property("sonar.projectKey", "com.balancecalories")
        property("sonar.projectName", "Balance Calories")
        property("sonar.host.url", "http://alpine-box.lan:9000")
        property("sonar.sourceEncoding", "UTF-8")
        property("sonar.coverage.jacoco.xmlReportPaths", coverageReports.joinToString(","))
        if (sonarToken.isNotEmpty()) {
            property("sonar.token", sonarToken)
        }
    }
}

tasks.named("sonar") {
    dependsOn(":core:jacocoTestReport", ":data:jacocoDebugUnitTestReport")
}
