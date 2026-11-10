// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

/**
 * Signing data comes only from Gradle properties outside the repository
 * (~/.gradle/gradle.properties). Without them the release build is unsigned
 * instead of failing – F-Droid signs its own builds.
 */
val signingProperties = listOf(
    "PLANETFOSS_STORE_FILE",
    "PLANETFOSS_STORE_PASSWORD",
    "PLANETFOSS_KEY_ALIAS",
    "PLANETFOSS_KEY_PASSWORD",
).associateWith { providers.gradleProperty(it).orNull }

val canSignRelease = signingProperties.values.none { it.isNullOrBlank() }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.hilt)
}

/** Version name and build number from Git, as in Hausmusik. */
fun git(vararg args: String): String? = runCatching {
    val execution = providers.exec {
        commandLine(listOf("git") + args)
        workingDir = rootDir
        isIgnoreExitValue = true
    }
    if (execution.result.get().exitValue != 0) return@runCatching null
    execution.standardOutput.asText.get().trim().ifEmpty { null }
}.getOrNull()

val baseVersion: String = providers.gradleProperty("PLANETFOSS_VERSION").orNull ?: "0.3.0"
val gitCommitCount: Int = git("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1
val gitShortSha: String = git("rev-parse", "--short=8", "HEAD") ?: "unknown"
val gitIsDirty: Boolean = git("status", "--porcelain") != null
val appVersionName: String = git("describe", "--tags", "--exact-match", "HEAD")?.removePrefix("v")
    ?: ("$baseVersion-dev+$gitCommitCount.g$gitShortSha" + if (gitIsDirty) ".dirty" else "")

/**
 * Where the app API lives. Release: planetfoss.net. Debug builds can point at the
 * test environment or a local fixture server: -PPLANETFOSS_DEBUG_URL=http://10.0.2.2:8099/
 */
val releaseBaseUrl = "https://planetfoss.net/"
val debugBaseUrl: String = providers.gradleProperty("PLANETFOSS_DEBUG_URL").orNull ?: releaseBaseUrl

android {
    namespace = "net.planetfoss.app"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = file(signingProperties.getValue("PLANETFOSS_STORE_FILE")!!)
                storePassword = signingProperties.getValue("PLANETFOSS_STORE_PASSWORD")
                keyAlias = signingProperties.getValue("PLANETFOSS_KEY_ALIAS")
                keyPassword = signingProperties.getValue("PLANETFOSS_KEY_PASSWORD")
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    defaultConfig {
        applicationId = "net.planetfoss.app"
        // Android 14+: the versions that still get security updates from Google (bulletin 2026-10)
        minSdk = 34
        targetSdk = 37
        versionCode = gitCommitCount
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("String", "API_BASE_URL", "\"$debugBaseUrl\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
            buildConfigField("String", "API_BASE_URL", "\"$releaseBaseUrl\"")
        }
        // QA: the release build (R8, shrunk resources) signed with the debug key and pointing at the
        // fixture server, to catch minification problems before a real release. Never published.
        create("qa") {
            initWith(getByName("release"))
            applicationIdSuffix = ".qa"
            versionNameSuffix = "-qa"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            buildConfigField("String", "API_BASE_URL", "\"$debugBaseUrl\"")
        }
        // Baseline profile: the release build without R8, so the recorded rules name the real classes
        // (R8 rewrites them for release). Profileable from the shell, signed with the debug key. Never published.
        create("benchmark") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            applicationIdSuffix = ".benchmark"
            versionNameSuffix = "-benchmark"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        generateLocaleConfig = true
    }

    // the app language can be switched in the app: every bundle install must carry all languages
    bundle {
        language { enableSplit = false }
    }

    // Room schemas for MigrationTestHelper: Robolectric reads the debug assets (not shipped in release)
    sourceSets.getByName("debug").assets.directories.add("$projectDir/schemas")

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE.md", "/META-INF/LICENSE-notice.md")
        }
    }

    // F-Droid and reproducible builds: no Google-encrypted dependency metadata in the APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = true
        checkDependencies = false
        // Android 14+ always uses the adaptive icon; the raster mipmaps beside it are what F-Droid
        // extracts as the app icon (there is no fastlane icon), so they stay
        disable += "IconXmlAndPng"
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.navigation.suite)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.work.runtime.ktx)
    implementation(libs.glance.appwidget)
    implementation(libs.browser)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // installs src/main/baseline-prof.txt on F-Droid and sideloaded installs, where no store compiles it
    implementation(libs.profileinstaller)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)
    testImplementation(libs.work.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
