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

## Data and assets (planned per MASTERPLAN.md; entries added when shipped)

- PPSSPP `assets/shaders/*` + `defaultshaders.ini` (GPL-2.0-or-later) — post-processing chain (P19)
- `gamecontrollerdb.txt` (SDL community DB, zlib-style) — gamepad auto-mapping (P9)
- rcheevos v11.6.0 (MIT, © RetroAchievements.org) — rc_client runtime, rhash, rc_libretro;
  vendored at `core-emulator/src/main/cpp/rcheevos/`, compiled into `libpulsar_retro.so` (P20)
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
