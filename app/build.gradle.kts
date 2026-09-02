import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.kaon.music"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kaon.music"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val envPath = System.getenv("KEYSTORE_PATH")
            val propFile = rootProject.file("release-keystore.properties")

            if (envPath != null) {
                storeFile = file(envPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                    ?: throw GradleException("KEYSTORE_PASSWORD environment variable not set.")
                keyAlias = System.getenv("KEY_ALIAS")
                    ?: throw GradleException("KEY_ALIAS environment variable not set.")
                keyPassword = System.getenv("KEY_PASSWORD")
                    ?: throw GradleException("KEY_PASSWORD environment variable not set.")
            } else if (propFile.exists()) {
                val props = Properties()
                propFile.inputStream().use { props.load(it) }
                val path = props.getProperty("storeFile")
                    ?: throw GradleException("storeFile missing in release-keystore.properties")
                storeFile = rootProject.file(path)
                storePassword = props.getProperty("storePassword")
                    ?: throw GradleException("storePassword missing in release-keystore.properties")
                keyAlias = props.getProperty("keyAlias")
                    ?: throw GradleException("keyAlias missing in release-keystore.properties")
                keyPassword = props.getProperty("keyPassword")
                    ?: throw GradleException("keyPassword missing in release-keystore.properties")
            }
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null && releaseSigning.storeFile!!.exists()) {
                signingConfig = releaseSigning
            } else {
                tasks.matching { it.name.contains("Release") && (it.name.startsWith("assemble") || it.name.startsWith("bundle")) }.configureEach {
                    doFirst {
                        throw GradleException("Release signing configuration is missing. Set KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD environment variables or provide release-keystore.properties.")
                    }
                }
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-Xskip-metadata-version-check",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = true
        disable += listOf("UnsafeOptInUsageError")
    }
}

dependencies {
    implementation(project(":innertube"))
    coreLibraryDesugaring(libs.desugaring)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.5.0+1")

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coil 3
    implementation(libs.coil.compose)
    implementation(libs.coil.network)

    // DataStore & Coroutines
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Logging
    implementation(libs.timber)

    // MusicMeta metadata enrichment engine
    implementation("io.github.famesjranko:musicmeta-core:0.9.2")

    // Profile Installer for Baseline Profiles
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("androidx.test:core:1.6.1")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.matching { it.name.startsWith("check") && it.name.endsWith("AarMetadata") }.configureEach {
    enabled = false
}

// Unit tests for the streaming/internetworking layer load classes from the `innertubex`
// dependency, which ships Java 21+ bytecode, while the build is pinned to JDK 17
// (org.gradle.java.home). Only the test JVMs therefore run on a newer toolchain
// (first of 21/25 found); compilation and the Gradle daemon stay on JDK 17.
val testToolchainLauncher = listOf(21, 25).firstNotNullOfOrNull { version ->
    try {
        javaToolchains.launcherFor {
            languageVersion.set(org.gradle.jvm.toolchain.JavaLanguageVersion.of(version))
        }.takeIf { it.isPresent }
    } catch (_: Exception) {
        null
    }
}

if (testToolchainLauncher != null) {
    tasks.withType(org.gradle.api.tasks.testing.Test::class.java).configureEach {
        javaLauncher.set(testToolchainLauncher)
    }
}
