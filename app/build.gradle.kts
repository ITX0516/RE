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
            // AI-START RELIABILITY REVERT (2026-08-02): useLegacyPackaging=true.
            //   After "Failed to start AI" on 14.7MB build we revert all novel
            //   packaging to the CONSERVATIVE defaults that are known to work:
            //     - jniLibs now has 6 .so files (libkatago + 5 ld.so deps).
            //     - Legacy packaging = Gradle does NOT compress jniLibs inside the
            //       APK → each .so is stored uncompressed, 4k-page-aligned, which
            //       is historically what linker / PackageManager / ART test against.
            //     - Combined with extractNativeLibs=true in the manifest, the OS
            //       GUARANTEES a real extracted copy in /data/app-lib (nativeLibraryDir)
            //       that dlopen() can always find, even for child processes that
            //       do NOT share ART's linker-namespace (i.e. plain exec*() + LD_LIBRARY_PATH).
            //   APK size cost: ~6MB extra vs compressed jniLibs. Worth it.
            useLegacyPackaging = true
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
