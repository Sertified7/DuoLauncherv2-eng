plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Pli is signed in CI with the key described by these variables (see .github/workflows/build-pli.yml).
val signingFile = System.getenv("PLI_STORE_FILE")?.takeIf { it.isNotBlank() }?.let(::file)

android {
    namespace = "com.yuksel.pli"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.yuksel.pli"
        minSdk = 31
        targetSdk = 36
        // CI builds count up so each new APK installs over the previous one.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }
    signingConfigs {
        if (signingFile != null) {
            create("release") {
                storeFile = signingFile
                storePassword = System.getenv("PLI_STORE_PASSWORD")
                keyAlias = System.getenv("PLI_KEY_ALIAS")
                keyPassword = System.getenv("PLI_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingFile != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    lint { checkReleaseBuilds = false; abortOnError = false }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.window:window:1.5.1")
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
}
