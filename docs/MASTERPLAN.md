# Pulsar — Master Plan

> The definitive, step-by-step execution plan. Synthesized 2026-07-02 from a deep research pass:
> a full inventory of the PPSSPP codebase (cloned + mapped file-by-file), touch-control UX across
> Delta/DraStic/Dolphin/RetroArch, compatibility-DB architecture (compat.ini / GameIndex.yaml /
> SwanStation gamedb), the modern frontend feature checklist, and Android performance engineering.
>
> **How to use:** we execute one step (P1…P27) per working session, in order. Each step has binary
> acceptance criteria. Steps marked **[DEVICE]** need the user's phone; **[ACCOUNTS]** need external
> accounts. Progress is tracked here and in `STAGES.md`.

## Vision

Pulsar is the first fully-legal, fully-polished PlayStation-heritage emulator platform for
Android: a GPLv3 app pairing best-in-class PSP/PS1/PS2 emulation (libretro cores built from
source: PPSSPP → SwanStation → ARMSX2-lineage) with a legitimate storefront of only
redistributable homebrew/freeware plus friction-free import of the user's own dumps and BIOS.

It wins on three axes where igpsp-style clones cheat or cut corners:

1. **Input feel** — Delta/DraStic-grade touch controls (slide-off gliding, floating analog, rich
   haptics, WYSIWYG layout editor) on a raw-View-over-SurfaceView zero-jank input path.
2. **It-just-works compatibility** — a self-improving GameDB merged from GPL upstreams
   (PPSSPP `compat.ini`, PCSX2 `GameIndex.yaml`, SwanStation gamedb) layered with device-class
   overrides and community in-app reports through Supabase.
3. **Android platform citizenship** — Swappy frame pacing, Oboe low-latency audio with dynamic
   rate control, 16KB-page-compliant cores, ADPF thermal adaptation, TV/foldable support.

Sequencing is ruthless: **PSP reaches genuinely playable-and-polished FIRST**, then PS1, then PS2
(device-gated). The legal boundary is absolute at every step.

## Guiding principles

1. **Legal boundary is non-negotiable**: GPLv3 with published source; host only verifiably
   redistributable games; user imports own ROMs/BIOS via SAF; no Sony trademarks or button-glyph
   copies; no CC-BY-NC-ND data (post-Sept-2024 DuckStation gamedb); no IGDB as default; no
   MANAGE_EXTERNAL_STORAGE.
2. **PSP first, to POLISHED, before a single PS1 task starts.** A narrow system done superbly
   beats three done adequately. PS2 hard-gated by DeviceCapabilities.
3. **Reuse GPL aggressively, comply properly**: PPSSPP (GPLv2+), RetroArch (GPLv3), Dolphin
   (GPLv2+), PCSX2 (GPLv3), SwanStation (GPL) are importable into GPLv3 Pulsar with attribution,
   preserved headers, stated changes, and a NOTICE file. Delta (AGPL) and DraStic (closed) are
   design references only — reimplement, never copy.
4. **The input hot path is sacred**: raw `View.onTouchEvent` over SurfaceView with
   `requestUnbufferedDispatch` and a lock-free atomic input snapshot polled by the emu thread.
   Compose is for menus/editor chrome only — never between finger and core.
5. **Foundation-four before any polish**: SurfaceView+Vulkan(FIFO, pre-rotation)+Swappy pacing;
   Oboe LowLatency/Exclusive; libretro dynamic rate control; JIT-safe/16KB-page-compliant cores
   built from pinned source. These ARE the "smooth emulator".
6. **Defaults must feel right out of the box** (the Delta/DraStic lesson): tuned per-device-class
   touch layouts, per-game settings applied silently from the GameDB, one-tap resume.
7. **One layered settings model**: app defaults → shipped GameDB → device-class overlay → user
   per-game diff; stored diff-only; UI shows which layer set each value.
8. **Design data schemas once, day one**: canonical serial (LETTERS-DIGITS) keys everything; one
   touch-layout JSON schema serves runtime, editor, and future skins; GameDB `schema_version`
   decoupled from app version.
9. **device_class (SoC/GPU × driver × backend) is a first-class data dimension** — compat reports
   collect the classifying fields so the system self-improves.
