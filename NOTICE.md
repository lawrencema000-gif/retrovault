# NOTICE — Third-party components and provenance

Pulsar is free software licensed under the **GNU GPL v3** (see `LICENSE`). It builds upon and
gratefully credits the following projects. Where GPL code or data is incorporated, the combined
work is distributed under GPLv3 with changes stated in the git history of this repository.

## Emulator cores (built from pinned source — see `.github/workflows/build-cores.yml`)

| Core | Upstream | License | Pinned revision |
|---|---|---|---|
| ppsspp_libretro (PSP) | https://github.com/hrydgard/ppsspp | GPL-2.0-or-later | `v1.20.4` (`fa50bb1976065c4f8b1b47af227d367fe9771555`) |
| swanstation_libretro (PS1) | https://github.com/libretro/swanstation | GPL-3.0 | _to be pinned at P23_ |
| PS2 core | ARMSX2 lineage (evaluation at P25) | GPL-3.0 | _to be pinned at P25_ |

## Libraries and headers

| Component | Source | License | Use |
|---|---|---|---|
| libretro.h | https://github.com/libretro/libretro-common | MIT | Core API boundary (`core-emulator/src/main/cpp/libretro.h`) |
| AGDK Frame Pacing (Swappy) | androidx.games:games-frame-pacing | Apache-2.0 | Frame pacing around GL present (`video_gl.cpp` / host) |
| Oboe | com.google.oboe:oboe | Apache-2.0 | Low-latency audio output (`audio_out.cpp`) |
| libretro test cores | https://github.com/libretro/libretro-samples @ `bce193bc` | MIT-style sample code | CI-built validation cores (never shipped to users) |

## Bundled runtime assets

| Asset | Source | License | Where |
| --- | --- | --- | --- |
| PPSSPP `ppge_atlas.zim` | https://github.com/hrydgard/ppsspp `v1.20.4` (`assets/ppge_atlas.zim`) | GPL-2.0-or-later | `app/…/assets/coresystem/PPSSPP/`; extracted into the core system dir at session start (`CoreAssets.kt`) — the HLE OSD/dialog font atlas required for sceUtility dialogs to render |
| `gamecontrollerdb.txt` | https://github.com/mdqinc/SDL_GameControllerDB (master, 2026-07-05, sha256 `0229ce25…8cceb2`) | zlib | `core-input/…/assets/`; per-model controller auto-mapping (`ControllerDb.kt`) matched by vendor/product id |

## Data and assets (planned per MASTERPLAN.md; entries added when shipped)

- PPSSPP `assets/compat.ini` (GPL-2.0-or-later) — per-game compatibility flags (P12)
- PPSSPP `assets/shaders/*` + `defaultshaders.ini` (GPL-2.0-or-later) — post-processing chain (P19)
- `gamecontrollerdb.txt` (SDL community DB, zlib-style) — gamepad auto-mapping (P9)
- rcheevos (MIT) — RetroAchievements client (P20)
- AGDK games-frame-pacing "Swappy" (Apache-2.0) — frame pacing (P2)
- google/oboe (Apache-2.0) — low-latency audio (P3)
- libchdr (BSD-3-Clause) — CHD disc-image support (P6)

## Design references (no code copied)

- Delta (AGPL — schema/UX vocabulary reimplemented, never copied)
- DraStic (proprietary — UX behavior reference only)

## Trademarks

"PlayStation", "PSP", "PS1", "PS2" are trademarks of Sony Interactive Entertainment.
Pulsar is not affiliated with or endorsed by Sony. Pulsar ships no Sony-owned code, BIOS,
firmware, artwork, or button glyphs.
