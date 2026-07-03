// Emulator core boundary: the Kotlin JNI facade + the native libretro host (src/main/cpp).
// Per-system core .so files (ppsspp_libretro etc.) are built by CI from pinned source
// (see .github/workflows/build-cores.yml) and dropped into src/main/jniLibs/<abi>/.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.retrovault.emulator"
    compileSdk = 35
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 26
        ndk {
            // arm64 for devices; x86_64 so the Android-emulator image can run the host too.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                // Swappy's prefab package requires the shared C++ STL.
                arguments("-DANDROID_STL=c++_shared")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        prefab = true // consume AGDK Swappy (games-frame-pacing) from its AAR
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core-model"))
    implementation(libs.games.frame.pacing)
}
