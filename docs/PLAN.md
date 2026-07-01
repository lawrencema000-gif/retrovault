# RetroVault — Build Plan

Synthesized from a multi-agent research pass (emulator cores, legal catalog, Google Play
policy, app architecture). Confidence is high on cores/policy/architecture; the catalog size
depends on manual licensing work.

## Product in one line

A fully **open-source (GPLv3)** Android emulator for **PSP → PS1 → PS2** (shipping in that
order) with a **Supabase-backed storefront** of only **legally-redistributable** games
(homebrew, public-domain, freeware, permission-licensed indies) plus a one-tap
**import-your-own-ROM/BIOS** flow.

## The finding that reshapes the brief: the app must be open-source (GPLv3)

Every viable emulator core is copyleft:

| System | Core | License | BIOS |
|---|---|---|---|
| PSP | **PPSSPP** (best-in-class, actively maintained) | GPL-2.0-or-later | **None needed** |
| PS1 | **SwanStation** (primary) · Beetle PSX (accuracy) · PCSX-ReARMed (low-end) | GPLv3 | User-supplied |
| PS2 | **ARMSX2** (only clean, current PCSX2 fork) | GPLv3 | User-supplied |

Because the app links these cores (statically or as libretro `.so`), GPL copyleft reaches the
whole app: it **must be licensed GPLv3, ship complete corresponding source per release, and add
no EULA/DRM restrictions.** A "closed-source, custom-branded" build is **not legally possible**
with these cores — so that requirement is dropped in favor of the **proven PPSSPP model**:
open repo + optional paid "Gold" SKU + ads. libretro has litigated GPL violators (Hyperkin
Retron5), so this is real.

**Cores that are OFF the table:**
- **DuckStation** — relicensed to CC-BY-NC-ND (no commercial, no derivatives, no packaging) and
  Android-abandoned since May 2025. Do not fork/repackage. (Use SwanStation, its last-GPL fork.)
- **AetherSX2 / NetherSX2** — discontinued and GPL-compliance-tainted / non-free. Never bundle.

## Hard legal boundary (never cross)

- ✅ Host/serve: homebrew, public-domain, freeware, redistribution-licensed indie games only.
- ✅ User-imports: their own ROM dumps and their own PS1/PS2 BIOS (local only, never uploaded).
- ❌ Never host, bundle, link, auto-download, or "induce/encourage" access to: copyrighted
  commercial ROMs, or any Sony BIOS/firmware.
- ❌ No console trademarks, logos, box art, or character art in the app, icon, or store listing.

## Architecture (chosen)

- **Single Gradle multi-module** Kotlin + Jetpack Compose app; `libs.versions.toml` + a
  `:build-logic` convention-plugins module.
- **libretro-frontend model**: one C++/JNI facade loads interchangeable `*_libretro_android.so`
  cores → one video/audio/input/save-state contract across all systems, cores swappable
  (mitigates single-maintainer abandonment risk).
- **Storefront/library/settings** = single-Activity Compose navigation. **Each game** launches a
  separate `EmulatorActivity` (`android:process=:emu`) owning a **SurfaceView** (Vulkan-first)
  embedded via `AndroidView`; the `retro_run` loop runs on a dedicated render thread with a
  Compose HUD/touch overlay above it. A native core crash is contained to the `:emu` process.
- **Backend**: Supabase (Postgres catalog + Auth email/Google OAuth + Storage + RLS + Edge
  Functions). Large game payloads on **Cloudflare R2** (zero-egress) via **short-TTL presigned
  URLs**; Supabase Storage only for box art + saves + states (uid-folder RLS).
- **Download/install**: WorkManager foreground worker + OkHttp HTTP Range (resumable) + sha256
  verify → app-scoped storage.
- **Save sync**: save-states (`retro_serialize`, zstd, tagged with `core_version`, warn on
  mismatch — never blind-restore) + memory-card/SRAM (`retro_get_memory_data`) as the durable
  path.
- **Input**: Compose/Canvas touch overlay + Android `InputDevice`/`KeyEvent`/`MotionEvent`
  gamepads converged into one input-state buffer feeding `retro_set_input_state`.

### Modules

`:app` · `:core-emulator` (only native module: CMake/NDK + jniLibs + libretro JNI facade) ·
`:feature-player` · `:feature-store` · `:feature-library` · `:feature-settings` ·
`:core-input` · `:data-supabase` · `:data-download` · `:data-saves` · `:core-ui` ·
`:core-model` · `:build-logic`

### Supabase schema (core tables)

`systems` · `games` (system_id, title, license enum, storage_provider `r2|supabase`,
storage_key, download_size_bytes, sha256, box_art_url, region, version, is_published) ·
`profiles` (= auth.users.id, tier) · `user_library` · `downloads` · `save_states`
(slot, storage_key, core_version, unique(user,game,slot)) · `memory_cards`.
RLS: catalog world-readable where `is_published`; every per-user row `auth.uid() = user_id`.
A signing Edge Function verifies eligibility before issuing a minutes-TTL download URL.

