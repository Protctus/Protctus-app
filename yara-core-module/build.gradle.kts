plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.protctus.yaracore"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        targetSdk = 34
    }

    // NOTE: The bundled yara-src/libyara/ native sources are absent from this
    // repository, so the CMake/native build cannot compile. The module is kept
    // as a pure Kotlin/Java library; YaraScanner degrades gracefully (returns
    // empty results + log warning) when the native library is unavailable.
    // externalNativeBuild is intentionally NOT configured here.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
