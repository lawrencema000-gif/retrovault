plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.retrovault.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.retrovault.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        vectorDrawables {
            useSupportLibrary = true
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // P21 distribution split. `full` = Play Store (Play Billing + AdMob); `foss` = F-Droid/GPL with
    // ZERO proprietary deps. The dimension is declared here + in :feature-store + :data-billing so
    // variant-aware resolution picks foss all the way down. Instrumented tests run on fullDebug
    // (isDefault) via :app:connectedFullDebugAndroidTest.
    flavorDimensions += "distribution"
    productFlavors {
        create("full") { dimension = "distribution"; isDefault = true }
        create("foss") { dimension = "distribution" }
    }

    packaging {
        jniLibs {
            // Extract native libs to disk so cores can be dlopen-ed by absolute path.
            // Revisit at P22 (Play Asset Delivery / on-demand core download).
            useLegacyPackaging = true
        }
    }

    buildTypes {
        release {
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
}

dependencies {
    // Feature + core modules
    implementation(project(":core-model"))
    implementation(project(":core-ui"))
    implementation(project(":core-emulator"))
    implementation(project(":core-input"))
    implementation(project(":data-supabase"))
    implementation(project(":data-download"))
    implementation(project(":data-library"))
    implementation(project(":data-saves"))
    implementation(project(":data-settings"))
    implementation(project(":data-cheats"))
    implementation(project(":feature-store"))
    implementation(project(":feature-player"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    debugImplementation(libs.androidx.ui.tooling)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.okhttp)
    androidTestImplementation(libs.kotlinx.coroutines.android)
}

// P21 acceptance gate: the `foss` variant must resolve to ZERO proprietary Google deps (F-Droid/GPL
// distribution). Resolves the full transitive foss runtime classpaths and fails the build if any
// billingclient / play-services / UMP artifact leaks in. The AGP variant configurations don't exist
// until AGP's own afterEvaluate has run, so register in afterEvaluate; the resolved-artifacts
// Provider is captured there (no Project access in doLast) for configuration-cache safety.
afterEvaluate {
    val proprietaryGroups = setOf(
        "com.android.billingclient",
        "com.google.android.gms",   // covers play-services-ads*; F-Droid bans this whole group
        "com.google.android.ump",
    )
    listOf("fossDebugRuntimeClasspath", "fossReleaseRuntimeClasspath").forEach { cfgName ->
        val cfg = configurations.findByName(cfgName) ?: return@forEach
        // Walk the resolved dependency GRAPH (module coordinates), not artifacts — this needs no
        // artifactType and so avoids the android-res/jar/symbol variant-ambiguity of artifact views.
        val root = cfg.incoming.resolutionResult.rootComponent
        val verify = tasks.register("verify${cfgName.replaceFirstChar(Char::uppercase)}") {
            doLast {
                val seen = mutableSetOf<String>()
                val leaks = mutableListOf<String>()
                fun walk(c: org.gradle.api.artifacts.result.ResolvedComponentResult) {
                    (c.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier)
                        ?.takeIf { it.group in proprietaryGroups }
                        ?.let { leaks += "${it.group}:${it.module}:${it.version}" }
                    c.dependencies.filterIsInstance<org.gradle.api.artifacts.result.ResolvedDependencyResult>()
                        .forEach { if (seen.add(it.selected.id.displayName)) walk(it.selected) }
                }
                walk(root.get())
                require(leaks.isEmpty()) { "FOSS variant leaked proprietary deps: $leaks" }
            }
        }
        tasks.named("check") { dependsOn(verify) }
    }
}