## Legal catalog — three tiers

- **Tier 1 (day one, ~30–60, zero ambiguity):** open-source homebrew on GitHub with an explicit
  GPL/MIT/BSD/zlib/CC `LICENSE` — PSP GameBrew (licensed source), PS1 homebrew repos, PS2
  `ps2dev`/`ps2homebrew` (cleanest of the three). Ship license text + source offer per download.
- **Tier 2 (growth engine, +100–300):** per-developer written redistribution permission, led by
  the active modern PS1 scene (`psxhomebrewgames.itch.io`). Track every grant (title, author,
  date, scope, license, proof URL) in a permissions table.
- **Tier 3 (optional commercial):** license recognizable IP from **Piko Interactive** (owns
  180+ IPs, licenses outward — mostly 8/16-bit, better for a future multi-system expansion).

**Avoid entirely:** all retail PS1/PS2/PSP ISOs (still copyrighted; no public-domain commercial
pool); any fan-game/port using Nintendo/Sega/Capcom/Konami IP (author permission is worthless);
treating "free to download" (itch.io/Internet Archive) as "free to redistribute." Screen every
title for third-party IP before publish. Realistic credible launch ≈ **150–250 clean titles
(~60% PSP / 30% PS1 / 10% PS2)**.

## Distribution & monetization

- **Google Play = primary.** Emulators are allowed (Device & Network Abuse interpreter/VM
  exception; RetroArch/Dolphin are live). The failure mode is the **Intellectual Property**
  policy, not "it's an emulator": no bundled/linked ROMs or BIOS, no in-app ROM-site links, no
  console trademarks. Keep the storefront cleanly separable from the emulator.
- **Complete Google's 2026 developer identity verification** and operate as an identifiable
  business — anonymous APK distribution is closing (verification hits sideloading on certified
  devices from 2026).
- **Secondary reach:** direct/GitHub APK + self-updater, Samsung Galaxy Store, Aptoide. A
  fully-FOSS ads-free variant can ship on **F-Droid**. (Amazon Appstore closed Aug 2025.)
- **Monetization (PPSSPP model):** AdMob free tier + one-time **"RetroVault Gold"** IAP (remove
  ads, extra save-state slots, cloud sync, overlay skins) + optional cloud-storage subscription.
  Google Play Billing everywhere except the US external-billing carve-out (Epic v. Google,
  sunsets Nov 1 2027). **Never gate GPL source behind payment.**

## Phased roadmap

- **Phase 0 — Foundations & legal scaffolding:** multi-module skeleton (13 modules stubbed),
  `:core-model`, Supabase schema + RLS + auth trigger, R2 bucket + `get-download-url` Edge
  Function, supabase-kt wiring, GPLv3 LICENSE + public repo + DMCA process + license-tracking sheet.
- **Phase 1 — PSP MVP (shippable):** `:core-emulator` libretro JNI facade loading
  `ppsspp_libretro_android.so` (Vulkan) + `:feature-player` + `:core-input` +
  store/library over ~30–60 Tier-1 PSP homebrew + `:data-download` (R2 signed URLs) +
  import-your-own-ROM + AdMob/Gold + Play internal test & parallel direct APK.
- **Phase 2 — Cloud saves, accounts & polish:** `:data-saves` sync, account UI + Gold gating,
  controller/overlay config, store-listing hardening, Play production (PSP-only) + F-Droid variant.
- **Phase 3 — PS1:** SwanStation/Beetle/PCSX-ReARMed via the existing facade, user-BIOS import,
  `.bin/.cue/.chd` handling, catalog expansion (Tier-1 + Tier-2 itch.io outreach).
- **Phase 4 — PS2 (gated):** ARMSX2-as-libretro, device-capability gate (SoC allowlist / ≥6GB /
  Android 10+ / Vulkan), PS2 BIOS import, per-game compat metadata, Play Asset Delivery core
  download, small PS2 homebrew catalog.
- **Phase 5 — Growth & resilience:** admin review pipeline (license + IP screening), Tier-2/3
  scaling, egress cost monitoring, save-state core-drift handling, self-updater, moderation +
  fast DMCA tooling.

## Top risks

1. **GPL forces full open-source** and forbids added restrictions — publish complete source per
   release or it's a violation.
2. **PS2 is the weak link** — ARMSX2 is young, device-sensitive; gate it hard.
3. **Rightsholder takedowns** (Sony/Nintendo post-Yuzu) can hit even a compliant app; keep it
   provably clean, branding generic.
4. **"Free" ≠ "redistributable"** — the auto-clean catalog is only dozens without outreach.
5. **Storage egress** can silently balloon — the R2 hybrid is mandatory before scaling.
6. **Save-state fragility** across core versions — memory cards are the durable path.
7. **Native/JNI crash & ABI bloat** — mitigated by `:emu` process + Play Asset Delivery.
8. **2026 dev verification** removes anonymity — run as a verified business.

> This is architectural/product guidance, not legal advice. Get qualified counsel before launch.
