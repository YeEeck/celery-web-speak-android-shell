plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val versionNameOverride = System.getenv("CWS_VERSION_NAME")
    ?.removePrefix("v")
    ?.takeIf { it.isNotBlank() }
val versionCodeOverride = System.getenv("CWS_VERSION_CODE")?.let { value ->
    requireNotNull(value.toIntOrNull()?.takeIf { it > 0 }) {
        "CWS_VERSION_CODE must be a positive integer"
    }
}

val releaseKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
val hasReleaseSigningConfig = !releaseKeystorePath.isNullOrBlank()

android {
    namespace = "com.yeck.celerywebspeak.android.shell"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yeck.celerywebspeak.android.shell"
        minSdk = 26
        targetSdk = 35
        versionCode = versionCodeOverride ?: 3
        versionName = versionNameOverride ?: "1.0.2"
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = requireNotNull(System.getenv("ANDROID_KEYSTORE_PASSWORD")) {
                    "ANDROID_KEYSTORE_PASSWORD is required for release signing"
                }
                keyAlias = requireNotNull(System.getenv("ANDROID_KEY_ALIAS")) {
                    "ANDROID_KEY_ALIAS is required for release signing"
                }
                keyPassword = requireNotNull(System.getenv("ANDROID_KEY_PASSWORD")) {
                    "ANDROID_KEY_PASSWORD is required for release signing"
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
