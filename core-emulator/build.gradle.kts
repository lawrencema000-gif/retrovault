// Emulator core boundary. Kotlin-only for now: the JNI facade + libretro host plan live here;
// the native C++ host (src/main/cpp) and per-ABI core .so files are wired with the NDK in the
// final integration pass. Until then LibretroBridge.available == false.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.retrovault.emulator"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // Activated in the functional pass (requires NDK):
    // externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
    // defaultConfig { ndk { abiFilters += listOf("arm64-v8a", "x86_64") } }
}

dependencies {
    implementation(project(":core-model"))
}
