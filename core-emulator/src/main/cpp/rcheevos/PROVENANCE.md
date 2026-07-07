# Vendored: rcheevos (RetroAchievements runtime + rc_client)

- Upstream: https://github.com/RetroAchievements/rcheevos
- Version: **v11.6.0**
- Commit: `3106e6d3d274b63c59976cb07dda94a292ab45ca`
- License: **MIT** (see LICENSE) — compatible with this app's GPLv3.
- Vendored: 2026-07-07 for P20 (RetroAchievements support).

Only `include/` and `src/` are vendored (upstream `test/`, `validator/`,
`Package.swift` omitted). Pristine copy — do not edit; to update, re-vendor a
pinned tag and re-run the build. `src/rc_client_raintegration.c` is compiled but
is entirely behind `#ifdef RC_CLIENT_SUPPORTS_RAINTEGRATION` (Windows-only), so it
is an empty translation unit on Android.
