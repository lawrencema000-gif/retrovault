# RetroVault

A multi-system **PSP / PS1 / PS2 emulator for Android** with a built-in **storefront**
of games that are legally free to distribute — plus a one-tap flow to import games you
already own.

The goal is the smooth "browse → tap → download → play" experience of a game store, built
**entirely on legally distributable content** (homebrew, public-domain, freeware, and
redistribution-licensed indie games) plus an **import-your-own-ROM/BIOS** flow for titles the
user legally owns.

> ⚖️ **RetroVault does not host, bundle, link to, or distribute copyrighted commercial games or
> console BIOS files.** Commercial titles and PS1/PS2 BIOS are supplied by the user from their
> own legally-obtained copies. This boundary is what keeps the project shippable and legal.

## Open source (required, not optional)

Every viable PlayStation-family core is copyleft — **PPSSPP** (PSP, GPL-2.0+), **SwanStation**
(PS1, GPLv3), **ARMSX2** (PS2, GPLv3) — so the app that links them is a GPL derivative and
**must be open source under the GPLv3.** A closed-source build is not legally possible with
these cores. RetroVault follows the proven **PPSSPP model**: open repository + complete source
per release, monetized with ads and an optional paid "Gold" unlock. See
[`docs/PLAN.md`](docs/PLAN.md) and [`LICENSE`](LICENSE).

## Status

Early scaffold (**Phase 0**). The current build is a Kotlin + Jetpack Compose **storefront
shell** running on placeholder catalog data, and it **builds cleanly** (`:app:assembleDebug`).
Next: the multi-module split, Supabase backend + real legal catalog, then the PSP (PPSSPP) core,
then downloads + import. Full plan in [`docs/PLAN.md`](docs/PLAN.md).

## Tech stack (target)

- **Kotlin** + **Jetpack Compose** (Material 3), single-Activity storefront + Navigation-Compose
- **libretro-frontend** model: one C++/JNI facade loads swappable `*_libretro_android.so` cores
  (PPSSPP → SwanStation/Beetle PSX/PCSX-ReARMed → ARMSX2)
- Separate `EmulatorActivity` (`android:process=:emu`), **SurfaceView** + Vulkan, render-thread
  `retro_run` loop, Compose HUD + touch overlay
- **Supabase** — Postgres catalog + Auth + Storage + RLS + Edge Functions (signed download URLs)
- **Cloudflare R2** (zero-egress) for large legal game payloads; Supabase Storage for box art + saves
- **WorkManager** resumable downloads (HTTP Range + sha256); **Coil** for box art
- Multi-module Gradle (`libs.versions.toml` + `:build-logic` convention plugins)

## Current project layout (scaffold)

```
app/src/main/java/com/retrovault/app/
├── MainActivity.kt            # Compose entry point
├── RetroVaultApp.kt           # Application
├── data/{model,repository}    # Game, GameSystem, CatalogRepository (placeholder → Supabase)
├── navigation/                # Destinations + root Scaffold/NavHost
├── ui/{components,screens,theme}
└── util/
```

## Building

Open in **Android Studio** (bundled JDK/SDK) — this repo opens and builds directly. minSdk 26
(Android 8.0+), compileSdk 35.

CLI builds need JDK 17–21 (the system JDK here is 25, which Gradle 8.11 does not run on — point
Gradle at Android Studio's bundled JBR, e.g. `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`).
`local.properties` (SDK path) is generated locally and git-ignored.

## License

**GPLv3** — see [`LICENSE`](LICENSE). Complete corresponding source ships with every release; no
EULA/DRM restrictions are added on top. Bundled emulator cores retain their own GPL licenses.
