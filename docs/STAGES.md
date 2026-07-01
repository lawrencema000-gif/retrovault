# RetroVault / Pulsar — Stage Tracker

> **CURRENT STAGE → Stage 3: UI/UX implementation (Pulsar design system)**

This is the single source of truth for what stage the app is at. Every working session shows
which stage we're on and updates this file.

Legend: done ✅ · in progress 🔄 · not started ⬜ · blocked 🔒 (needs external resource)

| # | Stage | Status | Notes |
|---|-------|--------|-------|
| 0 | **Foundations** — Kotlin/Compose scaffold, GitHub repo, verified build | ✅ | commit `d29ee36` |
| 1 | **Backend** — Supabase schema, RLS, storage, catalog wired over PostgREST | ✅ | project `mxasjicdkryaqugrccdo` |
| 2 | **Multi-module architecture** — core-model / core-ui / data-supabase / feature-store / app | ✅ | commit `3116911` |
| 3 | **UI/UX implementation** — Pulsar design system + screens | 🔄 **CURRENT** | from Claude design `Pulsar.dc.html` |
| 4 | **PSP emulator core** — PPSSPP via libretro/JNI + in-game player screen | ⬜ | needs NDK + core .so + test device |
| 5 | **Downloads + import** — Cloudflare R2, WorkManager pipeline, BYO-ROM (SAF) | 🔒 | needs Cloudflare account |
| 6 | **Accounts + cloud saves** — supabase-kt auth, save-states + controller screens | ⬜ | |
| 7 | **PS1 core** — SwanStation + user BIOS import | ⬜ | |
| 8 | **PS2 core** — ARMSX2, device-capability gated | ⬜ | |
| 9 | **Monetization** — AdMob free tier + "Pulsar Gold" IAP | ⬜ | |
| 10 | **Distribution** — Google Play + F-Droid + direct APK, dev verification | 🔒 | needs Play/dev accounts |

## Stage 3 breakdown (current)

The design (`design/pulsar/`) is dark navy, blue `#2a7fff` → teal `#21e6c1`, Chakra Petch (display)
+ Manrope (body), and covers all screens. We implement the browsing surfaces now; the
player/saves/controller screens are built alongside their functional stages (4/6).

- [ ] Pulsar design system in `:core-ui` — color palette, gradients, Chakra Petch + Manrope fonts
- [ ] Boot / splash screen (animated logo + PULSAR wordmark)
- [ ] Library screen — "Your Library", search + avatar, Continue Playing carousel, All Games grid
- [ ] Floating blurred bottom nav (Library / Saves / Controls / Settings)
- [ ] Game Detail screen — hero cover, meta chips, play button, action row, about
- [ ] Settings screen — Video / Audio / Emulation / System sections
- [ ] (Stage 4/6) In-game player, Save States, Controller remap screens

## Branding note

The design is branded **Pulsar** ("Portable System Player") — a deliberately trademark-safe name
(no Sony marks). We adopt **Pulsar** as the product/display name. The repo + package
(`com.retrovault` / `retrovault`) can be renamed to match later if desired; not required for the UI.
