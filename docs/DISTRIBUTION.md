# Pulsar — Distribution

Pulsar is GPLv3 open-source. Ship the same clean build (engine + user-imported ROMs + a store of
only legally-distributable games) across channels; the failure mode to engineer against is the
**Intellectual Property** policy, not "it's an emulator."

## Channels

| Channel | Role | Notes |
|---|---|---|
| **Google Play** | Primary | Emulators are allowed (interpreter/VM exception). Must pass IP policy: no bundled/linked ROMs or BIOS, no console trademarks/box-art, no in-app ROM-site links. |
| **Direct APK / GitHub Releases** | Secondary | Full control, self-updater; common in the emulator scene. |
| **F-Droid** | FOSS variant | Fits GPLv3; no proprietary ad/IAP SDKs — donation-only build. |
| **Samsung Galaxy Store / Aptoide** | Extra reach | Optional. (Amazon Appstore shut down Aug 2025.) |

## Compliance checklist (every channel)

- ❌ Never bundle/preload/link a ROM or a Sony BIOS. Users import their own.
- ❌ No console trademarks, logos, or box/character art in app, icon, screenshots, listing.
- ✅ Store lists only homebrew / public-domain / freeware / redistribution-licensed titles; keep
  license paperwork per title.
- ✅ Generic branding ("Pulsar", "Portable System Player") — no PlayStation marks.
- ✅ Complete Google's 2026 developer identity verification; operate as an identifiable business
  (anonymity is no longer a fallback for sideloaded apps on certified devices).
- ✅ Keep a fast DMCA takedown process; keep the storefront separable from the emulator.

## Monetization (see :data-billing)

PPSSPP model: open repo + AdMob free tier + one-time **Pulsar Gold** IAP (+ optional cloud-storage
subscription). Google Play Billing everywhere except the US external-billing carve-out (Epic v.
Google, sunsets Nov 1 2027). GPL source is never gated behind payment. F-Droid build is
donation-only (no proprietary SDKs).

## Build variants (P21 — live)

Product-flavor dimension `distribution`, declared in `:app`, `:feature-store`, `:data-billing`:

- `full` — Play/direct: AdMob + Play Billing + UMP consent (proprietary Google deps). `isDefault`.
- `foss` — F-Droid/GPL: **zero proprietary deps** (Play Billing, play-services-ads, UMP all stripped);
  Gold unavailable, ads off, free tier fully functional. Enforced by the `:app:verifyFoss*RuntimeClasspath`
  Gradle gate (wired into `check`), which fails the build if any `com.android.billingclient` /
  `com.google.android.gms` / `com.google.android.ump` artifact leaks into a foss classpath.

Release builds:
- Play AAB: `./gradlew :app:bundleFullRelease`
- F-Droid / direct GPL APK: `./gradlew :app:assembleFossRelease`

…with a signing config from a keystore kept **out of git**. Instrumented tests run on the full
variant: `./gradlew :app:connectedFullDebugAndroidTest` (plain `connectedDebugAndroidTest` no longer
exists once flavors are declared).
