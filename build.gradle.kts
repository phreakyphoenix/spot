// Only declare plugin versions here, do not apply them directly
plugins {
    kotlin("android") version "1.9.10" apply false
    id("com.android.library") version "8.13.0" apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.13.0")
        classpath(kotlin("gradle-plugin", version = "1.9.10"))
    }
}
