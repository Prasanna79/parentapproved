plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.kidswatch.feasibility"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kidswatch.feasibility"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
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
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")

    // TV-specific
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.leanback:leanback:1.0.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Network
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // WebView
    implementation("androidx.webkit:webkit:1.12.1")

    // Chrome Custom Tabs (for sign-in that bypasses WebView detection)
    implementation("androidx.browser:browser:1.7.0")

    // Desugaring (needed by NewPipeExtractor)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.4")

    // NewPipe Extractor (stream URL extraction without YouTube API)
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.25.2")

    // Media3 ExoPlayer (native video playback)
    val media3Version = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
