# Netplay / Multiplayer — Go/No-Go (P27 evaluation)

Decision date: 2026-07-14. Every claim below was verified against primary sources at the exact
pinned core versions (PPSSPP `v1.20.4` = `fa50bb19`, per `.github/workflows/build-cores.yml`) by
downloading and grepping the core source — not summarized from docs.

## Verdicts

| Path | Verdict | Why |
| --- | --- | --- |
| PSP **adhoc** (PPSSPP's own network stack) | **GO** | Config-level, not frontend netcode. The v1.20.4 libretro port ships a complete `network` core-option category already wired to `g_Config`; our settings pipeline + manifest permissions already exist. ~2–4 dev-days incl. UI. |
| RetroArch-style **frame-sync netplay** | **NO-GO (impossible)** | libretro's own Netplay FAQ: "GB, GBA, PSP netplay are currently not possible with our implementation." docs.libretro.com marks the PPSSPP core Netplay ✕. Rollback needs cheap deterministic per-frame savestates; PSP can't. There is no alternative to the adhoc stack. |
| PSP **infrastructure mode** (revived PSN servers) | **DEFER (absent)** | Infrastructure mode, PSN username, and DNS override are entirely absent from the v1.20.4 libretro port (verified in `libretro_core_options.h` + `libretro.cpp`). Can't ship without core patches; also the legally grayest option. |
| PPSSPP 1.20 **packet relay** (aemu_postoffice, PR #21116) | **DEFER (post-1.0)** | The relay code + submodule are compiled into the v1.20.4 tree but not exposed as a libretro core option. Since we build cores from source at a pinned ref, a small exposure-only patch (+1–2 days) could add it later. It is the fix for CGNAT internet play. |

## How adhoc works in the pinned core (verified)

All keys exist verbatim in v1.20.4 `libretro/libretro_core_options.h` and map straight onto
`g_Config` in `libretro.cpp` — the core opens its own OS sockets; zero references to
`retro_netpacket` or any libretro netplay interface:

- `ppsspp_enable_wlan` — default **disabled**, label literally "Enable Networking/WLAN (Beta, may
  break games)" → our UI must be opt-in "Multiplayer (beta)".
- `ppsspp_change_mac_address01..12` — MAC as 12 hex-digit options. All-zero → core calls
  `CreateRandMAC()` and pushes the digits back via `RETRO_ENVIRONMENT_SET_VARIABLE`
  (libretro.cpp:1052–1058). **Our host doesn't handle SET_VARIABLE, so the pushback silently
  drops and the MAC re-randomizes every session** — breaks game-side friend pairing. Fix: persist
  a generated MAC app-side, or add the SET_VARIABLE env case. Either works.
- `ppsspp_change_pro_ad_hoc_server_address` — presets socom.cc (option default) /
  psp.gameplayer.club / myneighborsushicat.com / localhost / "IP address". Key finding: **any
  other value passes verbatim into `g_Config.sProAdhocServer`** (libretro.cpp:1082), so a
  free-text hostname field passes straight through the main key — the 12-digit-IP mechanism
  (`ppsspp_pro_ad_hoc_server_addressNN`) can be ignored entirely.
- `ppsspp_enable_builtin_pro_ad_hoc_server` — device can host for same-LAN play.
- `ppsspp_enable_upnp`, `ppsspp_upnp_use_original_port`, `ppsspp_port_offset`,
  `ppsspp_minimum_timeout`, `ppsspp_forced_first_connect`, `ppsspp_wlan_channel`.
- `RETRO_ENVIRONMENT_GET_USERNAME` → `g_Config.sNickName` (libretro.cpp:1238). Our
  `libretro_host.cpp` has **no GET_USERNAME case** → every Pulsar player gets the default
  nickname. Fix: one env case + one JNI string.

Already in place: `SettingsResolver → nativeSetCoreVariable → GET_VARIABLE` pipeline (proven with
the shipped `ppsspp_*` settings) and `INTERNET` + `ACCESS_NETWORK_STATE` in the manifest.

## v1 implementation scope (one P27-sized session, ~2–4 dev-days)

1. Opt-in **Multiplayer (beta)** settings section — default off, matching the core.
2. Nickname field, fed via a new `GET_USERNAME` env case in `libretro_host.cpp`.
3. **Curated server picker** + free-text custom host. The official ppsspp.org list has 16 ACTIVE
   servers in 2026 (socom.cc France, psp.mgn.pub USA, eahub.eu, ArenaAnywhere EU/US/Asia/SA, Neko
   PSP Japan, …) while coldbird and myneighborsushicat.com are DEFUNCT — yet the stale
   myneighborsushicat preset is still in the core options, so we must curate in the UI and pass
   the chosen hostname through as a custom value. Deep-link to each server's Discord/rules; do
   not operate or endorse any server ourselves.
4. Persistent auto-generated MAC (see above).
5. **"Host on LAN" helper**: enables the built-in PRO adhoc server option and shows the device's
   local IP for the second device to enter.
6. Warnings: pause / fast-forward / rewind drop live connections.

No in-app rooms/lobbies — "rooms" are the PSP games' own in-game gathering halls; matchmaking
happens on each server's Discord.

## Honest scope for marketing/UI copy

The reliable promise is **same-Wi-Fi/LAN play** (two Pulsar devices, one hosting). Internet play
via community servers is **best-effort**: game traffic is P2P and needs UPnP or manual port
forwarding, which is frequently impossible on carrier CGNAT. PPSSPP 1.20's relay mode fixes
exactly this but is not reachable from the libretro core options at v1.20.4 (see DEFER above).

Pulsar↔Pulsar play is version-matched by construction (every install pins v1.20.4) — a genuine
advantage; cross-play with standalone PPSSPP only works while those users are on 1.20.x.

## Legal / Play compliance (LOW RISK, with to-dos)

Community adhoc servers are clean-room reimplementations (coldbird/aemu lineage, open source);
they coordinate peers and relay user packets — no Sony code, no copyrighted content served.
Precedent: PPSSPP / PPSSPP Gold (`org.ppsspp.ppssppgold`) have shipped on Google Play for over a
decade with this exact networking stack and these exact public-server defaults.

Before shipping multiplayer:

- [ ] Re-run the IARC questionnaire declaring **"Users Interact"** (changes content-rating labels).
- [ ] Update Data safety form + `docs/PRIVACY.md`: when the user opts in, their IP address and
      chosen nickname go to a user-selected third-party community server; in-game chat on those
      servers is unmoderated.
- [ ] Keep the feature default-off / opt-in.
- [ ] Do not operate or endorse any server — link to the official ppsspp.org server list.

## Support burden (real but bounded)

Expect multiplayer to be the most ticket-dense feature per user. Documented failure modes: version
mismatch across peers (Pulsar↔Pulsar immune), identical port-offset required, game region/ID must
match (EU vs US copies can't join), "Failed to connect to AdhocServer" / "Failed to bind port",
CGNAT blocking internet P2P, per-game quirks (disable cheats/speedhacks to avoid desync, force
real clock sync for some titles), and pause/FF dropping sessions. Community compatibility tier
(AkiraJkr adhoc list): Perfect ≈ Monster Hunter Freedom series, God Eater, Ace Combat Joint
Assault; Not Working ≈ Gran Turismo, KH Birth by Sleep. Mitigations: "beta" label, in-app
troubleshooting page mirroring the official guide, and only claim per-game support for the
Perfect/Playable tier that overlaps our legal catalog.

## PS1 (SwanStation)

Out of scope for this evaluation. SwanStation has no equivalent built-in network stack in the
libretro port; PS1 multiplayer would require frame-sync netplay, which our single-frontend
architecture doesn't provide. No PS1 multiplayer story for 1.0.
