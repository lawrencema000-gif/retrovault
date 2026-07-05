// Input hot path: touch overlay (raw View) + gamepad routing into the native input snapshot.
// No Compose here — nothing sits between finger and core.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.retrovault.input"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core-emulator"))
    implementation(libs.kotlinx.serialization.json)
}