10. **One working session per step**, binary acceptance criteria, device/account steps marked and
    batched to respect the user's time.
11. **Ship compliance as code**: CI gates for 16KB alignment (`llvm-readelf` on every `.so`),
    license/NOTICE generation, RA hardcore single-source-of-truth flag built early.
12. **Monetization never taints the core**: ads only in frontend chrome (never in-game); Gold adds
    convenience — never gates emulation accuracy, compat data, or imported-ROM functionality.

---

## The steps

### Milestone A — The smooth emulator (PSP foundation-four)

**P1. NDK enablement + core build pipeline (16KB-compliant)**
Turn the C++ libretro host skeleton into a real NDK build; build `ppsspp_libretro` from pinned
source with `-Wl,-z,max-page-size=16384`; CI job runs `llvm-readelf -l` on every `.so` asserting
16KB LOAD alignment; dynamic core loading (dlopen + retro lifecycle) works.
*Accept:* core builds from source, passes the 16KB check, `LibretroBridge` dlopens it and reads
`retro_get_system_info` on an emulator image (incl. the 16KB image).

**P2. Video path: SurfaceView + Vulkan/GL host render + Swappy**
libretro HW-render context (GLES3 first, Vulkan negotiation second); Vulkan swapchain best
practice (FIFO, minImageCount 3, pre-rotation, OUT_OF_DATE recreation); AGDK Frame Pacing
(Swappy) around present; `Surface.setFrameRate` API 30+; survive surface destroy/recreate.
*Accept:* stable paced 60fps render, rotation doesn't corrupt, logged frame-times show pacing.

**P3. Audio path: Oboe + dynamic rate control**
Oboe LowLatency/Exclusive stream, 2-burst buffer, lock-free ring buffer from
`retro_audio_sample_batch`, ±0.5% sinc-resampler rate nudging from buffer occupancy,
Bluetooth-friendly buffer toggle, device-change restart.
*Accept:* 10 min glitch-free audio; rate delta within ±0.5%.

**P4. Input hot path: raw overlay View + atomic input state + basic gamepad**
`TouchOverlayView` (plain View) over the SurfaceView; `requestUnbufferedDispatch`; pointer-ID
bitmask tracking into a lock-free packed-atomic snapshot read on the emu thread; gamepad
KeyEvent/MotionEvent into the same snapshot; retire the Compose gamepad from the input path;
input→frame latency instrumentation.
*Accept:* simultaneous d-pad + 2 face + shoulder never drops; pad drives the core; no
main-thread hop.

**P5. First light: PSP homebrew boots end-to-end [DEVICE]**
Library → download → EmulatorActivity(:emu) → play, on real hardware. Fix JIT execmem in the
:emu process, driver quirks, SAF fd passing. Verify JIT (not interpreter) and record baseline
metrics (pacing, underruns, latency, memory). Also smoke-test the 16KB emulator image.
*Accept:* cold start → playing a store homebrew in <10s; 15-min session no crash.

### Milestone B — PSP to polished

**P6. Library identification: serial extraction, embedded art, fast scanning**
Port PPSSPP `ParamSFO.cpp` (DISC_ID/title from ISO/CSO/CHD) + `GenerateFakeID` for homebrew;
extract ICON0/PIC1 as legally-clean art; canonical serial normalization; Room library index;
incremental SAF rescans via DocumentsContract; boot-module CRC at scan; libchdr (BSD-3) for CHD.
*Accept:* a mixed folder yields a correct grid with official icons in seconds, keyed by serial.

**P7. Touch controls v1: the feel layer**
8-way d-pad as one control (9 zones, hysteresis); floating auto-centering analog; slide-off /
gliding options; hitboxes decoupled from visuals (extendedEdges, region modifiers 1.0–2.0,
grow-when-pressed 1.5×); haptics press+release primitives w/ intensity (off-hot-path thread);
pressed-brighten; cutout-aware per-device-class defaults.
*Accept:* side-by-side vs PPSSPP on-device, feel is equal or better; defaults need zero tweaks.

