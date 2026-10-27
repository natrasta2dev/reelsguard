plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "fr.codapps.reelsguard"
    compileSdk = 34

    defaultConfig {
        applicationId = "fr.codapps.reelsguard"
        minSdk = 26
        targetSdk = 34
        // Version : MAJEUR.MINEUR.CORRECTIF (une hausse du MAJEUR rend la mise à jour obligatoire).
        // Le tag Git de la release doit être identique, préfixé d'un « v » (ex. v1.0.0).
        versionCode = 1
        versionName = "1.0.0"
    }

    // Signature des releases : la clé n'est jamais dans le dépôt.
    // En CI elle vient des secrets GitHub ; en local, des variables d'environnement.
    signingConfigs {
        create("release") {
            val path = System.getenv("RG_KEYSTORE_PATH")
            if (path != null) {
                storeFile = file(path)
                storePassword = System.getenv("RG_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RG_KEY_ALIAS") ?: "reelsguard"
                keyPassword = System.getenv("RG_KEY_PASSWORD") ?: System.getenv("RG_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (System.getenv("RG_KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
