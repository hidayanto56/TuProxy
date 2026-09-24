plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tustudio.tuproxy"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tustudio.tuproxy"
        minSdk = 28
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
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
    // Upload-key signing via env vars (never commit secrets).
    // TUPROXY_STORE_FILE / TUPROXY_STORE_PASSWORD / TUPROXY_KEY_ALIAS / TUPROXY_KEY_PASSWORD
    val storeFileEnv = System.getenv("TUPROXY_STORE_FILE")
    if (storeFileEnv != null) {
        signingConfigs {
            create("play") {
                storeFile = file(storeFileEnv)
                storePassword = System.getenv("TUPROXY_STORE_PASSWORD")
                keyAlias = System.getenv("TUPROXY_KEY_ALIAS")
                keyPassword = System.getenv("TUPROXY_KEY_PASSWORD")
            }
        }
        buildTypes {
            named("release") {
                signingConfig = signingConfigs.getByName("play")
            }
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
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.android.gms:play-services-ads:23.2.0")
    implementation("com.google.android.play:review-ktx:2.0.1")
    implementation("com.android.billingclient:billing-ktx:8.0.0")
}
