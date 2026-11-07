   import java.util.Base64
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.passenger.sms"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.passenger.sms"
        minSdk = 24
        targetSdk = 34
        versionCode = 7
        versionName = "2.2"
    }

    // Private store signing key: provided by GitHub Actions secrets (never stored in the repo).
    val storeKeyB64 = System.getenv("KEYSTORE_B64") ?: ""
    val hasStoreKey = storeKeyB64.isNotBlank() &&
        !System.getenv("KEYSTORE_PASSWORD").isNullOrBlank() &&
        !System.getenv("KEY_ALIAS").isNullOrBlank() &&
        !System.getenv("KEY_PASSWORD").isNullOrBlank()

    signingConfigs {
        // Old sideload key (used for debug builds and as a fallback when no secrets are set)
        create("legacy") {
            storeFile = file("passenger.p12")
            storePassword = "passenger123"
            keyAlias = "passenger"
            keyPassword = "passenger123"
            storeType = "pkcs12"
        }
        if (hasStoreKey) {
            create("store") {
                val f = File(rootDir, ".gradle/psms-store.p12")
                f.parentFile.mkdirs()
                f.writeBytes(Base64.getMimeDecoder().decode(storeKeyB64))
                storeFile = f
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
                storeType = "pkcs12"
            }
        }
    }

    flavorDimensions += "dist"
    productFlavors {
        // Cafe Bazaar edition: no external license codes, all features available
        create("bazaar") {
            dimension = "dist"
            buildConfigField("boolean", "STORE_BUILD", "true")
        }
        // Direct (sideload) edition: PSMS Pro unlocked with activation codes
        create("direct") {
            dimension = "dist"
            buildConfigField("boolean", "STORE_BUILD", "false")
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("legacy")
            isDebuggable = false
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (hasStoreKey) "store" else "legacy")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.fragment:fragment-ktx:1.7.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
