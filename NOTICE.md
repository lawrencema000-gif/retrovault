# NOTICE — Third-party components and provenance

Pulsar is free software licensed under the **GNU GPL v3** (see `LICENSE`). It builds upon and
gratefully credits the following projects. Where GPL code or data is incorporated, the combined
work is distributed under GPLv3 with changes stated in the git history of this repository.

## Emulator cores (built **unmodified** from pinned source — see `.github/workflows/build-cores.yml`)

| Core | Upstream | License | Pinned revision |
|---|---|---|---|
| ppsspp_libretro (PSP) | https://github.com/hrydgard/ppsspp | GPL-2.0-or-later | `v1.20.4` (`fa50bb1976065c4f8b1b47af227d367fe9771555`) |
| swanstation_libretro (PS1) | https://github.com/libretro/swanstation | GPL-3.0 | `f901022198dacf125d43331c6540492441ab415b` (main, 2026-06-29 — upstream has no tags) |
| PS2 core | ARMSX2 lineage (evaluation at P25) | GPL-3.0 | _to be pinned at P25_ |

> Cores and GPL data files are built from / copied byte-identical from the pinned upstream
> revision with **no modifications**; the build workflow above is the "scripts to control
> compilation" part of their Corresponding Source (GPLv3 §1). The CI-built `ppsspp_libretro`
> core additionally compiles in libchdr (BSD-3-Clause) for CHD disc-image support, plus
> PPSSPP's other vendored `ext/` components, all from the pinned unmodified upstream tree;
> none of these are part of `libpulsar_retro.so`. The CI-built `swanstation_libretro` core
> vendors its own deps (`dep/`: libchdr, glad, vixl, vulkan-loader, libretro-common, …) and
> embeds **OpenBIOS** (MIT, © 2019 PCSX-Redux authors — `dep/openbios/`) as a built-in PS1
> BIOS fallback; its per-game compatibility settings are compiled-in GPLv3 code derived from
> pre-relicense DuckStation (no external gamedb file exists).

## Libraries and headers

| Component | Source | License | Use |
|---|---|---|---|
| libretro.h | https://github.com/libretro/libretro-common | MIT (© 2010-2024 The RetroArch team) | Core API boundary (`core-emulator/src/main/cpp/libretro.h`) |
| rcheevos v11.6.0 | https://github.com/RetroAchievements/rcheevos @ `3106e6d3d274b63c59976cb07dda94a292ab45ca` | MIT (© 2018 RetroAchievements.org) | rc_client runtime, rhash, rc_libretro — vendored pristine at `core-emulator/src/main/cpp/rcheevos/` (see its `PROVENANCE.md`), compiled into `libpulsar_retro.so` |
| AGDK Frame Pacing (Swappy) | androidx.games:games-frame-pacing `2.1.2` | Apache-2.0 | Frame pacing around GL present (`video_gl.cpp` / host) |
| Oboe | com.google.oboe:oboe `1.9.3` | Apache-2.0 | Low-latency audio output (`audio_out.cpp`) |
| AMD FidelityFX FSR 1.0 — RCAS | https://github.com/GPUOpen-Effects/FidelityFX-FSR (`ffx_fsr1.h`) | MIT (© 2021 Advanced Micro Devices, Inc.) | `kFragFsrSharpen` in `video_gl.cpp` is a GLSL ES 1.00 port of the RCAS sharpening pass |
| sharp-bilinear | Themaister (Hans-Kristian Arntzen), via libretro/slang-shaders `interpolation/shaders/sharp-bilinear.slang` | Public domain (upstream declaration) | `kFragSharpBilinear` in `video_gl.cpp` reimplements the algorithm |
| libretro test cores | https://github.com/libretro/libretro-samples @ `bce193bc` | MIT-style sample code | CI-built validation cores (never shipped to users) |

## Application dependencies (Maven; both flavors unless noted)

| Component | License |
|---|---|
| Kotlin stdlib · kotlinx-coroutines 1.9.0 · kotlinx-serialization 1.7.3 | Apache-2.0 |
| AndroidX (core-ktx, lifecycle, activity-compose, navigation-compose, work-runtime, security-crypto) + Jetpack Compose (BOM 2024.12.01: ui, material3, material-icons-extended) | Apache-2.0 |
| Coil 2.7.0 (io.coil-kt) | Apache-2.0 |
| OkHttp 4.12.0 + Okio (transitive) | Apache-2.0 |
| Sentry Android SDK — **`full` flavor only, opt-in, inert without a DSN** | MIT (the SDK; the hosted sentry.io service is proprietary) |
| Google Play Billing 7.1.1 — **`full` flavor only** | Proprietary (Google Play Billing SDK terms) |
| Google Mobile Ads (AdMob) 23.6.0 + User Messaging Platform 3.1.0 — **`full` flavor only** | Proprietary (Google Mobile Ads SDK terms) |

