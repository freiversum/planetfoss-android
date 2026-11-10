// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

/**
 * Records the baseline profile and measures the cold start on a device (docs/08-release.md).
 * Runs against the app's `benchmark` build type: release without R8, debug-signed.
 */
plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "net.planetfoss.baselineprofile"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        // Android 13+ records profiles without root
        minSdk = 34
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}

// only the benchmark variant makes sense: it targets the app's benchmark build
androidComponents {
    beforeVariants { it.enable = it.buildType == "benchmark" }
}
