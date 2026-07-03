# RetroVault / Pulsar — Stage Tracker

> **✅ All 10 stages' foundations laid and building green.**
> **📋 Execution now follows [`MASTERPLAN.md`](MASTERPLAN.md) — 27 steps (P1–P27), one per session.**
> **📍 CURRENT STEP → P2: Video path (SurfaceView + Vulkan/GL host render + Swappy)**
>
> ✅ **P1 done (2026-07-04):** NDK r28.2 live; `ppsspp_libretro` v1.20.4 built from pinned source
> in CI (both ABIs, 16KB readelf gate green); cores fetched+stripped via `scripts/fetch-cores.ps1`;
> `nativeProbeCore` dlopened the core on the Android-36 emulator image → `PPSSPP v1.20.4`,
> extensions `elf|iso|cso|prx|pbp|chd`, libretro API 1. ProbeCoreTest: OK (2 tests).

The original stages map to master-plan steps: Stage 4 → P1–P5 · Stage 3 polish → P7–P8, P17, P19 ·
Stage 5 → P15 · Stage 6 → P16 · Stage 7 → P23–P24 · Stage 8 → P25–P26 · Stage 9 → P21 ·
Stage 10 → P22. New capability steps: P6 (library ID), P9 (gamepad), P10 (states/rewind/FF),
P11 (settings framework), P12–P13 (compat GameDB), P14 (cheats), P18 (thermal), P20 (RetroAchievements),
P27 (skins/widgets/netplay).

This is the single source of truth for what stage the app is at. Every working session shows
which stage we're on and updates this file.

Legend: done ✅ · foundation laid 🧱 (structure/UI done; functional bits in the final integration pass) · in progress 🔄 · not started ⬜ · blocked 🔒 (needs external resource)

| # | Stage | Status | Notes |
|---|-------|--------|-------|
| 0 | **Foundations** — Kotlin/Compose scaffold, GitHub repo, verified build | ✅ | commit `d29ee36` |
| 1 | **Backend** — Supabase schema, RLS, storage, catalog wired over PostgREST | ✅ | project `mxasjicdkryaqugrccdo` |
| 2 | **Multi-module architecture** — core-model / core-ui / data-supabase / feature-store / app | ✅ | commit `3116911` |
| 3 | **UI/UX implementation** — Pulsar design system + screens | ✅ | boot/library/detail/settings + nav; player/saves/controls come at 4/6 |
| 4 | **PSP emulator core** — libretro JNI host + in-game player screen | 🧱 | `:core-emulator` (JNI facade + C++ host skeleton) + `:feature-player` (player UI) + launch; native core/.so/device = final pass |
| 5 | **Downloads + import** — WorkManager pipeline, BYO-ROM (SAF), signed-URL Edge Function | 🧱 | `:data-download` + import UI + deployed `get-download-url`; R2 hosting = final pass (needs Cloudflare acct) |
| 6 | **Accounts + cloud saves** — GoTrue auth client + `:data-saves` (store + sync) + Save-States/Controller screens | 🧱 | client + UI done; sign-in UI + real sync = final pass |
| 7 | **PS1 core** — SwanStation mapping + user BIOS import | 🧱 | core mapping + BIOS import/status; core `.so` + device = final pass |
| 8 | **PS2 core** — ARMSX2 mapping, device-capability gate | 🧱 | mapping + RAM/OS gate + BIOS; core `.so` + device = final pass |
| 9 | **Monetization** — AdMob free tier + "Pulsar Gold" IAP | 🧱 | `:data-billing` (Gold entitlement + facade) + Settings Gold row; AdMob/Play Billing SDKs = final pass |
| 10 | **Distribution** — Google Play + F-Droid + direct APK, dev verification | 🧱 | DISTRIBUTION.md + PRIVACY.md + fastlane metadata; build variants + signing = final pass (needs Play/dev accounts) |

## Stage 3 breakdown (current)

The design (`design/pulsar/`) is dark navy, blue `#2a7fff` → teal `#21e6c1`, Chakra Petch (display)
+ Manrope (body), and covers all screens. We implement the browsing surfaces now; the
player/saves/controller screens are built alongside their functional stages (4/6).

- [x] Pulsar design system in `:core-ui` — color palette, gradients, Chakra Petch + Manrope fonts
- [x] Boot / splash screen (logo + PULSAR wordmark + progress)
- [x] Library screen — "Your Library", search + avatar, Featured carousel, All Games grid
- [x] Floating bottom nav (Library / Saves / Controls / Settings)
- [x] Game Detail screen — hero cover, meta chips, play button, action row, about
- [x] Settings screen — Video / Audio / Emulation / System sections
- [ ] (Stage 4/6) In-game player, Save States, Controller remap screens — built with the emulator
- [ ] On-device visual review + polish pass (needs a device/emulator on your side)

## Branding note

The design is branded **Pulsar** ("Portable System Player") — a deliberately trademark-safe name
(no Sony marks). We adopt **Pulsar** as the product/display name. The repo + package
(`com.retrovault` / `retrovault`) can be renamed to match later if desired; not required for the UI.
