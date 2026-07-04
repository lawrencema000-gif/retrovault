cmake_minimum_required(VERSION 3.22.1)
project(pulsar_retro)

# Native libretro host: dlopen cores, GL video backbone, Swappy frame pacing.
# Loaded by com.retrovault.emulator.LibretroBridge.
add_library(pulsar_retro SHARED
    libretro_host.cpp
    video_gl.cpp
    audio_out.cpp)

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
    GLESv3)

# 16 KB page-size compliance (Google Play requirement; default in NDK r28+, kept explicit).
target_link_options(pulsar_retro PRIVATE "-Wl,-z,max-page-size=16384")
