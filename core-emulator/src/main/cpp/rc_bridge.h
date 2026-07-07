// RetroAchievements bridge (rcheevos v11.6.0 rc_client) — the surface libretro_host.cpp drives.
// All functions here run on the emulator run-loop thread (the single owner of rc_client_* calls),
// except where noted. The JNI exports (Java_com_retrovault_emulator_RaBridge_*) live in the .cpp.
#pragma once

#include <cstddef>
#include <cstdint>
#include <vector>

// Run-loop thread, once per iteration: drain the command queue + HTTP-completion inbox, invoking
// any owed rc_client callbacks HERE (never on the OkHttp thread) so rc_client stays single-owner.
void rc_bridge_service();

// Run-loop thread, after each retro_run(): rc_client_do_frame (evaluates achievement logic).
void rc_bridge_on_emulated_frame();

// Run-loop thread, when paused / a frame was skipped: rc_client_idle (services async work).
void rc_bridge_idle();

// Run-loop thread, BEFORE the core is unloaded/dlclosed: abort async ops, fail any owed HTTP
// callbacks, unload_game, destroy the client, free the memory regions. Safe if no session.
void rc_bridge_end_session();

bool rc_bridge_is_active();          // an rc_client exists
bool rc_bridge_ra_game_loaded();     // an RA game is loaded (achievement memrefs are live)

// ---- save-state RA-progress container (run-loop thread) ----
// [blob] holds the raw core state on entry; if an RA game is loaded, append [rc_progress][footer]
// so achievement progress rides inside the save state. No-op (legacy format) when RA is inactive.
void rc_bridge_pack_state(std::vector<uint8_t>& blob);

// Given a save-state file, return how many leading bytes are the core state (to hand to
// retro_unserialize). Equals fileLen for legacy (footer-less) states.
size_t rc_bridge_state_core_len(const uint8_t* file, size_t fileLen);

// After retro_unserialize restored guest RAM, restore RA progress from the trailer (or reset the
// trackers when the state is legacy / RA is inactive). Run-loop thread.
void rc_bridge_state_restore_progress(const uint8_t* file, size_t fileLen);
