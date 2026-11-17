import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    jacoco
}

// Pure Kotlin, no Android. Consumed by :data and :app later; keeping the JVM target at 17
// keeps the artefact usable by Android toolchains without desugaring surprises.
kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// The only module whose coverage SonarQube reads: it holds the maths, and its tests are plain JUnit.
// :data and :app would need AGP's unit-test instrumentation and a report task per variant.
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
    }
}
