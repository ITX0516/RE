plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.badukai.next"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.badukai.next"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            // P0: keep R8 off for now to avoid debug-cycle impact; enable in P1
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }

    packaging {
        jniLibs {
            // Modern packaging: AGP stores .so page-aligned but zlib-compressed inside the
            // APK; the system mmaps them at install time WITHOUT extracting to app-lib.
            // Combined with extractNativeLibs=false in AndroidManifest this cuts ~30-40% of
            // the INSTALLED footprint vs legacy "APK stored + extract copy".
            useLegacyPackaging = false
            pickFirsts += listOf(
                "lib/arm64-v8a/libc++_shared.so"
            )
        }
    }

    aaptOptions {
        // tflite/model/gz/so entries were "safety" listing from upstream but:
        //   - so is handled by packaging.jniLibs above (NOT assets)
        //   - model & gz are downloaded at runtime (not bundled)
        //   - tflite: we don't bundle any .tflite either
        // Leave only "bin" (pre-compressed / no gain from re-compressing).
        noCompress += listOf("bin")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
