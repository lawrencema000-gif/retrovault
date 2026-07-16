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

        // Crash reporting (full flavor, opt-in): empty DSN = Sentry never initializes. The real
        // DSN arrives via CI secret at release time; never hardcode it.
        buildConfigField("String", "SENTRY_DSN", "\"${System.getenv("SENTRY_DSN") ?: ""}\"")
    }

    // P21 distribution split. `full` = Play Store (Play Billing "Gold" — NO ads: linking AdMob
    // into the same APK as the GPL PPSSPP core is a GPL-compliance risk, dropped 2026-07-17);
    // `foss` = F-Droid/GPL with ZERO proprietary deps. The dimension is declared here +
    // in :feature-store + :data-billing so variant-aware resolution picks foss all the way down.
    // Instrumented tests run on fullDebug (isDefault) via :app:connectedFullDebugAndroidTest.
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
        buildConfig = true   // SENTRY_DSN field (full flavor crash reporting, P22)
    }

    // Reproducible builds (P22): the Play dependency-metadata block is encrypted with Google's
    // key and unverifiable by F-Droid — keep it out of the APK, keep it in the AAB for Play.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }
}

// P22 GPL compliance: the APK must carry the license + notice texts (GPLv3 §4/§6 — a URL is not
// a copy). NOTICE.md and LICENSE are copied from the repo root at build time so the bundled
// copies can never drift from the canonical ones; static third-party license texts live in
// app/src/main/assets/legal/. Rendered by the in-app Licenses screen.
val legalAssetsDir = layout.buildDirectory.dir("generated/legalAssets")
val copyLegalAssets = tasks.register<Copy>("copyLegalAssets") {
    from(rootProject.file("NOTICE.md"), rootProject.file("LICENSE"))
    into(legalAssetsDir.map { it.dir("legal") })
    rename("LICENSE", "gpl-3.0.txt")
}
android.sourceSets["main"].assets.srcDir(legalAssetsDir)
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(copyLegalAssets)
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
    implementation(libs.androidx.glance.appwidget)   // "Continue playing" widget (P27)

    // Crash reporting (P22): full flavor only, opt-in, inert without a DSN. The SDK is MIT but
    // reports to the proprietary sentry.io service — foss ships zero crash-reporting code.
    "fullImplementation"(libs.sentry.android)

    debugImplementation(libs.androidx.ui.tooling)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.okhttp)
    androidTestImplementation(libs.kotlinx.coroutines.android)
}

// P21 acceptance gates. `foss` must resolve to ZERO proprietary Google deps (F-Droid/GPL
// distribution). `full` may carry Play Billing (Gold) but must NEVER link ads/UMP — bundling
// AdMob with the GPL PPSSPP core in one APK is a GPL-compliance risk (ads dropped 2026-07-17,
// this gate keeps them from coming back). Resolves the full transitive runtime classpaths and
// fails the build on any banned artifact. The AGP variant configurations don't exist until
// AGP's own afterEvaluate has run, so register in afterEvaluate; the resolved-artifacts
// Provider is captured there (no Project access in doLast) for configuration-cache safety.
afterEvaluate {
    // foss: the whole gms group is banned (F-Droid policy) along with billing + UMP.
    // full: Play Billing legitimately pulls gms base/tasks/location transitively, so full bans
    // UMP as a group but gms only by module prefix (play-services-ads, -ads-lite, -ads-identifier…).
    val fossGroups = setOf("com.android.billingclient", "com.google.android.gms", "com.google.android.ump")
    val fullGroups = setOf("com.google.android.ump")
    val fullGmsAdsPrefix = "play-services-ads"
    val rulesByConfig = mapOf(
        "fossDebugRuntimeClasspath" to Pair(fossGroups, false),
        "fossReleaseRuntimeClasspath" to Pair(fossGroups, false),
        "fullDebugRuntimeClasspath" to Pair(fullGroups, true),
        "fullReleaseRuntimeClasspath" to Pair(fullGroups, true),
    )
    rulesByConfig.forEach { (cfgName, rule) ->
        val (bannedGroups, banGmsAds) = rule
        val cfg = configurations.findByName(cfgName) ?: return@forEach
        // Walk the resolved dependency GRAPH (module coordinates), not artifacts — this needs no
        // artifactType and so avoids the android-res/jar/symbol variant-ambiguity of artifact views.
        val root = cfg.incoming.resolutionResult.rootComponent
        val verify = tasks.register("verify${cfgName.replaceFirstChar(Char::uppercase)}") {
            doLast {
                val seen = mutableSetOf<String>()
                val leaks = mutableListOf<String>()
                fun banned(group: String, module: String): Boolean =
                    group in bannedGroups ||
                        (banGmsAds && group == "com.google.android.gms" && module.startsWith(fullGmsAdsPrefix))
                fun walk(c: org.gradle.api.artifacts.result.ResolvedComponentResult) {
                    (c.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier)
                        ?.takeIf { banned(it.group, it.module) }
                        ?.let { leaks += "${it.group}:${it.module}:${it.version}" }
                    c.dependencies.filterIsInstance<org.gradle.api.artifacts.result.ResolvedDependencyResult>()
                        .forEach { if (seen.add(it.selected.id.displayName)) walk(it.selected) }
                }
                walk(root.get())
                require(leaks.isEmpty()) { "$cfgName leaked banned deps: $leaks" }
            }
        }
        tasks.named("check") { dependsOn(verify) }
    }
}
