plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.khahdihdz.fbresume"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.khahdihdz.fbresume"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        create("ciRelease") {
            val keystorePath = project.findProperty("FBRESUME_KEYSTORE_FILE") as String?
            val keystorePassword = project.findProperty("FBRESUME_KEYSTORE_PASSWORD") as String?
            val keyAliasValue = project.findProperty("FBRESUME_KEY_ALIAS") as String?
            val keyPasswordValue = project.findProperty("FBRESUME_KEY_PASSWORD") as String?
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("ciRelease")
        }
        debug {
            // CI signs the distributable debug APK with the same persistent key.
            signingConfig = signingConfigs.getByName("ciRelease")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core:1.15.0")
}
