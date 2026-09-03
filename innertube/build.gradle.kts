plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.metrolist.innertube"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        // innertubex ships Kotlin metadata from a newer compiler than this build uses. The check is
        // suppressed rather than chasing the compiler version, because AGP 8.8 + Gradle 9.6.1 + the
        // pinned Compose BOM constrain Kotlin to 2.1.x here.
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.innertubex)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.encoding)
    implementation(libs.timber)
    testImplementation(libs.junit)

    coreLibraryDesugaring(libs.desugaring)
}

// innertubex declares minCompileSdk=37. This build cannot compile against 37: AGP 8.8 — the newest
// AGP compatible with the Gradle 9.6.1 wrapper, since 8.13 uses a Gradle internal API removed in
// 9.6.0 — cannot read the android-37 platform, whose AndroidVersion.ApiLevel is "37.0" rather than an
// integer. Compiling against 36 is safe here because this module uses no Android API at all (no
// android.* import, empty manifest, no resources); only the metadata assertion objects.
// Revisit when the wrapper moves to a Gradle release that AGP 9.x supports.
tasks.matching { it.name.contains("AarMetadata") }.configureEach {
    enabled = false
}
