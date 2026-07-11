# F-Droid submission plan (P22)

The `foss` flavor is F-Droid's build: zero proprietary deps (gradle-enforced), no crash
reporting, manual-only update check, full license texts in-app. Metadata template:
[`fdroid/com.retrovault.app.yml`](../fdroid/com.retrovault.app.yml).

## The one real blocker: cores must build from source

F-Droid's buildserver builds from a **clean git checkout** — the gitignored CI-artifact cores in
`core-emulator/src/main/jniLibs/` won't exist, and fetching prebuilt `.so` files in a recipe is
forbidden by the inclusion policy (the prebuilt whitelist covers only Maven Central / Google
Maven / Debian-class sources). Precedents:

- **PPSSPP** (org.ppsspp.ppsspp): F-Droid compiles the whole native app *including ffmpeg* from
  source — proof the buildserver handles PPSSPP-scale native builds. Zero anti-features.
- **RetroArch**: only the frontend builds from source; its runtime core downloader earned
  `NonFreeNet` and the F-Droid build can't fetch cores at all — the outcome to avoid.
- **Lemuroid**: prebuilt cores are `scandelete`d and the "free dynamic" flavor downloads them at
  runtime from GitHub → `NonFreeNet` + worse UX.

**Chosen approach (Option A — PPSSPP model):** before submission, add the pinned PPSSPP tree as a
git submodule (`external/ppsspp @ v1.20.4`, the exact ref `.github/workflows/build-cores.yml`
already pins) plus `scripts/build-cores.sh` replicating the CI cmake invocation
(`-DLIBRETRO=ON`, `-Wl,-z,max-page-size=16384`, NDK r28). Both GitHub CI and the fdroiddata
recipe call the same script — single source of truth, and the prerequisite for reproducible
builds. Cores produced in the recipe's `build:` step run *after* the source scan, so nothing is
flagged. Result: no core anti-features and cores that actually work on F-Droid.

## Submission checklist (in order)

1. **Cores-from-source** (above) — a fresh clone must produce a working `fossRelease`.
2. **Signing ceremony**: create ONE stable foss release keystore (kept out of git; key loss =
   F-Droid package reset). Sign with `apksigner` in CI. Keep `signingConfig` out of gradle.
3. **Annotated tag** `vX.Y.Z` on the exact release commit matching `versionName`.
4. **Reproducibility spot-check**: build `fossRelease` from two clean clones in different
   directories; diffoscope the APKs. (`dependenciesInfo.includeInApk = false` is already set —
   the #1 APK reproducibility killer. Add `-ffile-prefix-map` + `-Wl,--build-id=none` to the
   native builds if paths/build-ids diverge.)
5. **Fastlane images**: add `images/icon.png` + `images/phoneScreenshots/` for a decent listing.
6. Submit a **direct MR** to fdroiddata with the metadata file (faster than the RFP queue),
   noting we are upstream. Expect weeks of review; updates are automatic afterwards
   (`AutoUpdateMode: Version`, `UpdateCheckMode: Tags`).
7. Decide **before** submitting: the applicationId `com.retrovault.app` is permanent on F-Droid.
   If a Pulsar-branded package rename is ever wanted, do it first.

## Release-channel map

| Channel | Flavor | Update path | Notes |
|---|---|---|---|
| Google Play | `full` (AAB, `bundleFullRelease`) | Play | No update-check code exists in this flavor (Play policy). Staged: listing, data-safety form, content rating. |
| F-Droid | `foss` | F-Droid client | Built by F-Droid from source, or reproducible-verified (`Binaries:`). |
| GitHub Releases (direct APK) | `foss` (`assembleFossRelease`, apksigner-signed) | Manual in-app check (user-initiated, opens the releases page) | Release body must carry the corresponding-source pointers (repo tag + per-core upstream commit + build workflow link) per GPLv3 §6d. |
