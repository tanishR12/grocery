plugins {
    id("com.android.application")
}

fun config(name: String, fallback: String = ""): String =
    providers.gradleProperty(name).orElse(fallback).get()

android {
    namespace = "in.zyplo.vendor"
    compileSdk = 36

    defaultConfig {
        applicationId = "in.zyplo.vendor"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "BASE_URL", "\"${config("ZYPLO_BASE_URL", "https://zyplo.in/grocery-vendor-login")}\"")
        buildConfigField("String", "ALLOWED_HOST", "\"${config("ZYPLO_ALLOWED_HOST", "zyplo.in")}\"")
        buildConfigField("String", "LOCATION_ENDPOINT", "\"${config("ZYPLO_LOCATION_ENDPOINT", "https://zyplo.in/api/vendor/location")}\"")
        buildConfigField("String", "DEVICE_TOKEN_ENDPOINT", "\"${config("ZYPLO_DEVICE_TOKEN_ENDPOINT", "https://zyplo.in/api/vendor/device-token")}\"")
        buildConfigField("String", "FIREBASE_APPLICATION_ID", "\"${config("FIREBASE_APPLICATION_ID")}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${config("FIREBASE_API_KEY")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${config("FIREBASE_PROJECT_ID")}\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"${config("FIREBASE_SENDER_ID")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
}

dependencies {
    implementation("androidx.activity:activity:1.13.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.core:core:1.19.0")
    implementation("com.google.android.gms:play-services-location:21.4.0")
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-messaging")
}
