# RetroVault

A multi-system **PSP / PS1 / PS2 emulator for Android** with a built-in **storefront**
of games that are legally free to distribute — plus a one-tap flow to import games you
already own.

The goal is the smooth "browse → tap → download → play" experience of a game store,
built **entirely on legally distributable content**:

- **Homebrew** (PSP / PS1 / PS2 community games)
- **Public-domain** and **freeware** titles
- **Indie games** whose developers permit redistribution (CC / GPL / explicit permission)
- **Import-your-own-ROM** for games the user legally owns

> ⚖️ **RetroVault does not host, bundle, or distribute copyrighted commercial games or
> console BIOS files.** Commercial titles and BIOS are provided by the user from their own
> legally-obtained copies. This is the hard line that keeps the project shippable and legal.

## Status

Early scaffold. Current build is a Kotlin + Jetpack Compose **storefront shell** running on
placeholder catalog data. Emulator cores, the Supabase backend, downloads, and import are
the next phases (see [`docs/PLAN.md`](docs/PLAN.md)).

## Tech stack

- **Kotlin** + **Jetpack Compose** (Material 3), single-Activity, Navigation-Compose
- **Coil** for async box-art loading
- **Supabase** (planned) — catalog, auth, user library, cloud saves, CDN for legal game files
- **Native emulator cores** (planned) — PPSSPP (PSP) first, then PS1 and PS2 via JNI/libretro

## Project layout

```
app/src/main/java/com/retrovault/app/
├── MainActivity.kt            # Compose entry point
├── RetroVaultApp.kt           # Application
├── data/
│   ├── model/                 # Game, GameSystem
│   └── repository/            # CatalogRepository (in-memory placeholder → Supabase)
├── navigation/                # Destinations + root Scaffold/NavHost
├── ui/
│   ├── components/            # GameCard, ...
│   ├── screens/               # Home (store), GameDetail, Library, Settings
│   └── theme/                 # Color, Type, Theme
└── util/                      # formatBytes, ...
```

## Building

Requires **Android Studio** (bundled JDK/SDK) — this repo is set up to open and build directly.

1. Open the project in Android Studio.
2. Let it sync Gradle (first sync downloads Gradle 8.11.1 + dependencies).
3. Run the `app` configuration on an emulator or device (minSdk 26 / Android 8.0+).

CLI builds need JDK 17–21 (the system JDK here is 25, which Gradle 8.11 does not run on —
point Gradle at Android Studio's bundled JBR, e.g. set `JAVA_HOME` to
`C:\Program Files\Android\Android Studio\jbr`).

`local.properties` (SDK path) is generated locally and git-ignored.

## Legal

RetroVault is an emulator + a store for **legally distributable** games only. Emulators are
legal; distributing copyrighted ROMs/BIOS is not. The app is designed so the hosted catalog
contains only content we have the right to distribute, and everything else is user-supplied.
