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

sonar {
    properties {
        property("sonar.projectKey", "com.balancecalories")
        property("sonar.projectName", "Balance Calories")
        property("sonar.host.url", "http://alpine-box.lan:9000")
        property("sonar.sourceEncoding", "UTF-8")
        if (sonarToken.isNotEmpty()) {
            property("sonar.token", sonarToken)
        }
    }
}
