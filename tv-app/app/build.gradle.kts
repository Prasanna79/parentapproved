plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "tv.parentapproved.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "tv.parentapproved.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 11
        versionName = "0.8.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "RELAY_URL", "\"https://relay.parentapproved.tv\"")
        buildConfigField("int", "PROTOCOL_VERSION", "1")
    }

    buildTypes {
        debug {
            buildConfigField("Boolean", "IS_DEBUG", "true")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("Boolean", "IS_DEBUG", "false")
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
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
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
    implementation("androidx.compose.material:material-icons-extended")
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

    // Desugaring (needed by NewPipeExtractor)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.4")

    // NewPipe Extractor
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.25.2")

    // Media3 ExoPlayer
    val media3Version = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")

    // Ktor server
    val ktorVersion = "2.3.7"
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-sessions:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // QR code
    implementation("com.google.zxing:core:3.5.2")

    // Room (cache + play events)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Coil (image loading)
    implementation("io.coil-kt:coil-compose:2.7.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Unit test dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("androidx.room:room-testing:$roomVersion")

    // Instrumented test dependencies
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("com.squareup.okhttp3:okhttp:4.12.0")
}
