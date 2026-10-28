plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.koala.cartes"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.koala.cartes"
        minSdk = 26
        targetSdk = 35
        versionCode = 15
        versionName = "1.7.1"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        create("release") {
            storeFile = file("koalacartes.jks")
            storePassword = "koalacartes"
            keyAlias = "koalacartes"
            keyPassword = "koalacartes"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    testOptions { unitTests.isIncludeAndroidResources = true }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    val camerax = "1.4.1"
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // Lecture des codes-barres et du texte : modules Google déjà présents sur le téléphone
    // (téléchargés une fois, puis fonctionnent sans connexion) au lieu de 26 Mo embarqués dans l'appli
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")

    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}
