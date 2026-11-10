// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

// Pure Kotlin/JVM: API client, models and supernova detection, testable without Android.
plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.okhttp)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.test {
    // optional: real filter lists for FilterSetTest.realLists (-Pplanetfoss.lists=<dir>)
    providers.gradleProperty("planetfoss.lists").orNull?.let { systemProperty("planetfoss.lists", it) }
    testLogging { showStandardStreams = true }
}