**P8. Touch layout editor + layout JSON schema**
`.pulsarlayout` schema (mappingSize logical coords; per-control position/scale/visibility/
extendedEdges/opacity; scoped {system, gameId?, orientation}); WYSIWYG editor on the live render
layer (drag, pinch-scale, snap grid, alignment guides, cutout drawn, reset); Save Global / For
This Game; portrait + landscape separate; reachable from settings and pause menu.
*Accept:* rearrange/resize/hide, save per-game + global, edits render exactly in gameplay.

**P9. External gamepad: full support**
Press-to-bind wizard (multi-bind, per-device by descriptor); analog tuning (deadzone, inverse
deadzone, curves, axial); live calibration plot; hotplug (connect→overlay hides; disconnect→
auto-pause); emulator virtkeys bindable (save/load/FF/rewind/screenshot/menu); ship
`gamecontrollerdb.txt` + SDL-GUID auto-mapping; gamepad-navigable frontend.
*Accept:* Xbox/DS pad instant; remapped clone persists; unplug pauses; UI drivable by d-pad.

**P10. Save states, rewind, fast-forward, auto-resume**
Slot manager (5 default/30 max + auto-slot, thumbnails + timestamps); undo save AND undo load;
quick save/load hotkeys + slot cycling; auto-save on background/exit + "Continue" as primary
library action; fast-forward hold AND toggle (2×/3×/5×/∞) with SKIP_FLIP battery mode; slow-mo;
rewind (interval snapshots, RAM budget label); central `hardcoreActive` interlock flag (RA-ready).
*Accept:* round-trip with thumbnails; kill app mid-game → resume in 2 taps at the same frame.

**P11. Settings framework + full PSP settings UI**
4-layer resolution engine (defaults → GameDB → device-class → user diff) with origin badges;
complete Video/Audio/Emulation/Controls/System screens (searchable, per-game entry points);
core options mapped to typed settings; device-class detection (Adreno/Mali/Xclipse/PowerVR);
Vulkan/GLES picker with automatic fallback + failed-backend blacklist.
*Accept:* per-game override wins + shows its badge; forced Vulkan failure falls back cleanly.

**P12. Compatibility GameDB: import pipeline + Supabase schema + snapshot**
Node/TS importer for pinned `compat.ini` (72 flag sections) → unified settings jsonb;
Supabase tables (game_serials, gamedb_entries, device_profiles, compat_reports,
recommended_settings, compat_summary) with RLS; scheduled Edge Function → versioned CDN snapshot
+ baked APK snapshot; flags applied silently at load w/ escape hatch; NOTICE entries.
*Accept:* a known-fussy serial auto-gets its flag; snapshot regenerates end-to-end.

**P13. In-app compat reporting + game-page compat surfacing**
Post-session prompt (≥10 min, once per user+serial+version): 5-tier rating + sub-scores + note;
auto-filled SoC/GPU/driver/backend/settings-diff/CRC; insert-only RLS; game pages show
device-class-scoped tier badge + "Works best with" one-tap settings; public compat web page.
*Accept:* prompt fires exactly once; report lands with full metadata; badge updates after
aggregation.

**P14. Cheats: CWCheat UI + cheat.db import**
Surface the core's CWCheat engine; import community cheat.db via SAF/URL (never bundled — legal
boundary); per-game toggle list w/ search; cheats clear `hardcoreActive`.
*Accept:* imported DB lists cheats for a test title; toggling applies live; nothing ships in APK.

**P15. Store v1: legal catalog + R2/CDN delivery + install pipeline [ACCOUNTS]**
index.json-compatible catalog from Supabase (PPSSPP `Store.cpp` contract: author, license,
websiteURL, contentRating, size, sha256); curate 15–30 verified-redistributable PSP homebrew
(PPSSPP store catalog as lead list, per-title license evidence recorded, author outreach where
ambiguous); zips on R2 (or Supabase Storage v1) via existing signed-URL function; download →
verify → unzip → library; store UI shows license/author prominently.
*Needs:* Cloudflare R2 account (or approval to stay on Supabase Storage).
*Accept:* fresh install browses, downloads a real homebrew with progress, plays it; sha256
mismatch aborts.

