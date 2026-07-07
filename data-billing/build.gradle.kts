plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.retrovault.billing"
    compileSdk = 35
    defaultConfig { minSdk = 26 }

    // P21 distribution split: `full` (Play Store, proprietary Google deps) vs `foss` (F-Droid/GPL,
    // zero proprietary deps). Must match the dimension declared in :feature-store and :app so the
    // `foss` selection propagates down the app -> feature-store -> data-billing chain.
    flavorDimensions += "distribution"
    productFlavors {
        create("full") { dimension = "distribution"; isDefault = true }
        create("foss") { dimension = "distribution" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android) // Apache-2.0 — fine in both flavors

    // Proprietary, full-only: Play Billing SDK + OkHttp for the server-verify call. `foss` never
    // sees these on any classpath (fullImplementation applies only to full* variants).
    "fullImplementation"(libs.billing.ktx)
    "fullImplementation"(libs.okhttp)
}
