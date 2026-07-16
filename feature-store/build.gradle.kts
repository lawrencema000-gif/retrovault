plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.retrovault.feature.store"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    // P21 distribution split — carries the dimension so `foss` propagates app -> feature-store ->
    // data-billing. full = Play Billing (Gold); foss = neither. Ads were removed entirely
    // (GPL compliance: the PPSSPP core's GPL grant doesn't allow linking AdMob into the same APK).
    flavorDimensions += "distribution"
    productFlavors {
        create("full") { dimension = "distribution"; isDefault = true }
        create("foss") { dimension = "distribution" }
    }
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-ui"))
    implementation(project(":data-supabase"))
    implementation(project(":data-download"))
    implementation(project(":data-saves"))
    implementation(project(":data-settings"))
    implementation(project(":data-library"))
    implementation(project(":data-cheats"))
    implementation(project(":data-billing"))
    implementation(project(":core-input"))   // P27: .pulsarskin store + layout export

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.ui.tooling)
}