**P16. Auth UI + cloud saves with versioning and conflict UX [DEVICE]**
Sign-in UI (email + OAuth; anonymous-first); manifest-based 3-way conflict detection (RetroArch
model) — never clobber; conflict sheet (Keep device / cloud / both); memstick saves always sync,
save-states opt-in; version history w/ restore (Gold: extended depth).
*Accept:* two devices editing the same save produce the conflict sheet, not silent loss.

**P17. Onboarding, empty states, accessibility, platform baseline**
3-step skippable first-run (legal explainer → SAF folder pick → controller test); teaching empty
states (incl. missing-BIOS); edge-to-edge + predictive back (back → pause menu); a11y baseline
(TalkBack, focus order, 48dp, 200% text, 4.5:1); per-app language; OLED-black theme option.
*Accept:* new user reaches gameplay <2 min unaided; Accessibility Scanner clean.

**P18. Performance & thermal polish: ADPF, Game Mode, overlays [DEVICE]**
Activity config audit (sensorLandscape, cutout ALWAYS, gesture-exclusion rects, :emu reclaim);
ADPF `getThermalHeadroom` → step-down policy (resolution → shaders → frameskip) behind a toggle;
PerformanceHintManager around retro_run; Game Mode API; FPS/frametime/audio overlay; on-device
profiling session.
*Accept:* 30-min session holds fps with visible quality stepping instead of stutter.

**P19. Display polish: post-shaders, display layout, texture enhancement**
Ship PPSSPP shader pack (FXAA, CRT/scanlines, FSR 1.0, sharp-bilinear, …) + defaultshaders.ini
format, multi-shader stacking with uniform sliders in the host present pass; display layout
editor (drag/scale, aspect presets, integer scale, per-orientation, internal rotation for
vertical shmups); anisotropic/xBRZ texture options; screenshots → MediaStore + share sheet.
*Accept:* CRT+FSR stack renders w/ adjustable uniforms; vertical shmup plays rotated; screenshots
in gallery.

**P20. RetroAchievements (hardcore-compliant) [ACCOUNTS]**
rcheevos `rc_client` (MIT): memory-read callback, OkHttp dispatcher, Keystore token, stable UA;
achievement state serialized into save states; per-game list + unlock toasts + session summary;
hardcore ON by default where enabled — blocks state-load/rewind/slow-mo/cheats via the P10 flag;
publish privacy policy + monetization disclosure; apply for RA approval (6-month public clock).
*Needs:* RA account.
*Accept:* real unlock in softcore + hardcore; hardcore verifiably blocks the banned features.

**P21. Monetization: AdMob + Pulsar Gold (Play Billing) [ACCOUNTS]**
Play Billing "Pulsar Gold" (remove ads, extended cloud history, themes; server-verified
entitlement); AdMob banner/native in library/store chrome ONLY (never in-game, no exit
interstitials); UMP consent; `foss` flavor with ads+billing stripped (built now to keep GPL
distribution clean); free tier fully functional.
*Needs:* Play Console + merchant, AdMob account.
*Accept:* test purchase survives reinstall; foss flavor builds with zero proprietary deps.

**P22. PSP release hardening + distribution (1.0) [DEVICE+ACCOUNTS]**
QA matrix (10–15 titles across compat tiers + user's own dumps); GPL compliance audit (NOTICE,
licenses screen, stated changes); Play listing (data safety, content rating, target SDK, 16KB
re-verify); F-Droid metadata + reproducible foss build + RFP; direct APK on GitHub Releases with
update-check; crash reporting (Sentry) + opt-in analytics.
*Needs:* Play Console ($25), signing ceremony, ~2h device QA.
*Accept:* Play internal-track approved; 1.0 tag cut.

### Milestone C — PS1

**P23. PS1 bring-up: SwanStation core + serials + gamedb [DEVICE]**
SwanStation in the core pipeline (pinned, 16KB, CI); SYSTEM.CNF BOOT-line serial parser +
PSX.EXE hash fallback; multi-disc + .cue/.chd/.pbp scanning; PS1 BIOS flow (hash-detected,
per-region); import SwanStation gamedb (GPL — never post-2024 DuckStation); memory-card UI.
*Needs:* user's PS1 BIOS + test dump.
*Accept:* PS1 homebrew boots with correct serial, BIOS by hash, gamedb applied.

