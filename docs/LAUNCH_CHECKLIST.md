# Launch Checklist — the things only you (the account owner) can do

Everything code-side is done and tested on the emulator (P6–P17, P19–P24, P27; suite 67 green).
What remains is split into: (A) a device session with your phone, and (B) account setups.
Work through them in any order; each section says exactly what to do and what unblocks.

> Monetization is **Gold-only** (decided 2026-07-17). AdMob was removed for GPL compliance —
> **you do NOT need an AdMob account.** The `verifyFull*RuntimeClasspath` build gates keep ads
> out permanently.

## A. Device session (unblocks P5, then P18/P25/P26)

1. On your Android phone: Settings → About phone → tap **Build number** 7× (enables Developer
   options) → Developer options → enable **USB debugging**.
2. Plug the phone into this PC via USB, accept the "Allow USB debugging?" prompt.
3. Tell Claude "device connected" — the P5 validation pass runs from there (PSP gameplay feel,
   PS1 boot, touch-stick fix re-verify, skins, rotation, TalkBack, audio soak, controller if you
   have one). P18 (thermal), P25 (PS2), P26 (TV/foldables) follow in later device sessions.
4. Nice-to-have for PS1 testing: a game dump (.bin/.cue or .chd) you made from a disc you own.
   BIOS is optional (SwanStation boots via embedded OpenBIOS), but a real `scph5501.bin` dump
   from your console gives best compatibility.

## B. Accounts

### 1. Google Play Console (unblocks Play release + Gold IAP)
- [ ] Register at https://play.google.com/console ($25 one-time) as `lawrencema000-gif`'s
      developer identity (individual or org — org needs a D-U-N-S number).
- [ ] Complete Google's **developer verification** (identity + address; 2026 requirement).
- [ ] Set up a **payments/merchant profile** (required to sell the Gold IAP).
- [ ] Create the app (package `com.retrovault.app`, name "Pulsar").
- [ ] In-app product: id `pulsar_gold_unlock` (one-time). Optional later: `pulsar_cloud_monthly`.
- [ ] IARC content-rating questionnaire. ⚠️ When adhoc multiplayer ships (see NETPLAY.md),
      re-run it declaring **"Users Interact"**.
- [ ] Data safety form — matches docs/PRIVACY.md: no ads SDKs, opt-in crash reports (Sentry),
      Supabase auth/saves, IP+nickname to third-party servers only if multiplayer ships.
- [ ] Service account for purchase verification: Play Console → API access → create a service
      account with "View financial data" + order management → download its JSON key → set it as
      the `GOOGLE_PLAY_SA_JSON` secret on the Supabase `verify-purchase` edge function, then
      deploy it. (It fails closed until then — safe.)
- [ ] Upload: `./gradlew :app:bundleFullRelease` (needs signing — section 4).

### 2. RetroAchievements (unblocks live RA login/unlocks; ~6-month public clock)
- [ ] Create an account at https://retroachievements.org.
- [ ] Apply for **standalone-emulator approval** (RA requires emulators integrating rc_client to
      be reviewed for hardcore compliance — Pulsar's interlock is built and tested). The approval
      process wants a public, released emulator, so file this once a first release/tag exists.
      The clock is long (~6 months of public availability is typical) — start it early.
- [ ] Once approved: test a real login (token persists via Android Keystore) + a real unlock,
      then wire the AchievementsPanel into the player nav (staged, one session).

### 3. Sentry (optional — crash reports, full flavor, opt-in)
- [ ] Create a project at https://sentry.io (or self-host) → copy the DSN.
- [ ] Set `SENTRY_DSN` as a CI secret / env var at release-build time. Empty DSN = Sentry stays
      completely inert (that's the current state; shipping without it is fine).

### 4. Signing ceremony (unblocks any release build)
- [ ] Generate an upload keystore:
      `keytool -genkeypair -v -keystore pulsar-upload.jks -alias pulsar -keyalg RSA -keysize 4096 -validity 10000`
- [ ] Store it OUTSIDE the repo + an offline backup of the keystore and passwords.
- [ ] Add a `signingConfigs` block via `~/.gradle/gradle.properties` paths (never commit secrets).
- [ ] Play uses Play App Signing (your key = upload key). Direct-APK releases use the same key.
- [ ] At 1.0: tag `v1.0.0`; the GitHub release body must carry the GPL §6d corresponding-source
      pointers (repo tag + per-core upstream commit + build-workflow link) — see DISTRIBUTION.md.

### 5. F-Droid (after 1.0, foss flavor)
- [ ] Pre-submission blocker: cores must build from source in the recipe (external/ submodules +
      a shared build script — see docs/FDROID.md). Then submit `fdroid/com.retrovault.app.yml`.

## C. Open product decisions (yours)
- [ ] **Battlegrounds 3 SFX provenance** — flagged 2026-07-06 (sounddogs.com chain-of-title
      doubt). Before public launch: confirm rights with the author (xfacter) or unpublish and
      make rRootage the featured anchor.
- [ ] **ScreenScraper box-art** (P27 staged) — needs their written approval for commercial use
      before any scraper ships. Email contact@screenscraper.fr if wanted; otherwise skip.
- [ ] **Adhoc multiplayer** — GO verdict recorded in docs/NETPLAY.md (~2–4 dev-days when you
      want it; brings the IARC/Data-safety updates above with it).
