// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://chaquo.com/maven") }
    }
    dependencies {
        // Updated to latest version for better Python 3.11+ support
        classpath("com.chaquo.python:gradle:16.1.0")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
    alias(libs.plugins.dagger.hilt.android) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false // not found on any of the following sources issue
}

// Custom clean task to remove Python cache
tasks.register("cleanPython") {
    group = "build"
    description = "Cleans Python build cache to force Chaquopy to rebuild with correct Python version"
    doLast {
        val appPythonDir = file("app/build/python")
        if (appPythonDir.exists()) {
            appPythonDir.deleteRecursively()
            println("✓ Cleaned Python cache at app/build/python")
        } else {
            println("ℹ Python cache doesn't exist yet")
        }
    }
}

// Hook into the clean task when it's created
gradle.projectsEvaluated {
    tasks.findByName("clean")?.dependsOn("cleanPython")
}