The `foss` flavor contains **none** of the proprietary rows — enforced at build time by the
`:app:verifyFoss*RuntimeClasspath` gates.

## Bundled runtime assets

| Asset | Source | License | Where |
| --- | --- | --- | --- |
| PPSSPP `ppge_atlas.zim` | https://github.com/hrydgard/ppsspp `v1.20.4` (`assets/ppge_atlas.zim`, sha256 `3ab7fcf6956d0281ada94d501fc6d1cfa8fc034f139e25fed6c829f723d92a95`) | GPL-2.0-or-later | `app/…/assets/coresystem/PPSSPP/`; extracted into the core system dir at session start (`CoreAssets.kt`) — the HLE OSD/dialog font atlas required for sceUtility dialogs to render |
| `gamecontrollerdb.txt` | https://github.com/mdqinc/SDL_GameControllerDB (master, 2026-07-05, sha256 `0229ce25…8cceb2`) | zlib | `core-input/…/assets/`; per-model controller auto-mapping (`ControllerDb.kt`) matched by vendor/product id |
| PPSSPP `compat.ini` | https://github.com/hrydgard/ppsspp `v1.20.4` (`assets/compat.ini`, sha256 `e0db159a…589da1`) | GPL-2.0-or-later | `app/…/assets/coresystem/PPSSPP/` (extracted at session start — the core applies its per-serial compat flags natively) AND parsed by `tools/gamedb-import` into the GameDB snapshot (`assets/gamedb/snapshot.json` + Supabase `gamedb_entries`) |

## Hosted homebrew catalog (redistribution license verified per title)

Pulsar hosts only games whose license clearly permits third-party redistribution of the
binary + assets. Each title's license was verified at its canonical source (not merely
because another store hosts it). Titles with mixed/unclear asset licensing are NOT hosted.

| Title | Author | License | Source (evidence) |
| --- | --- | --- | --- |
| Battlegrounds 3 | Alex Wickes (xfacter) | GPL-3.0 (code) — **audio provenance under review** | https://github.com/xfacter/battlegrounds3 |
| rRootage | Kenta Cho / Marcus R. Brown (PSP port) | BSD-2-Clause | https://github.com/PSP-Archive/rRootage-PSP |
| Suicide Barbie | The Black Lotus | MIT (code) + CC-BY-SA-4.0 (assets) | https://github.com/theblacklotus/suicide-barbie |

> **Note on Battlegrounds 3:** license review (2026-07-06) found the game's sound effects may
> derive from a commercial SFX library (sounddogs.com) that the author's blanket CC-BY-SA
> asset grant may not cover — a possible chain-of-title defect. **Decision (2026-07-06): keep
> published for now** — it has been on PPSSPP's official store for years and the concern is
> credible but unconfirmed. To revisit before public launch (confirm SFX rights with the
> author, or migrate the anchor to rRootage, which is cleanly BSD-2).

## Bundled fonts

| Font | Source | License | Where |
| --- | --- | --- | --- |
| Chakra Petch (Medium / SemiBold / Bold) | Cadson Demak — https://fonts.google.com/specimen/Chakra+Petch | SIL OFL-1.1 | `core-ui/src/main/res/font/chakra_petch_*.ttf` — display/title typeface (`Type.kt`) |
| Manrope (Regular / Medium / SemiBold / Bold / ExtraBold) | Mikhail Sharanda — https://fonts.google.com/specimen/Manrope | SIL OFL-1.1 | `core-ui/src/main/res/font/manrope_*.ttf` — body typeface (`Type.kt`) |

## Design references (no code copied)

- Delta (AGPL — schema/UX vocabulary reimplemented, never copied)
- DraStic (proprietary — UX behavior reference only)

## Trademarks

"PlayStation", "PSP", "PS1", "PS2" are trademarks of Sony Interactive Entertainment.
Pulsar is not affiliated with or endorsed by Sony. Pulsar ships no Sony-owned code, BIOS,
firmware, artwork, or button glyphs.
