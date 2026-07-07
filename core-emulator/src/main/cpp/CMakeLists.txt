cmake_minimum_required(VERSION 3.22.1)
project(pulsar_retro)

# ---------------------------------------------------------------------------
# rcheevos (RetroAchievements runtime + rc_client) — vendored v11.6.0, MIT.
# Built as a STATIC C lib linked into pulsar_retro. Public headers are extern "C"
# safe (rc_export.h: RC_BEGIN_C_DECLS == `extern "C" {`), so the C++ host can
# #include "rc_client.h" / "rc_hash.h" / rc_libretro.h directly.
# ---------------------------------------------------------------------------
set(RCHEEVOS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/rcheevos)
file(GLOB_RECURSE RCHEEVOS_SOURCES CONFIGURE_DEPENDS ${RCHEEVOS_DIR}/src/*.c)
# Windows/RAIntegration-only TU (empty on Android) — drop it.
list(REMOVE_ITEM RCHEEVOS_SOURCES ${RCHEEVOS_DIR}/src/rc_client_raintegration.c)

add_library(rcheevos STATIC ${RCHEEVOS_SOURCES})
target_include_directories(rcheevos
    PUBLIC  ${RCHEEVOS_DIR}/include
    PRIVATE ${RCHEEVOS_DIR}/src
            ${CMAKE_CURRENT_SOURCE_DIR})   # rc_libretro.h needs <libretro.h> (cpp/libretro.h)
target_compile_definitions(rcheevos
    # RC_CLIENT_SUPPORTS_HASH is PUBLIC: it gates both the rc_client.c impl AND the rc_client.h
    # declarations of begin_identify_and_load_game / begin_change_media, so the rc_bridge consumer
    # (compiled into pulsar_retro) must see the same define or those calls are "undeclared".
    PUBLIC  RC_STATIC
            RC_CLIENT_SUPPORTS_HASH
    PRIVATE RC_DISABLE_LUA                 # no Lua runtime (lua_State stays opaque fwd-decl)
            _LARGEFILE64_SOURCE)           # fseeko64/ftello64 for >2 GB PSP ISOs
target_compile_options(rcheevos PRIVATE -w) # vendored C89/C99 — keep its warnings out of our build
set_target_properties(rcheevos PROPERTIES POSITION_INDEPENDENT_CODE ON)

# Native libretro host: dlopen cores, GL video backbone, Swappy frame pacing,
# RetroAchievements bridge. Loaded by com.retrovault.emulator.LibretroBridge.
add_library(pulsar_retro SHARED
    libretro_host.cpp
    video_gl.cpp
    audio_out.cpp
    rc_bridge.cpp)

# rc_bridge.h -> rcheevos/src/rc_libretro.h -> <libretro.h> (cpp/) — put both on the path.
target_include_directories(pulsar_retro PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${RCHEEVOS_DIR}/src)

# AGDK Frame Pacing (Swappy) via Prefab (androidx.games:games-frame-pacing).
find_package(games-frame-pacing REQUIRED CONFIG)
# Oboe low-latency audio via Prefab (com.google.oboe:oboe).
find_package(oboe REQUIRED CONFIG)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(pulsar_retro
    games-frame-pacing::swappy_static
    oboe::oboe
    ${log-lib}
    ${android-lib}
    dl          # dlopen the core .so
    EGL
    GLESv3
    rcheevos)   # RetroAchievements runtime + rc_client (vendored, static)

# 16 KB page-size compliance (Google Play requirement; default in NDK r28+, kept explicit).
target_link_options(pulsar_retro PRIVATE "-Wl,-z,max-page-size=16384")