**P24. PS1 polish: settings, dual-analog touch, cheats, QA [DEVICE]**
PS1 settings (renderer, resolution, PGXP, true color, widescreen, deinterlacing); digital +
DualShock dual-stick touch layouts driven by gamedb controller info; GameShark/AR paste-any-format
(Omniconvert algorithms, GPL; import-only); compat reporting + store extended to PS1; device QA.
*Accept:* PS1 title full-speed with PGXP; pasted GameShark code applies.

### Milestone D — PS2 + big screen

**P25. PS2 bring-up: ARMSX2-lineage core + gating + GameIndex [DEVICE]**
Evaluate/build the PS2 core (16KB dynarec audit is CRITICAL — budget real time); harden the
device gate (6GB / SD845-class+ / Vulkan 1.1, honest messaging); import PCSX2 GameIndex.yaml +
NetherSX2 mobile deltas through the mobile-capability filter; PS2 serials (BOOT2 + ELF CRC);
libadrenotools Turnip loader (Adreno power users); SoundTouch time-stretch FF audio (all systems).
*Needs:* PS2-capable device, PS2 BIOS, test dump.
*Accept:* light PS2 title in-game on gated hardware; unsupported devices see the gate.

**P26. PS2 polish + big-screen: settings, TV, foldables, QA (2.0) [DEVICE]**
PS2 settings (EE cycle rate/skip, blending accuracy, widescreen patches); dual-analog defaults +
multi-controller ports; Android TV build (leanback, banner, 10-foot focus polish); foldable/tablet
two-pane + tabletop mode; full-matrix regression; 2.0 through all channels.
*Accept:* PS2 plays acceptably w/ thermal stepping; app fully TV-navigable; fold transitions clean.

### Milestone E — Community & delight

**P27. Skins, widgets, gestures, netplay evaluation**
`.pulsarskin` v1 (P8 schema + asset pack) with import/export + curated gallery; Glance
"Continue playing" widget + dynamic shortcuts + optional PiP; gesture mapping page + combo/custom
buttons (toggle+turbo, PPSSPP CustomButton model); PSP netplay (adhoc/infra) go/no-go doc;
playtime + collections; ScreenScraper opt-in scraper for imported PS1/PS2 art (written commercial
approval FIRST).
*Accept:* skin round-trips across devices; widget resumes in one tap; combo button works.

---

## Settings catalog (the complete spec)

Scopes: **global** · **per-system** · **per-game** (per-game always wins; origin badges shown).

### Video
Backend (Vulkan/GLES + auto-fallback + blacklist) · GPU driver/Turnip (Adreno) · Internal
resolution (Auto/1×–8×; PS2 1×–4×) · HW-scaler display resolution · Aspect ratio
(Original/4:3/16:9/Stretch/Custom) · Integer scaling · Display filter (Linear/Nearest) · Screen
position/scale editor (per-orientation) · Internal rotation (vertical shmups) · Crop-to-16:9 /
cutout insets · Frame skipping (Off/1–8) + Auto · Render duplicate frames (30fps smoothing) ·
Buffered command frames (1/2/3) · VSync/low-latency present · Texture filtering (Auto/Nearest/
Linear/AutoMax + Smart-2D) · Anisotropic (Off–16×) · Texture upscaling (xBRZ family + GPU path) ·
Skip buffer effects · Skip GPU readbacks · Lower-res effects (Off/Safe/Balanced/Aggressive) ·
Spline/Bezier quality · PGXP (PS1) · True color (PS1) · Widescreen hack (PS1/PS2) ·
Post-shader chain (ordered, per-shader uniforms) · FPS/battery overlay · Thermal auto-adjust.

### Audio
Enable sound · Game volume · Fast-forward volume · Achievement volume · UI sounds · Reverb (PSP
SAS) · Latency mode (Low/Safe/Bluetooth-friendly, auto-BT detect) · Auto-switch device ·
FF audio (Time-stretch/Pitch/Mute) · Mix with other apps.

