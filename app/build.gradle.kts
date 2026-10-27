plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.deals"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.deals"
        minSdk = 28
        targetSdk = 34
        versionCode = 10
        versionName = "1.1"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("deals.jks")
            storePassword = "deals123"
            keyAlias = "deals"
            keyPassword = "deals123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
