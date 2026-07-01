# RetroVault — Build Plan

> This file is updated as research completes. A background research pass is running to lock
> down emulator-core choices + licenses, legal catalog sources, Google Play policy, and the
> full architecture. The phased plan below is refined once that lands.

## Product in one line

An Android emulator for PSP / PS1 / PS2 with a storefront of **legally distributable** games
and a **bring-your-own-ROM** import flow — the friendly "download and play" UX, without
hosting copyrighted content.

## The non-negotiable legal boundary

- ✅ Host & serve: homebrew, public-domain, freeware, and redistribution-licensed indie games.
- ✅ Let users import: their own legally-owned game dumps and their own console BIOS.
- ❌ Never host or link: copyrighted commercial ROMs, or Sony BIOS files.

This boundary is what makes the app durable (not DMCA-bait) and monetizable on real stores.

## Phased roadmap (initial draft — pending research synthesis)

### Phase 0 — Scaffold ✅ (in progress)
- Kotlin + Compose app shell, storefront UI, navigation, placeholder catalog. **Done.**
- Git repo + GitHub. **Next.**

### Phase 1 — Backend + real legal catalog
- Provision Supabase: `systems`, `games`, `users`, `user_library`, downloads/saves; RLS.
- Storage/CDN bucket for legal game files.
- Replace in-memory `CatalogRepository` with a Supabase-backed repository.
- Seed a curated launch catalog of vetted, legally-distributable homebrew/PD titles.

### Phase 2 — Emulator core: PSP (PPSSPP)
- Integrate the first native core (PSP via PPSSPP) through JNI/libretro.
- Wire a game session Activity: rendering surface, audio, touch overlay + gamepad input.
- Save states (local first).

### Phase 3 — Download + install + import
- Download manager (resumable, Wi-Fi-only option) for hosted legal games.
- Storage Access Framework import for user-owned ROMs and BIOS.
- Per-game settings.

### Phase 4 — PS1, then PS2 cores
- Add PS1 core (DuckStation/SwanStation/PCSX-ReARMed), then evaluate PS2 feasibility on Android.
- BIOS handled strictly as user-supplied.

### Phase 5 — Accounts, cloud saves, monetization
- Supabase auth + cloud save sync.
- Compliant monetization (ads / one-time unlock) — no paywalling of others' GPL content.

### Phase 6 — Distribution
- Decide channel(s): direct APK/site, F-Droid, Play, alt stores — per policy research.
- Store listing, privacy policy, content attribution/licensing page.

## Open questions being researched
- libretro (unified cores) vs. embedding each emulator's native lib — licensing + effort.
- GPL obligations for a store/ad-supported app bundling GPL cores.
- Realistic size & best sources of a legal launch catalog per system.
- Google Play's current emulator policy and whether this model passes review long-term.