### Emulation
CPU core (JIT/IR/Interpreter + auto-fallback) · Fast memory · Emulated CPU clock · I/O timing ·
Cache full disc in RAM · Ignore bad memory access · EE cycle rate/skip (PS2) · Rounding/clamping
(PS2, GameDB-driven, Advanced) · Apply GameDB settings (escape hatch) · PSP model ·
Language/region/confirm-button · Memory Stick size · Rewind (interval + RAM estimate) ·
FF speeds (2×/3×/5×/∞/custom) · Slow-motion %.

### Controls: Touch
Show touch controls (auto-hide on gamepad) · Layout editor (per-game/orientation, snap grid) ·
Button style · Opacity · Auto-hide idle · Haptics (press/release + intensity) · Button gliding ·
Sticky d-pad · 4-way mode · D-pad region modifier (1.0–2.0) · Action region modifier ·
Grow-hitbox-while-pressed · Floating analog · Deadzone/saturation · Second analog (PS1/PS2) ·
Custom combo buttons (≤20: bitmask + toggle + turbo + shape) · Utility buttons (FF/save/load/
screenshot/menu) · Gesture mapping (swipes, double-tap, analog-gesture) · Turbo period/duty.

### Controls: Gamepad
Press-to-bind wizard (multi-bind, per-device) · Emulator-function bindings · Deadzone/inverse
deadzone/sensitivity · Response curve (Linear/Aggressive/Relaxed/Wide) · Deadzone shape + axial ·
Analog→digital thresholds · Calibration screen (live plot + drift suggestion) · Hide overlay on
connect · Auto-pause on disconnect · Player ports · Rumble.

### Save States & Saves
Slots (1–30) · Confirm-on-load · Undo save+load · Auto-save on exit · Auto-resume
(Ask/Always/Never) · OSD indicator · Cloud sync memcard (on w/ sign-in) · Cloud sync states
(opt-in) · Version history depth (Gold extended).

### Cheats & Achievements
Enable cheats (kills hardcore) · Import cheat DB (SAF/URL) · Refresh interval · RA account ·
Hardcore mode · Encore/unofficial · Notification position + sound.

### System, Storage & Network
ROM folders (SAF) · BIOS manager (hash-detected per system) · Download location (transparent) ·
Wi-Fi-only downloads · Compat snapshot auto-update · Report prompts (Ask/Always/Never) ·
Export-everything zip · Import/restore · Crash/analytics opt-in.

### App & Interface
Theme (Pulsar Dark/OLED Black/Light/dynamic) · App language · Grid density · Badges · Playtime ·
Reduce motion · Gold status.

### Developer / Advanced
Logging + export · Debug overlay · Ignore compat settings · Custom GameDB URL (self-hosters) ·
Core versions + licenses screen.

---

## Controls spec (condensed — full detail lives in this plan's research)

- **Hot path:** SurfaceView (video) + one transparent raw `View` (touch) + Compose chrome only.
  `requestUnbufferedDispatch`, historical-sample draining, lock-free packed-atomic input snapshot
  (buttons bitmask + 4 axes) read by `retro_input_state` on the emu thread. Haptics on a separate
  Handler thread. Gamepad events write to the same snapshot.
- **Layout model:** `.pulsarlayout` JSON — `mappingSize` logical space (resolution-independent),
  per-control `{position, scale, visible, opacity, extendedEdges, params}`, scoped
  `{system, gameId?, orientation}`, most-specific-first. Future `.pulsarskin` = layout + assets.
- **Feel:** pointer-ID multi-touch bitmasks; slide-off releases/presses across buttons (gliding
  optional); 8-way d-pad with 9 angle zones + hysteresis; floating auto-centering analog with
  deadzone/saturation; hitboxes = visual + extendedEdges, region modifiers, grow-when-pressed;
  press/release haptic primitives with amplitude fallback; pressed-brighten; always-visible menu
  button; combo buttons with toggle+turbo counted in emulated frames.
- **Editor:** WYSIWYG on the live render layer; snap grid; alignment guides; cutout drawn;
  Save Global / For This Game; portrait + landscape separate.
- **Gamepads:** instant defaults for standard pads; SDL-GUID matching against
  `gamecontrollerdb.txt`; press-to-bind wizard; per-device profiles; hotplug auto-hide/pause;
  full frontend d-pad navigability.
