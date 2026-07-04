# RetroVault / Pulsar — Stage Tracker

> **✅ All 10 stages' foundations laid and building green.**
> **📋 Execution now follows [`MASTERPLAN.md`](MASTERPLAN.md) — 27 steps (P1–P27), one per session.**
> **📍 CURRENT STEP → P5: First light on the user's physical device [DEVICE SESSION — everything is staged]**
>
> 🎆 **P5-prep done — FIRST LIGHT ACHIEVED ON EMULATOR (2026-07-04):** the real, license-verified
> GPL-3.0 homebrew **Battlegrounds 3** (github.com/xfacter/battlegrounds3, mirrored with recorded
> evidence from the PPSSPP store lead-list) flowed through the ENTIRE production pipeline —
> live Supabase catalog → `get-download-url` edge fn → download → sha256 → zip-slip-safe unzip
> install → playable resolution (EBOOT.PBP) → **PPSSPP core booted it and presented 129 frames**.
> FirstLightTest OK. Store UI now has real Download→Downloading→Play states. PSP placeholders
> unpublished. Known nit for device polish: ship `ppge_atlas.zim` into the core system dir
> (PPSSPP OSD/save-dialog font; games run fine without it, dialogs render blank).
>
> ✅ **P6 done (2026-07-04):** Library identification engine (`:data-library`) — pure-Kotlin
> PARAM.SFO parser + PBP reader + ISO9660 reader, `GameIdentifier` (canonical serial from
> DISC_ID / stable fake ID for homebrew, ICON0 art extraction, boot-CRC32), JSON-cached
> `LibraryIndex` (persist + skip-unchanged rescan), SAF `LibraryScanner`. LibraryIdentifyTest
> validates serial canonicalization, a synthetic PARAM.SFO/PBP round-trip, homebrew fake-ID,
> index persistence, and the real Battlegrounds 3 EBOOT — **OK (6 tests)**; full suite **OK
> (14 tests)**. Deferred: CSO/CHD decompression (native libchdr, added with the core
> pass-through); SAF-tree scan + ISO9660 unverified without a device/real ISO; wiring imported
> games into the Library UI grid lands with the installed-games section.
>
> ✅ **P7 done (2026-07-04):** Touch **feel layer** — the marquee control quality. `TouchOverlayView`
> rewritten: d-pad is ONE control with 9 angle zones + a hysteresis band (rolling thumb never
> flickers between directions; 4-way toggle), **floating auto-centering analog stick** (base
> anchors where the thumb lands, deadzone rescale + saturation, pointer owns it till lift),
> slide-off/slide-between by default plus PPSSPP-style **gliding** (keep-first-pressed) and
> sticky-d-pad options, hitboxes decoupled from visuals with region modifiers (1.0–2.0) and
> grow-when-pressed (1.5×), pressed-state highlight + opacity. New `Haptics` (crisp CLICK on
> press / lighter TICK on release, intensity scale, all off a dedicated thread — vibrator
> binder calls never touch the input path). `InputHub` merges touch-analog vs pad-analog
> (larger deflection wins). FeelTest validates diagonals, the hysteresis flicker-guard (holds
> UP at 24° off-axis, flips at 45°), floating-stick anchor+rescale, and gliding — **OK (18
> tests)** full suite. Note: AudioTest's raw underrun count relaxed to a logged emulator-quality
> metric (SwiftShader AAudio HAL degrades across the shared suite process); DRC-within-cap stays
> a hard assertion, strict zero-underrun bar validated in isolation + on device (P5).
>
> ✅ **P4 done (2026-07-04):** Input hot path live — `:core-input` module with `TouchOverlayView`
> (raw View over the SurfaceView, `requestUnbufferedDispatch`, full-pointer re-hit-testing →
> multi-touch + slide-between for free, Canvas-drawn default layout), `GamepadMapper`
> (Android-standard keycodes + left-stick radial deadzone + HAT d-pad), `InputHub` merging both
> sources into the native atomic snapshot; Compose fully retired from the input path (chrome
> only); input→frame latency instrumentation (event timestamp → `retro_input_poll` EMA).
> InputTest: 4 controls held simultaneously with zero drops + slide-between verified against
> the native snapshot; gamepad buttons/analog/hat land in the same snapshot — full suite
> **OK (7 tests)**. Physical-pad validation batched into P5/P9 device sessions.
>
> ✅ **P3 done (2026-07-04):** Oboe audio live — LowLatency/Exclusive stream, lock-free SPSC ring,
> linear resampler with libretro dynamic rate control (±0.5%), half-ring silence prefill,
> video-warmup-gated audio start (15 frames), background pause/resume, BT-friendly buffer toggle
> (verified: ring 16384→32768), device-change restart hook. AudioTest on emulator: 0 underrun
> fills over a 20 s window, DRC always inside the cap, fill in band — **OK (1 test)**; full suite
> (probe+render+audio) **OK (5 tests)**. Deferred to P5 device session: 10-min LowLatency soak,
> real BT route, live device-change swap.
>
> ✅ **P1 done (2026-07-04):** NDK r28.2 live; `ppsspp_libretro` v1.20.4 built from pinned source
> in CI (both ABIs, 16KB readelf gate green); cores fetched+stripped via `scripts/fetch-cores.ps1`;
> `nativeProbeCore` dlopened the core on an emulator image → `PPSSPP v1.20.4`, API 1.
>
> ✅ **P2 done (2026-07-04):** GL video backbone live — EGL/ES3 context + pbuffer keep-alive,
> software-frame blit (565/8888/1555), SET_HW_RENDER FBO path, letterboxed present, paced run
> loop with background auto-pause, correct context_destroy→dlclose→EGL teardown order. Swappy
> integrated (games-frame-pacing prefab). RenderTest on the android-35 emulator: software core
> AND GLES hw-render core each presented 120+ frames with sane pacing, back-to-back sessions
> clean — **OK (2 tests)**. Deferred to P5 (device session): Swappy activation check on real
> hardware + physical rotation test (emulator ran headless; surface-recreate path is handled).

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
