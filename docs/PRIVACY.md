# Pulsar — Privacy Policy (draft)

_Last updated: 2026-07-07. Draft for review before store submission._

Pulsar is an open-source (GPLv3) emulator and a store for legally-distributable games.

## What we collect

- **Account (optional):** if you sign in, your email and an account id (via Supabase Auth) to sync
  your library and cloud saves. You can use the app without an account.
- **Cloud saves (optional):** if enabled, your save-state files are stored in your own private
  folder in our backend so they sync across your devices.
- **Crash reports (optional, OFF by default, Play build only):** if you turn on "Share crash
  reports" in Settings, crash logs and basic diagnostics are sent to Sentry (acting as our
  processor) to help fix bugs. No personal identifiers are attached, and the F-Droid/GPL build
  contains no crash-reporting code at all.
- **Compatibility reports (optional):** if you choose to rate a game's compatibility after
  playing, the rating plus anonymous device info (SoC/GPU family, Android version) is submitted
  keyed to a random install id — never an account or hardware identifier.
- **RetroAchievements (optional):** if you sign in to RetroAchievements, your RA username and
  token are stored encrypted on-device and used only to talk to retroachievements.org.

## What we do NOT do

- We do **not** host, distribute, or receive copyrighted game ROMs or console BIOS. Those stay on
  your device — imported by you, never uploaded.
- We do **not** sell your personal data.

## Your data

- Downloaded/imported games and BIOS live in app-scoped storage on your device and are removed when
  you uninstall.
- You can delete your account and cloud data at any time (contact below / in-app once shipped).

## Third parties

- Supabase (auth, catalog, cloud saves), a CDN for legal game files, and — in the ad-supported
  free tier (Play build only) — Google AdMob with UMP consent, plus Sentry for opt-in crash
  reports. The F-Droid/GPL build contains no ad, analytics, or crash-reporting SDKs (verified
  at build time).

## Contact

lawrence.ma000@gmail.com
