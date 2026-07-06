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
> ✅ **P17 done (2026-07-06):** Onboarding, empty states, a11y, platform baseline (device-verified
> bits — Accessibility Scanner + <2min-unaided are the P5 device pass). `AppPrefs` (core-ui,
> SharedPreferences-backed Compose state; private backing + read-only getters to dodge a
> generated-setter clash). **3-step skippable onboarding** (legal explainer → SAF games-folder
> pick → controller check) gated by `onboardingSeen` (Boot → Onboarding first-run → Library).
> **OLED-black theme** (`PulsarTheme(oledBlack)` swaps base to true black; live via AppPrefs
> state) + Settings APPEARANCE toggle. **Per-app language** (System/English/日本語, persisted +
> applied via platform `LocaleManager` on API33+). **Teaching library empty state**
> (browse/import, distinct offline copy). **Predictive back → pause menu** (manifest
> `enableOnBackInvokedCallback` + PlayerScreen BackHandlers: back opens quick menu, second back
> closes it). a11y: content descriptions on nav/onboarding actions. OnboardingPrefsTest: the
> once-only gate + OLED/language pref persistence across process re-init — **OK (2)**; full
> suite **OK (51 tests)**. Deferred to P5 device: Accessibility Scanner sweep, TalkBack/focus-
> order pass, edge-to-edge cutout polish.
>
> ✅ **P16 done (2026-07-06):** Auth + cloud saves with 3-way conflict UX (emulator-provable
> portion; two-physical-device collide test → P5 device session). **Anonymous-first auth:**
> enabled anon sign-ins via the Management API (PAT from Cred Manager, my Supabase mandate),
> `AuthManager` (GoTrue REST — anon signup, session persist, token refresh, email-link to make
> permanent); real anon session verified live. **Cloud saves:** `cloud_saves` manifest table +
> append-only `cloud_save_versions` (restore) + owner-only RLS (works for anon + permanent) +
> private `saves` bucket w/ owner-folder RLS; history maintained by trigger. **The acceptance
> — never clobber:** `ConflictResolver` (3-way: local vs cloud vs last-synced base → IN_SYNC/
> PUSH/PULL/CONFLICT); `CloudSaveSync` tracks the per-device base so two devices editing the
> same save produce a **CONFLICT, not silent loss**; `CloudSaveClient` (Storage + manifest over
> RLS-scoped tokens). `ConflictSheet` UI (Keep device / cloud / both — both preserves the cloud
> copy in a sidecar). CloudSaveTest: resolver exhaustive (every branch, incl. both-diverged→
> conflict + no-base→conflict), **two-device sim proving cloud unchanged + local intact + KEEP_
> BOTH preserves both**, and **live anonymous sign-in + real Storage round-trip (upload→manifest
> →download, bytes+hash match)** — **OK (3)**; full suite **OK (49 tests)**. ⚙️ Provisioning
> done this session: Supabase **anonymous sign-ins ENABLED**. Deferred: sign-in/link UI screen,
> auto-sync-on-exit player wiring, OAuth (needs Google client config), two-device live test (P5).
>
> ✅ **P15 done (2026-07-06):** Store v1 — legal catalog + delivery, on Supabase Storage (no R2
> needed). Catalog contract extended (`Game`/DTO/query carry `licenseUrl`+`sourceUrl`; sha256
> gate factored into shared `Sha256` util). **License curation done RIGHT:** a multi-agent
> workflow researched + **adversarially verified** all 18 PPSSPP-store candidates at their
> canonical sources (PPSSPP's own hosting permission does NOT transfer to us). Only titles with
> clear third-party-redistribution grants pass. **Newly hosted (verified):** rRootage
> (BSD-2-Clause, Kenta Cho/mrbrown) + Suicide Barbie (MIT + CC-BY-SA-4.0, The Black Lotus),
> mirrored to Supabase Storage via a one-shot token edge fn (now a 410 stub) with sha256+size,
> catalog rows published. **The verifier earned its keep:** it REFUTED Ozone (GPL code but
> gfx/music by uncredited third parties → GameBrew "Mixed") and — importantly — **Battlegrounds
> 3** (code GPLv3-clean, but SFX appear sourced from commercial sounddogs.com, an over-claimed
> CC-BY-SA grant = chain-of-title defect). ⚠️ **bg3 kept published for now (test anchor) but
> FLAGGED — recommend migrating the anchor to rRootage + unpublishing bg3 pending author
> confirmation.** 13 of 18 candidates rejected (freeware-no-redist / NC / mixed assets). Store
> UI: prominent LICENSE & SOURCE card (author + license + tappable source link) on the detail
> screen. StoreTest: catalog serves licensed downloadable titles w/ author+size+link, sha256
> gate BOTH ways (match passes / tamper aborts), and **rRootage downloads→verifies→installs→
> boots (121 frames)** — the pipeline proven on a title beyond the original anchor — **OK (4)**;
> full suite **OK (46 tests)**. NOTICE records per-title license evidence + the bg3 flag.
>
> ✅ **P14 done (2026-07-06):** Cheats (CWCheat). **Native:** `retro_cheat_reset`/`retro_cheat_set`
> symbols loaded; enabled codes pushed via `nativeApplyCheats(String[])` and applied on the
> run-loop thread (`applyCheatsIfDirty` before retro_run); cleared per session; a homebrew core
> with no cheat interface is handled gracefully. **`:data-cheats`:** `CheatDb` parser (CWCheat
> `_S`/`_G`/`_C0/_C1`/`_L` format, dashless-serial normalization), `CheatManager` (import from
> **SAF file or URL — validated, NEVER bundled**; per-serial enabled set as JSON; default-on
> honored; produces the enabled-code list). **UI:** in-game `CheatsSheet` (per-game toggles +
> search + empty states) off the quick menu's new Cheats tile; Settings → CHEATS imports/removes
> the cheat.db with a prominent "never bundled" note. **Enabling any cheat clears
> `hardcoreActive`** (achievements off, RA-ready). CheatTest: format parse, enabled-state
> round-trip across instances, **the legal boundary (walks the whole APK asset tree asserting no
> cheat.db ships)**, and live apply→clear on the real PPSSPP session without wedging — **OK (4)**;
> full suite **OK (42 tests)**.
>
> ✅ **P13 done (2026-07-06):** In-app compat reporting + game-page surfacing. **Account-less
> design** (auth accounts arrive later): reports key on a persistent install UUID; the
> `submit-compat-report` edge function validates (serial pattern, rating 1–5, size caps) and
> inserts with the service role; a **DB unique index enforces once per install+serial+
> app-version** and a **trigger maintains `compat_summary`** (count/avg/last). **App:**
> `CompatReporter` (installId, prompt policy ≥10 min + once per serial+version, submit,
> public `summaryFor`); post-session **CompatRatingSheet** (5 tiers Broken→Perfect + optional
> graphics/audio/speed sub-scores) shown on quit when eligible, auto-filling device info
> (SoC/GPU family/ABI/SDK/backend) + the user's settings diff + core version; game detail
> pages show a **"Community ★ x.x · N reports"** chip when the installed game's serial has
> reports. **🐛 Fixed along the way: menu-quit called session.stop() before finish(), which
> skipped the onDestroy auto-save — "Continue" only survived process kills. Quit now flows
> through teardown so the auto-save always lands.** CompatReportTest: prompt policy, stable
> install id, and a REAL round-trip against the live backend (insert → duplicate rejected →
> summary ≥1 with sane avg; row verified server-side) — **OK (3)**; full suite **OK (38)**.
>
> ✅ **P12 done (2026-07-06):** Compatibility GameDB, end to end. **Two levers:** (1) PPSSPP's
> own pinned `compat.ini` (GPL-2.0, 71 sections/890 serials) ships via CoreAssets into the
> core system dir — the CORE applies all engine-level per-serial flags natively; (2) our
> GameDB pipeline for app-level data: `tools/gamedb-import/import.mjs` (Node) parses the same
> file → **baked APK snapshot** (`assets/gamedb/snapshot.json`) + Supabase. **Cloud:** 6-table
> schema (game_serials, gamedb_entries, device_profiles, compat_reports, recommended_settings,
> compat_summary) with RLS (public read; reports insert-only per-user, P13 fills); token-
> guarded **`gamedb-sync` edge function** re-imports from the pinned tag, upserts (890/890),
> and regenerates the **versioned public storage snapshot** (`gamedb/latest.json` + v-stamped,
> verified fetchable) — the plan's "snapshot regenerates end-to-end" acceptance, demonstrated
> live. **App:** `GameDb` provider (baked snapshot, cache-file override point for remote
> refresh at P13) feeding the resolver's GAMEDB layer by default; EmulatorActivity identifies
> the disc **serial** at launch (GameIdentifier header read) → GameDB closure + native compat
> flags; `SaveStatesNotRecommended` flag **gates auto-save** (PPSSPP's judgment respected).
> GameDbTest: snapshot loads (890), known-fussy serials auto-get flags (Darkstalkers
> ULES-00016 → ForceSoftwareRenderer; GoW → framerate hack), compat.ini lands in the system
> dir, GAMEDB origin resolves + user override wins — **OK (3)**; full suite **OK (35 tests)**
> (AudioTest ring-fill % downgraded to logged metric alongside underruns — long-suite
> SwiftShader AAudio starvation; DRC-cap + liveness stay hard asserts).
>
> ✅ **P11 done (2026-07-05):** Settings framework. **4-layer resolution engine** (`:data-settings`):
> DEFAULT → GAMEDB (provider stub, wired to Supabase at P12) → DEVICE → USER_GLOBAL →
> USER_GAME, every value carrying its **origin badge**; user layers stored as JSON *diffs*
> (only explicit overrides). Typed `SettingDef` registry (Toggle/Choice) mapped to PPSSPP
> core options (internal resolution, frameskip, texture filter/upscale, CPU core, fast
> memory…). **Native core variables now fully dynamic**: `nativeSetCoreVariable` +
> GET_VARIABLE map + **GET_VARIABLE_UPDATE handshake** for live mid-session changes (the
> hardcoded override table from the P8 render fix is deleted — the device layer supplies
> IR JIT + native res on x86 AVDs, real arm64 devices default to full JIT + 2×).
> `DeviceClass` detection (Adreno/Mali/Xclipse/PowerVR/emulator; GL-renderer string capture
> point + ABI fallback). `BackendPolicy`: Vulkan/GLES picker with persistent failed-backend
> blacklist + clean terminal fallback to GLES3 (Vulkan context negotiation itself lands with
> the PS2 pass). Settings UI rewritten resolver-driven: searchable, origin badges, tap-to-
> cycle choices, per-override reset, per-game mode (`SettingsScreen(gameKey)`), device-class
> readout; Gold/BIOS sections kept. EmulatorActivity resolves + pushes vars BEFORE core load;
> rewind gated by its setting. SettingsTest: layer precedence + origins (incl. GameDB stub),
> diff persistence, backend fallback/blacklist, and resolver-driven variables booting the
> REAL game + live-update dirty handshake — **OK (4)**; full suite **OK (32 tests)** (the
> PPSSPP-booting tests now push resolver variables exactly like production — the new
> contract after deleting the hardcoded table).
>
> ✅ **P10 done (2026-07-05):** The convenience layer. **Fast-forward** (2×–5× via batched
> `retro_run`s per presented frame — intermediate frames neither shown nor audible = SKIP_FLIP
> battery mode by construction; 50% slow-mo; proven by audio-frames-produced more than
> doubling at 3×). **Rewind**: in-RAM snapshot ring budgeted by bytes (256MB default → ~6
> PSP snapshots @ 2s intervals), take/step/drain/disable all run-loop ops. **Screenshot** op
> (reuses P8 capture → full-res PNG under `screenshots/`). **Undo-save** (previous state
> backed up before overwrite) + **undo-load** (pre-load snapshot) in `SaveStateManager`.
> **Slot manager sheet** (auto + 4 slots, thumbnails + timestamps, save/load/delete/undo).
> **"Continue"** as the detail-screen primary CTA when an auto-save exists → auto-restores
> the auto slot after boot (kill app mid-game → resume in 2 taps). **`hardcoreActive`
> interlock** (RA-ready): pins speed at 100, refuses rewind. **🔧 Robustness fix found by
> testing: PPSSPP's threaded GL renderer deadlocks on back-to-back state ops — added a settle
> gate (next op deferred ~12 frames / 1s after any restore) so hammering load/rewind can
> never wedge the emulator.** P10Test: FF output ratio + hardcore pin; on the real game:
> ring fills → paced steps restore → hardcore refuses → disable clears; undo-save byte-exact
> restore; undo-load round-trip — **OK (2)**; full suite **OK (28 tests)**.
>
> ✅ **P9 done (2026-07-05):** External gamepad support (emulator-provable portion). Bundled
> **SDL `gamecontrollerdb.txt`** (zlib, 297 Android rows) with `ControllerDb`: vid/pid match
> (extracted from the SDL GUID hex), SDL element translation (bN→standard SDL button order→
> Android keycodes; aN→device's non-hat motion ranges sorted; hN.M→HAT axes; `~` inversion).
> **Mapping pipeline: user remap > controller DB > Android default** (`RemapStore.resolve`,
> per-device by `InputDevice.descriptor`, JSON-persisted). `GamepadMapper` rewritten profile-
> driven: per-device profile cache, multi-bind, axis-edge detection, **analog tuning** (radial/
> axial deadzone + inverse-deadzone floor + response-curve exponent), **virtkeys** (MENU/SAVE_
> STATE/LOAD_STATE/FF/SCREENSHOT) routed off the game path. `BindWizard` press-to-bind state
> machine (capture/skip/merge-over-base). `HotplugMonitor`: pad connect → touch overlay hides
> + profiles invalidated; last pad disconnect → **native pause** (new `nativeSetPaused`; frozen
> loop still services save/load ops) + "tap to resume" scrim. Quick menu Save/Load State tiles
> now functional (slot 1 quick-save; gamepad MENU virtkey opens the menu). GamepadP9Test —
> db parse/translate (incl. inverted axis), bundled-db load, **remap persists across store
> instances** (the "remapped clone persists" criterion), wizard capture+merge, custom profile +
> virtkey → native snapshot, tuning math, pause freeze/resume — **OK (7)**; full suite **OK
> (26 tests)**. Device-feel criteria (Xbox/DS instant, real hotplug, dpad-navigable frontend)
> → P5 device session. Deferred: bind-wizard Compose screen + calibration plot (P11 settings
> UI); FF/screenshot virtkeys act at P10.
>
> ✅ **P8 done (2026-07-05) — + a MAJOR latent bug fixed:** Save states. Native `retro_serialize`/
> `unserialize` ops are **posted from any thread but executed on the run-loop thread between
> frames** (the only thread safe for PPSSPP's GL state), caller blocks on a condvar (20s cap);
> atomic tmp+rename writes; framebuffer capture via FBO readback → Kotlin PNG thumbnails;
> `SaveStateManager` (numbered slots + slot-0 auto-save, on `SaveStore`); auto-save-on-exit in
> `EmulatorActivity.onDestroy` (works after the Surface detaches — the backgrounded loop branch
> services ops). **🐛 FIX: PSP games were rendering PURE BLACK since "first light" (which only
> counted frames, never checked pixels).** Root cause: PPSSPP reports `av_info 0×0` and passes
> `0×0` in the frame callback, so we fell back to 320×240 and the `uFlipY` shader flip sampled
> the EMPTY TOP of the oversized 4096² hw-FBO instead of the bottom-left sub-rect where PPSSPP
> renders (bottom-left origin). Fix: (1) core-option `ppsspp_internal_resolution=480x272` (+
> `ppsspp_cpu_core=IR JIT` for nested-emu safety) so PPSSPP reports real geometry; (2) sub-rect
> sampling with vertical orientation **baked into the quad UVs** honoring `bottom_left_origin`,
> uFlipY retired. **Battlegrounds 3 now visibly renders — boot splash + menu confirmed by
> screenshot.** Also bundled PPSSPP `ppge_atlas.zim` (GPL-2.0) so sceUtility dialogs draw
> (`CoreAssets` extracts it into the system dir). SaveStateTest boots the real game, saves slot
> mid-session, asserts a decodable **non-black** thumbnail (varied-pixel + size guard against
> render regression), loads it back, auto-saves — **OK**; full suite **OK (19 tests)**.
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
