// Host hooks the RetroAchievements bridge (rc_bridge.cpp) calls back into. Implemented by
// libretro_host.cpp, which owns the core, the run loop, and the hardcore interlock. Keeping this
// a narrow interface avoids exposing libretro_host's file-static globals across translation units.
#pragma once

#include <cstddef>
#include <cstdint>

struct retro_memory_map;

// The core's memory region for RA reads (retro_get_memory_data/size). *data is null if the core
// exposes no RAM for [id]. The returned pointer is stable for the session (safe to cache in
// rc_libretro's region table). Reads a stable pointer; callable from any thread while loaded.
void rc_host_get_core_memory(unsigned id, unsigned char** data, size_t* size);

// The descriptors the core supplied via RETRO_ENVIRONMENT_SET_MEMORY_MAPS, or null if none.
// rc_libretro prefers this over the flat SYSTEM_RAM path when present.
const struct retro_memory_map* rc_host_memory_map();

// Reset the emulated system (services RC_CLIENT_EVENT_RESET). No-op if the core lacks retro_reset.
void rc_host_reset_core();

// Apply hardcore state to the host interlock: set g_hardcoreActive and, when enabling, clear the
// active cheat set (cheats are mutually exclusive with hardcore). Called on the run-loop thread.
void rc_host_apply_hardcore(bool on);