- **System integration:** sticky immersive; gesture-exclusion rects for edge buttons; cutout mode
  ALWAYS; predictive back → pause menu; latency budget: touch→core-sample < 1 frame @60Hz.

## Compatibility system (condensed)

- **Canonical serial** keys everything: PSP `PARAM.SFO` DISC_ID (+GenerateFakeID for homebrew);
  PS1/PS2 `SYSTEM.CNF` BOOT/BOOT2 (+PSX.EXE hash fallback, +boot-ELF CRC for PS2 patches).
- **Sources (license-vetted, commit-pinned):** PPSSPP `compat.ini` (GPL) · SwanStation gamedb
  (GPL; NEVER post-2024 DuckStation CC-BY-NC-ND) · PCSX2 `GameIndex.yaml` (GPL) + NetherSX2
  mobile deltas → mobile-capability filter → unified typed settings jsonb with per-entry
  provenance SHAs. Merged snapshot published GPLv3 with NOTICE.
- **4-layer resolution:** app defaults → GameDB entry → device-class overlay → user per-game diff
  (diff-only docs, never mutated by updates). UI badges each value's origin; escape hatch.
- **Supabase:** `game_serials`, `gamedb_entries` (UNIQUE serial+source), `device_profiles`,
  `compat_reports` (insert-only RLS, auto-filled device metadata), `recommended_settings`,
  `compat_summary` (pg_cron materialized view). Anti-abuse: moderation gate + down-weighting.
- **Snapshot pipeline:** Edge Function → `compat/latest.json` (+versioned) in public Storage;
  baked APK snapshot; ETag refresh, 24h throttle; public compat web page.
- **Reporting UX:** post-session 5-tier prompt (once per user+serial+version) + optional
  sub-scores; game pages show device-class badges + one-tap recommended settings.

## What we reuse from PPSSPP (GPL→GPLv3, with attribution + NOTICE)

The core itself (built from pinned source) · `compat.ini` + flag semantics · `ParamSFO.cpp` +
GenerateFakeID · GameInfoCache icon extraction · `GamepadEmu.cpp` behavior models (MultiTouch
bitmasks, dpad zones, auto-center stick, gliding, gestures, CustomButton) · layout-editor
concepts · `KeyMap`/`ControlMapper` + `gamecontrollerdb.txt` · SaveState slot/undo/rewind
semantics · CWCheat engine + importer logic · the full shader pack + defaultshaders.ini ·
DisplayLayoutConfig · Config.h settings vocabulary + backend-fallback pattern · Reporting flow +
ppsspp-report schema · **Store.cpp index.json contract + GameManager install pipeline + their
store catalog as our curation lead-list** · RetroAchievements rc_client integration reference ·
48-language translation corpus · Vulkan driver-bug knowledge + Turnip loader · Android JIT/W^X
patterns · redump.csv verification concept · adhoc/infra netplay data (P27 evaluation).
**Explicitly NOT reused:** PPSSPP/Gold names+branding; removed upstream settings; Delta code
(AGPL); post-2024 DuckStation anything.

## Top risks

1. **PS2 core maturity** (ARMSX2 young; 16KB dynarec unverified) — P25 starts with an evaluation
   gate; PS2 can slip without hurting 1.0.
2. **16KB page compliance of dynarecs** — CI gate + budgeted fixing time.
3. **Play policy shifts for emulators** — F-Droid + direct APK are first-class from 1.0.
4. **Store catalog licensing is slow** — per-title verification; some titles drop out.
5. **Compat cold-start** — seed from upstream data, label sources transparently.
6. **RA approval takes 6+ months public** — clock starts at 1.0.
7. **Libretro feature ceilings** (netplay, texture replacement) — may force a deeper PPSSPP
   integration decision later.
8. **GPU driver long tail** — device-class overlays + healthy GLES fallback.
9. **Cheat/scraper content gray zones** — import/link only; written ScreenScraper approval first.
10. **GPL/F-Droid vs ads** — strict flavor separation.
11. **Upstream churn** — scheduled importer refresh.
12. **User-account provisioning lag** — device/account steps are marked and batched.
