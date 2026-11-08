// Root build file. Plugin versions live in gradle/libs.versions.toml and are applied per module.
// AGP 9 supplies Kotlin itself, so neither kotlin-android nor the Compose compiler plugin appears
// here: only pure-JVM :core still needs the explicit Kotlin JVM plugin.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}
