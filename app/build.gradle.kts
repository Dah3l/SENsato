// Módulo único de la aplicación: Apagones Habana
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.apagones.habana"
    compileSdk = 34
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.apagones.habana"
        minSdk = 26           // Android 8.0 Oreo en adelante
        targetSdk = 34        // Android 14
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    // --- Jetpack Compose (Material 3) ---
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // --- Activity y ciclo de vida Compose ---
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    // --- WorkManager (trabajo en segundo plano cada 15 min) ---
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // --- DataStore de Preferencias (circuitos y último id visto) ---
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // --- Jsoup (scraping del canal público de Telegram) ---
    implementation("org.jsoup:jsoup:1.17.2")

    // --- Coroutines ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // --- Básicos de AndroidX ---
    implementation("androidx.core:core-ktx:1.13.1")
}
