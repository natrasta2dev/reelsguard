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
    }

    // Deux éditions à partir du même code :
    //  - perso   : ReelsGuard, pour se limiter soi-même (mode strict avec attente) ;
    //  - famille : ReelsGuard Famille, contrôle parental protégé par un code parent.
    // Version : MAJEUR.MINEUR.CORRECTIF (une hausse du MAJEUR rend la mise à jour obligatoire).
    flavorDimensions += "edition"
    productFlavors {
        create("perso") {
            dimension = "edition"
            versionCode = 4
            versionName = "1.1.1"
            resValue("string", "app_name", "ReelsGuard")
            buildConfigField("boolean", "FAMILLE", "false")
            buildConfigField("String", "RELEASE_TAG_PREFIX", "\"v\"")
        }
        create("famille") {
            dimension = "edition"
            applicationIdSuffix = ".famille"
            versionCode = 1
            versionName = "1.0.0"
            resValue("string", "app_name", "ReelsGuard Famille")
            buildConfigField("boolean", "FAMILLE", "true")
            buildConfigField("String", "RELEASE_TAG_PREFIX", "\"famille-v\"")
        }
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
