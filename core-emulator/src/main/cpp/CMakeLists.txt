cmake_minimum_required(VERSION 3.22.1)
project(pulsar_retro)

# Native libretro host. Loaded by com.retrovault.emulator.LibretroBridge.
# Vendor libretro.h next to this file (see README.md) before enabling in build.gradle.kts.
add_library(pulsar_retro SHARED libretro_host.cpp)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(pulsar_retro
    ${log-lib}
    ${android-lib}
    dl          # dlopen the core .so
    EGL
    GLESv3)

# 16 KB page-size compliance (Google Play requirement; default in NDK r28+, kept explicit).
target_link_options(pulsar_retro PRIVATE "-Wl,-z,max-page-size=16384")
