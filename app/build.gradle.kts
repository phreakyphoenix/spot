import java.util.Properties

// Load local.properties
val localProps = Properties()
val localFile = rootProject.file("local.properties")
if (localFile.exists()) {
    localProps.load(localFile.inputStream())
}

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.example.spot"
    compileSdk = 36
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.spot"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // Inject secrets from local.properties
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"${localProps["SPOTIFY_CLIENT_ID"]}\"")
        buildConfigField("String", "SPOTIFY_REDIRECT_URI", "\"${localProps["SPOTIFY_REDIRECT_URI"]}\"")
        buildConfigField("String", "ACCESS_KEY", "\"${localProps["ACCESS_KEY"]}\"")
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))
    implementation("com.google.code.gson:gson:2.6.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("ai.picovoice:porcupine-android:3.0.3")
}
