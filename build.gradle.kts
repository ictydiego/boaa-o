plugins {
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.25" apply false
    id("com.google.devtools.ksp") version "1.9.25-1.0.20" apply false
    alias(libs.plugins.google.firebase.appdistribution) apply false
}

buildscript {
    dependencies {
        classpath("com.google.gms:google-services:4.4.2") // Google Services plugin
    }
}