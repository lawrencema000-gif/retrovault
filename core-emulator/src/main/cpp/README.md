# Native libretro host (`pulsar_retro`)

C++ frontend that `dlopen`s a libretro core `.so` and drives its `retro_*` lifecycle for the
`com.retrovault.emulator.LibretroBridge` JNI methods.

**Status:** foundation. The symbol loading, callback wiring, input, and save-state marshalling are
implemented; video/audio/hw-context glue is marked `TODO(integration)`.

## To activate (final integration pass)

1. Install the Android NDK (`sdkmanager "ndk;27.x.x"`).
2. Vendor `libretro.h` next to this file (from the libretro-common headers).
3. Enable the native build in `core-emulator/build.gradle.kts`:
   ```kotlin
   android {
       defaultConfig { ndk { abiFilters += listOf("arm64-v8a", "x86_64") } }
       externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
   }
   ```
4. Drop core `.so` files into `core-emulator/src/main/jniLibs/<abi>/`
   (e.g. `ppsspp_libretro_android.so` from the libretro buildbot) — **never** commit a Sony BIOS.
5. Finish `env_cb` (pixel format, HW render, system/save dirs), `video_cb`, `audio_batch_cb`.

Once built, `LibretroBridge.available` becomes `true` and the player drives real emulation.
