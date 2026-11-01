import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    id("kotlin-parcelize")
}

android {
    namespace = "com.protctus.app"
    compileSdk = 35

    // Load .env or local.properties ONCE at android block scope (accessible everywhere)
    val properties = Properties()
    val envFile = rootProject.file(".env")
    if (envFile.exists()) {
        properties.load(envFile.inputStream())
    } else {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            properties.load(localPropertiesFile.inputStream())
        }
    }

    defaultConfig {
        applicationId = "com.protctus.app"
        minSdk = 27
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // API keys are sourced EXCLUSIVELY from .env — never hardcode real values here
        val otpApiKeyRaw = properties.getProperty("OTP_API_KEY", "")
        val otpApiKey = if (otpApiKeyRaw.startsWith("\"") && otpApiKeyRaw.endsWith("\"")) {
            otpApiKeyRaw
        } else {
            "\"$otpApiKeyRaw\""
        }
        buildConfigField("String", "OTP_API_KEY", otpApiKey)

        val darkWebApiKeyRaw = properties.getProperty("DARK_WEB_API_KEY", "")
        val darkWebApiKey = if (darkWebApiKeyRaw.startsWith("\"") && darkWebApiKeyRaw.endsWith("\"")) {
            darkWebApiKeyRaw
        } else {
            "\"$darkWebApiKeyRaw\""
        }
        buildConfigField("String", "DARK_WEB_API_KEY", darkWebApiKey)

        val darkWebApiUrlRaw = properties.getProperty("DARK_WEB_API_URL", "https://dark-web-scanner-api.jatinsharmashivpuri02122008.workers.dev/scan")
        buildConfigField("String", "DARK_WEB_API_URL", "\"$darkWebApiUrlRaw\"")

        val phishingApiKeyRaw = properties.getProperty("PHISHING_API_KEY", "")
        val phishingApiKey = if (phishingApiKeyRaw.startsWith("\"") && phishingApiKeyRaw.endsWith("\"")) {
            phishingApiKeyRaw
        } else {
            "\"$phishingApiKeyRaw\""
        }
        buildConfigField("String", "PHISHING_API_KEY", phishingApiKey)

        val razorpayKeyIdRaw = properties.getProperty("RAZORPAY_KEY_ID", "")
        val razorpayKeyId = if (razorpayKeyIdRaw.startsWith("\"") && razorpayKeyIdRaw.endsWith("\"")) {
            razorpayKeyIdRaw
        } else {
            "\"$razorpayKeyIdRaw\""
        }
        buildConfigField("String", "RAZORPAY_KEY_ID", razorpayKeyId)
    }

    signingConfigs {
        create("release") {
            // Read from .env or local.properties — NEVER commit these values
            val ksPath = properties.getProperty("RELEASE_KEYSTORE_PATH", "")
            val ksPass = properties.getProperty("RELEASE_KEYSTORE_PASSWORD", "")
            val keyAlias = properties.getProperty("RELEASE_KEY_ALIAS", "")
            val keyPass = properties.getProperty("RELEASE_KEY_PASSWORD", "")

            if (ksPath.isNotBlank() && File(ksPath).exists()) {
                storeFile = File(ksPath)
                storePassword = ksPass
                this.keyAlias = keyAlias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use release keystore if configured, fallback to debug for local dev only
            val releaseConfig = signingConfigs.findByName("release")
            val hasReleaseKeystore = releaseConfig?.storeFile?.exists() == true
            signingConfig = if (hasReleaseKeystore) releaseConfig else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    androidComponents {
        onVariants { variant ->
            // Use standard output file naming, or custom file renaming mapping.
            // Modern AGP output artifact modification
            variant.outputs.forEach { output ->
                // Example: safe APK renaming
            }
        }
    }

    androidResources {
        noCompress.add("tflite")
        noCompress.add("onnx")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.biometric)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.database.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended:1.6.3")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.okhttp3)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation(libs.razorpay.checkout)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.androidx.webkit)
    ksp(libs.room.compiler)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    
    // ONNX Runtime Mobile for NeuroShield phishing inference
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.0")
    
    // Required by OfflinePhishingModel.kt for the legacy TFLite inference path.
    implementation("org.tensorflow:tensorflow-lite:2.16.1")


    // TOTP 2FA: QR code generation + HMAC
    implementation("com.google.zxing:core:3.5.3")
    
    // Core missing dependencies for compiling Android libraries and Utilities
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    // Google Play Integrity API (Anti-Tamper / Anti-Modding) removed 
    // implementation("com.google.android.play:integrity:1.4.0")

    // Test dependencies
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test:core:1.5.0")
    
    implementation(project(":yara-core-module"))
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}
