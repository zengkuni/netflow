plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "netflow.dev"
    compileSdk = 36

    defaultConfig {
        applicationId = "netflow.dev"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.3"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // The keystore is gitignored — generate your own with the keytool command
    // in README.md, or download a pre-signed APK from the GitHub Releases page.
    // The file name follows the new brand, but the entry inside it still
    // carries the original alias and password: regenerating the keystore
    // would mint a different signing key, which breaks in-place upgrades
    // for every install made so far. Do NOT "fix" the alias to netflow.
    // Override via NETFLOW_KEYSTORE_PASSWORD / NETFLOW_KEY_ALIAS /
    // NETFLOW_KEY_PASSWORD if you supply your own keystore.
    val keystoreFile = file("keystore/netflow-release.jks")
    signingConfigs {
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = keystoreFile
                storePassword = System.getenv("NETFLOW_KEYSTORE_PASSWORD") ?: "dataproxy"
                keyAlias = System.getenv("NETFLOW_KEY_ALIAS") ?: "dataproxy"
                keyPassword = System.getenv("NETFLOW_KEY_PASSWORD") ?: "dataproxy"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // AntiKillEnforcer reads the package name off BuildConfig (compile-time
        // constant, safe to interpolate into static shell commands).
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    // Optional root features (Anti-Kill enforcement, watchdog daemon).
    // The app runs fully without root; this is only pulled in for the
    // opt-in root path, and libsu's shell is never started unless the user
    // enables it, so no superuser prompt appears on a fresh install.
    implementation(libs.libsu.core)

    debugImplementation(libs.androidx.ui.tooling)
}
