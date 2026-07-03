// Pulsar native libretro host.
//
// Implements the JNI methods declared in com.retrovault.emulator.LibretroBridge by dlopen-ing a
// libretro core .so and driving its retro_* lifecycle. This is the integration skeleton: symbol
// loading, the callback wiring and save-state marshalling are done; the video/audio/hw-context
// glue is marked TODO(integration) and completed during on-device bring-up.
//
// Requires libretro.h vendored beside this file (see README.md) and the NDK enabled in
// core-emulator/build.gradle.kts.

#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <cstdint>
#include <cstdio>
#include <vector>
#include "libretro.h"

#define LOG_TAG "pulsar_retro"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct Core {
    void* handle = nullptr;
    void (*retro_init)() = nullptr;
    void (*retro_deinit)() = nullptr;
    void (*retro_set_environment)(retro_environment_t) = nullptr;
    void (*retro_set_video_refresh)(retro_video_refresh_t) = nullptr;
    void (*retro_set_audio_sample_batch)(retro_audio_sample_batch_t) = nullptr;
    void (*retro_set_input_poll)(retro_input_poll_t) = nullptr;
    void (*retro_set_input_state)(retro_input_state_t) = nullptr;
    bool (*retro_load_game)(const retro_game_info*) = nullptr;
    void (*retro_unload_game)() = nullptr;
    void (*retro_run)() = nullptr;
    size_t (*retro_serialize_size)() = nullptr;
    bool (*retro_serialize)(void*, size_t) = nullptr;
    bool (*retro_unserialize)(const void*, size_t) = nullptr;
};

Core g_core;
ANativeWindow* g_window = nullptr;
int16_t g_buttons = 0;
int16_t g_analog_lx = 0, g_analog_ly = 0;

bool env_cb(unsigned /*cmd*/, void* /*data*/) {
    // TODO(integration): SET_PIXEL_FORMAT, SET_HW_RENDER (GL/Vulkan context), GET_SYSTEM_DIRECTORY
    // (user BIOS dir), GET_SAVE_DIRECTORY, GET_VARIABLE (core options).
    return false;
}

void video_cb(const void* /*data*/, unsigned /*w*/, unsigned /*h*/, size_t /*pitch*/) {
    // TODO(integration): software cores -> blit framebuffer to g_window; hw cores (PPSSPP/ARMSX2)
    // render into the surface negotiated via SET_HW_RENDER.
}

size_t audio_batch_cb(const int16_t* /*data*/, size_t frames) {
    // TODO(integration): feed AudioTrack / OpenSL ES.
    return frames;
}

void input_poll_cb() {}

int16_t input_state_cb(unsigned port, unsigned device, unsigned /*index*/, unsigned id) {
    if (port != 0) return 0;
    if (device == RETRO_DEVICE_JOYPAD) return (g_buttons >> id) & 1;
    if (device == RETRO_DEVICE_ANALOG) return 0; // TODO(integration): decode analog index/id
    return 0;
}

template <typename T>
T sym(void* h, const char* name) { return reinterpret_cast<T>(dlsym(h, name)); }

} // namespace

