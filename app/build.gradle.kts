plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ayush.slate"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ayush.slate"
        minSdk = 22          // Fire OS 5 and newer
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
    }

    // A fixed key so every new build installs over the old one (keeps your favourites).
    signingConfigs {
        create("slate") {
            storeFile = file("slate.jks")
            storePassword = System.getenv("SLATE_KS_PASS") ?: "slate-launcher"
            keyAlias = "slate"
            keyPassword = System.getenv("SLATE_KS_PASS") ?: "slate-launcher"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("slate")
        }
        debug { signingConfig = signingConfigs.getByName("slate") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}
// No dependencies on purpose: no ad SDKs, no analytics, nothing to phone home.
