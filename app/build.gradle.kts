plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.perso.reelsguard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.perso.reelsguard"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    // Clé fixe : toutes les compilations ont la même signature,
    // donc chaque nouvel APK s'installe par-dessus l'ancien.
    signingConfigs {
        getByName("debug") {
            storeFile = file("reelsguard.jks")
            storePassword = "reelsguard-perso"
            keyAlias = "reelsguard"
            keyPassword = "reelsguard-perso"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
