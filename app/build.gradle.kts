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
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // Inject secrets from local.properties
        
        buildConfigField(
            "String",
            "SPOTIFY_CLIENT_ID",
            "\"${if (localProps.getProperty("SPOTIFY_CLIENT_ID") != null) localProps.getProperty("SPOTIFY_CLIENT_ID") else if (project.hasProperty("SPOTIFY_CLIENT_ID")) project.property("SPOTIFY_CLIENT_ID") else ""}\""
        )
        buildConfigField(
            "String",
            "SPOTIFY_REDIRECT_URI",
            "\"${if (localProps.getProperty("SPOTIFY_REDIRECT_URI") != null) localProps.getProperty("SPOTIFY_REDIRECT_URI") else if (project.hasProperty("SPOTIFY_REDIRECT_URI")) project.property("SPOTIFY_REDIRECT_URI") else ""}\""
        )
        buildConfigField(
            "String",
            "PICOVOICE_ACCESS_KEY",
            "\"${if (localProps.getProperty("PICOVOICE_ACCESS_KEY") != null) localProps.getProperty("PICOVOICE_ACCESS_KEY") else if (project.hasProperty("PICOVOICE_ACCESS_KEY")) project.property("PICOVOICE_ACCESS_KEY") else ""}\""
        )
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
    implementation("com.alphacephei:vosk-android:0.3.70")
}
