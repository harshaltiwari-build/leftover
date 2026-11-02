import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// The release key lives outside the project (~/.leftover-signing) and is never committed. A project-level
// keystore.properties (also git-ignored) wins if present, so CI or another machine can supply its own.
val signingProps = Properties().apply {
    listOf(rootProject.file("keystore.properties"), File(System.getProperty("user.home"), ".leftover-signing/keystore.properties"))
        .firstOrNull { it.isFile }?.inputStream()?.use { load(it) }
}

android {
    namespace = "app.leftover"
    compileSdk = 37

    defaultConfig {
        // Change "yourorg" to the GitHub organisation that owns the repository before the first Play release.
        applicationId = "io.github.yourorg.leftover"
        minSdk = 26
        targetSdk = 37
        versionCode = 13
        versionName = "0.9.1"
        // Text recognition is native code. Phones are ARM; leaving out x86 keeps about 16 MB out of the APK.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Website screenshot tools run only on request (am instrument ... -e screenshots true), never in test runs.
        testInstrumentationRunnerArguments["notAnnotation"] = "app.leftover.e2e.ScreenshotsOnly"
    }

    // On-phone tests run against "qa": a debug build under its own app id, so they never touch the real app's data.
    testBuildType = "qa"

    signingConfigs {
        if (signingProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        create("qa") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".qa"
            versionNameSuffix = "-qa"
            matchingFallbacks += listOf("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without the key (a fresh clone) the release build is left unsigned rather than silently debug-signed.
            signingConfigs.findByName("release")?.let { signingConfig = it }
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

    testOptions {
        // Robolectric needs the app's resources and manifest to run screens and the database on the JVM.
        unitTests.isIncludeAndroidResources = true
        // Robolectric reaches into JDK internals for file descriptors; Java 17+ must be told to allow it.
        unitTests.all {
            // Each test class gets a fresh JVM, so app-wide singletons (settings store, WorkManager) never leak between journeys.
            it.forkEvery = 1
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
        }
    }

    sourceSets {
        // Journey tests live in one place and run twice: on the JVM with Robolectric, and on a phone.
        getByName("test") { kotlin.directories += "src/sharedTest/java" }
        // Database upgrade tests read the exported schemas as assets; only the test-only qa build carries them.
        getByName("qa") { assets.srcDir("$projectDir/schemas") }
        getByName("androidTest") {
            kotlin.directories += "src/sharedTest/java"
            assets.srcDir("$projectDir/schemas")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.tesseract4android)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.biometric)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    "qaImplementation"(platform(libs.androidx.compose.bom))
    // Declares an exported empty activity the UI tests need. Test-only qa build only: never in what ships.
    "qaImplementation"(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
