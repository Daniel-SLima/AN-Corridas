plugins {
    id("com.android.application")
}

val signingReady = listOf(
    "ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD"
).all { !System.getenv(it).isNullOrBlank() }

android {
    namespace = "br.com.meutaxi.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "br.com.meutaxi.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"
    }

    signingConfigs {
        if (signingReady) {
            create("production") {
                storeFile = file("production-keystore.jks")
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (signingReady) signingConfig = signingConfigs.getByName("production")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