extern "C" {

// Loads a core and returns "name|version|extensions|api" from retro_get_system_info /
// retro_api_version WITHOUT running retro_init — the P1 smoke test that a core .so is loadable.
JNIEXPORT jstring JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeProbeCore(JNIEnv* env, jobject, jstring corePath) {
    const char* path = env->GetStringUTFChars(corePath, nullptr);
    void* handle = dlopen(path, RTLD_NOW | RTLD_LOCAL);
    env->ReleaseStringUTFChars(corePath, path);
    if (!handle) { LOGE("probe dlopen failed: %s", dlerror()); return nullptr; }

    auto api_version = sym<unsigned (*)()>(handle, "retro_api_version");
    auto get_info = sym<void (*)(retro_system_info*)>(handle, "retro_get_system_info");
    if (!api_version || !get_info) {
        LOGE("probe: core missing retro_api_version/retro_get_system_info");
        dlclose(handle);
        return nullptr;
    }

    retro_system_info info{};
    get_info(&info);
    char buf[512];
    snprintf(buf, sizeof(buf), "%s|%s|%s|%u",
             info.library_name ? info.library_name : "?",
             info.library_version ? info.library_version : "?",
             info.valid_extensions ? info.valid_extensions : "",
             api_version());
    dlclose(handle);
    return env->NewStringUTF(buf);
}

JNIEXPORT jboolean JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeInit(JNIEnv* env, jobject, jstring corePath) {
    const char* path = env->GetStringUTFChars(corePath, nullptr);
    g_core.handle = dlopen(path, RTLD_NOW | RTLD_LOCAL);
    env->ReleaseStringUTFChars(corePath, path);
    if (!g_core.handle) { LOGE("dlopen failed: %s", dlerror()); return JNI_FALSE; }

    g_core.retro_init = sym<void (*)()>(g_core.handle, "retro_init");
    g_core.retro_deinit = sym<void (*)()>(g_core.handle, "retro_deinit");
    g_core.retro_set_environment = sym<void (*)(retro_environment_t)>(g_core.handle, "retro_set_environment");
    g_core.retro_set_video_refresh = sym<void (*)(retro_video_refresh_t)>(g_core.handle, "retro_set_video_refresh");
    g_core.retro_set_audio_sample_batch = sym<void (*)(retro_audio_sample_batch_t)>(g_core.handle, "retro_set_audio_sample_batch");
    g_core.retro_set_input_poll = sym<void (*)(retro_input_poll_t)>(g_core.handle, "retro_set_input_poll");
    g_core.retro_set_input_state = sym<void (*)(retro_input_state_t)>(g_core.handle, "retro_set_input_state");
    g_core.retro_load_game = sym<bool (*)(const retro_game_info*)>(g_core.handle, "retro_load_game");
    g_core.retro_unload_game = sym<void (*)()>(g_core.handle, "retro_unload_game");
    g_core.retro_run = sym<void (*)()>(g_core.handle, "retro_run");
    g_core.retro_serialize_size = sym<size_t (*)()>(g_core.handle, "retro_serialize_size");
    g_core.retro_serialize = sym<bool (*)(void*, size_t)>(g_core.handle, "retro_serialize");
    g_core.retro_unserialize = sym<bool (*)(const void*, size_t)>(g_core.handle, "retro_unserialize");

    if (!g_core.retro_init || !g_core.retro_run || !g_core.retro_load_game) {
        LOGE("core missing required symbols");
        return JNI_FALSE;
    }

    g_core.retro_set_environment(env_cb); // must be set before retro_init
    g_core.retro_set_video_refresh(video_cb);
    g_core.retro_set_audio_sample_batch(audio_batch_cb);
    g_core.retro_set_input_poll(input_poll_cb);
    g_core.retro_set_input_state(input_state_cb);
    g_core.retro_init();
    LOGI("core initialized");
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeLoadGame(JNIEnv* env, jobject, jstring gamePath) {
    if (!g_core.retro_load_game) return JNI_FALSE;
    const char* path = env->GetStringUTFChars(gamePath, nullptr);
    retro_game_info info{};
    info.path = path;
    info.data = nullptr; // TODO(integration): load into memory when need_fullpath == false
    info.size = 0;
    bool ok = g_core.retro_load_game(&info);
    env->ReleaseStringUTFChars(gamePath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeSetSurface(JNIEnv* env, jobject, jobject surface) {
    if (g_window) { ANativeWindow_release(g_window); g_window = nullptr; }
    if (surface) g_window = ANativeWindow_fromSurface(env, surface);
    // TODO(integration): (re)create the EGL/Vulkan swapchain bound to g_window.
}

JNIEXPORT void JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeRunFrame(JNIEnv*, jobject) {
    if (g_core.retro_run) g_core.retro_run();
}

JNIEXPORT void JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeSetInput(JNIEnv*, jobject, jint port, jint buttons, jint lx, jint ly) {
    if (port == 0) {
        g_buttons = static_cast<int16_t>(buttons);
        g_analog_lx = static_cast<int16_t>(lx);
        g_analog_ly = static_cast<int16_t>(ly);
    }
}

JNIEXPORT jint JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeSerializeSize(JNIEnv*, jobject) {
    return g_core.retro_serialize_size ? static_cast<jint>(g_core.retro_serialize_size()) : 0;
}

JNIEXPORT jbyteArray JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeSerialize(JNIEnv* env, jobject) {
    if (!g_core.retro_serialize || !g_core.retro_serialize_size) return nullptr;
    size_t n = g_core.retro_serialize_size();
    std::vector<uint8_t> buf(n);
    if (!g_core.retro_serialize(buf.data(), n)) return nullptr;
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(n));
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(n), reinterpret_cast<const jbyte*>(buf.data()));
    return arr;
}

JNIEXPORT jboolean JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeUnserialize(JNIEnv* env, jobject, jbyteArray data) {
    if (!g_core.retro_unserialize) return JNI_FALSE;
    jsize n = env->GetArrayLength(data);
    std::vector<uint8_t> buf(n);
    env->GetByteArrayRegion(data, 0, n, reinterpret_cast<jbyte*>(buf.data()));
    return g_core.retro_unserialize(buf.data(), static_cast<size_t>(n)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeUnload(JNIEnv*, jobject) {
    if (g_core.retro_unload_game) g_core.retro_unload_game();
    if (g_core.retro_deinit) g_core.retro_deinit();
    if (g_window) { ANativeWindow_release(g_window); g_window = nullptr; }
    if (g_core.handle) dlclose(g_core.handle);
    g_core = Core{};
}

} // extern "C"
