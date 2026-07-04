// Pulsar native libretro host.
//
// Owns the emulator session on a single render thread: dlopen the core, drive the retro_*
// lifecycle, provide the GL video backbone (video_gl.*), pace frames with Swappy (AGDK
// games-frame-pacing) and expose a minimal JNI surface to com.retrovault.emulator.LibretroBridge.
//
// Threading model:
//   - nativeStartSession stores parameters; nativeRunLoop (called on a dedicated Java thread)
//     performs EGL init + core load + game load + the frame loop, so that environment callbacks
//     issued during retro_load_game (notably SET_HW_RENDER) run with the GL context current.
//   - nativeSetVideoSurface may be called from the main thread at any time (mutex handoff).
//   - Input state is written from UI/gamepad threads and read here (atomics).

#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <atomic>
#include <chrono>
#include <cstdarg>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>
#include <thread>
#include <vector>

#include "libretro.h"
#include "video_gl.h"
#include "audio_out.h"

#include "swappy/swappyGL.h"

#define LOG_TAG "pulsar_retro"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// ---------------------------------------------------------------------------- core symbols

struct Core {
    void* handle = nullptr;
    void (*retro_init)() = nullptr;
    void (*retro_deinit)() = nullptr;
    unsigned (*retro_api_version)() = nullptr;
    void (*retro_get_system_info)(retro_system_info*) = nullptr;
    void (*retro_get_system_av_info)(retro_system_av_info*) = nullptr;
    void (*retro_set_environment)(retro_environment_t) = nullptr;
    void (*retro_set_video_refresh)(retro_video_refresh_t) = nullptr;
    void (*retro_set_audio_sample)(retro_audio_sample_t) = nullptr;
    void (*retro_set_audio_sample_batch)(retro_audio_sample_batch_t) = nullptr;
    void (*retro_set_input_poll)(retro_input_poll_t) = nullptr;
    void (*retro_set_input_state)(retro_input_state_t) = nullptr;
    void (*retro_set_controller_port_device)(unsigned, unsigned) = nullptr;
    bool (*retro_load_game)(const retro_game_info*) = nullptr;
    void (*retro_unload_game)() = nullptr;
    void (*retro_run)() = nullptr;
    size_t (*retro_serialize_size)() = nullptr;
    bool (*retro_serialize)(void*, size_t) = nullptr;
    bool (*retro_unserialize)(const void*, size_t) = nullptr;
};

template <typename T>
T sym(void* h, const char* name) { return reinterpret_cast<T>(dlsym(h, name)); }

// ---------------------------------------------------------------------------- session state

Core g_core;
VideoGL g_video;
AudioOut g_audio;

std::string g_corePath, g_gamePath, g_systemDir, g_saveDir;
bool g_supportsNoGame = false;
double g_coreFps = 60.0;
uint64_t g_audioFrames = 0;
int g_audioSourceRate = 0;

std::atomic<bool> g_running{false};
std::atomic<bool> g_stopRequested{false};
std::atomic<bool> g_swappyEnabled{false};

// input snapshot (written by UI/gamepad threads via nativeSetInput)
std::atomic<int32_t> g_buttons{0};
std::atomic<int32_t> g_analogLX{0};
std::atomic<int32_t> g_analogLY{0};

// ---------------------------------------------------------------------------- callbacks

void core_log(enum retro_log_level level, const char* fmt, ...) {
    char buf[1024];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);
    int prio = ANDROID_LOG_INFO;
    switch (level) {
        case RETRO_LOG_DEBUG: prio = ANDROID_LOG_DEBUG; break;
        case RETRO_LOG_INFO:  prio = ANDROID_LOG_INFO;  break;
        case RETRO_LOG_WARN:  prio = ANDROID_LOG_WARN;  break;
        case RETRO_LOG_ERROR: prio = ANDROID_LOG_ERROR; break;
        default: break;
    }
    __android_log_write(prio, "pulsar_core", buf);
}

uintptr_t hw_get_current_framebuffer() {
    return g_video.currentFramebuffer();
}

retro_proc_address_t hw_get_proc_address(const char* symName) {
    return (retro_proc_address_t)eglGetProcAddress(symName);
}

bool env_cb(unsigned cmd, void* data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            *(bool*)data = true;
            return true;

        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            auto fmt = *(const enum retro_pixel_format*)data;
            if (fmt != RETRO_PIXEL_FORMAT_0RGB1555 && fmt != RETRO_PIXEL_FORMAT_XRGB8888 &&
                fmt != RETRO_PIXEL_FORMAT_RGB565) return false;
            g_video.setPixelFormat(fmt);
            LOGI("pixel format set: %d", (int)fmt);
            return true;
        }

        case RETRO_ENVIRONMENT_SET_HW_RENDER: {
            auto* cb = (retro_hw_render_callback*)data;
            // Query av_info is not yet available during load — size the FBO generously;
            // PSP at 5x internal ≈ 2400x1360; test cores are tiny. 4096 is safe on ES3.
            if (!g_video.enableHwRender(cb, 4096, 4096)) return false;
            cb->get_current_framebuffer = hw_get_current_framebuffer;
            cb->get_proc_address = hw_get_proc_address;
            return true;
        }

        case RETRO_ENVIRONMENT_GET_PREFERRED_HW_RENDER:
            *(unsigned*)data = RETRO_HW_CONTEXT_OPENGLES3;
            return true;

        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
            ((retro_log_callback*)data)->log = core_log;
            return true;

        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            *(const char**)data = g_systemDir.empty() ? nullptr : g_systemDir.c_str();
            return !g_systemDir.empty();

        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            *(const char**)data = g_saveDir.empty() ? nullptr : g_saveDir.c_str();
            return !g_saveDir.empty();

        case RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME:
            g_supportsNoGame = *(const bool*)data;
            return true;

        case RETRO_ENVIRONMENT_GET_VARIABLE:
            return false; // core option defaults for now (P11 wires the settings framework)

        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
            *(bool*)data = false;
            return true;

        case RETRO_ENVIRONMENT_SET_VARIABLES:
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS:
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_INTL:
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2:
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2_INTL:
            return true; // accepted; storage arrives with the settings framework

        case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION:
            *(unsigned*)data = 2;
            return true;

        case RETRO_ENVIRONMENT_GET_LANGUAGE:
            *(unsigned*)data = RETRO_LANGUAGE_ENGLISH;
            return true;

        case RETRO_ENVIRONMENT_SET_GEOMETRY: {
            auto* geo = (const retro_game_geometry*)data;
            g_video.setGeometry(geo->base_width, geo->base_height, geo->aspect_ratio);
            return true;
        }

        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO: {
            auto* av = (const retro_system_av_info*)data;
            g_video.setGeometry(av->geometry.base_width, av->geometry.base_height,
                                av->geometry.aspect_ratio);
            if (av->timing.fps > 0) g_coreFps = av->timing.fps;
            return true;
        }

        default:
            return false;
    }
}

void video_cb(const void* data, unsigned width, unsigned height, size_t pitch) {
    if (data == RETRO_HW_FRAME_BUFFER_VALID) {
        g_video.markHwFrame(width, height);
    } else if (data != nullptr) {
        g_video.submitSoftwareFrame(data, width, height, pitch);
    } else {
        g_video.markDupeFrame();
    }
}

size_t audio_batch_cb(const int16_t* data, size_t frames) {
    g_audioFrames += frames;
    if (data && frames) g_audio.writeFrames(data, frames);
    return frames;
}

void audio_sample_cb(int16_t left, int16_t right) {
    int16_t buf[2] = { left, right };
    audio_batch_cb(buf, 1);
}

void input_poll_cb() {}

int16_t input_state_cb(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port != 0) return 0;
    if (device == RETRO_DEVICE_JOYPAD) {
        if (id == RETRO_DEVICE_ID_JOYPAD_MASK) return (int16_t)(g_buttons.load() & 0xFFFF);
        return (int16_t)((g_buttons.load() >> id) & 1);
    }
    if (device == RETRO_DEVICE_ANALOG && index == RETRO_DEVICE_INDEX_ANALOG_LEFT) {
        if (id == RETRO_DEVICE_ID_ANALOG_X) return (int16_t)g_analogLX.load();
        if (id == RETRO_DEVICE_ID_ANALOG_Y) return (int16_t)g_analogLY.load();
    }
    return 0;
}

// ---------------------------------------------------------------------------- core lifecycle

bool loadCoreOnThread() {
    g_core.handle = dlopen(g_corePath.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (!g_core.handle) {
        LOGE("dlopen failed: %s", dlerror());
        return false;
    }
    void* h = g_core.handle;
    g_core.retro_init = sym<void (*)()>(h, "retro_init");
    g_core.retro_deinit = sym<void (*)()>(h, "retro_deinit");
    g_core.retro_api_version = sym<unsigned (*)()>(h, "retro_api_version");
    g_core.retro_get_system_info = sym<void (*)(retro_system_info*)>(h, "retro_get_system_info");
    g_core.retro_get_system_av_info = sym<void (*)(retro_system_av_info*)>(h, "retro_get_system_av_info");
    g_core.retro_set_environment = sym<void (*)(retro_environment_t)>(h, "retro_set_environment");
    g_core.retro_set_video_refresh = sym<void (*)(retro_video_refresh_t)>(h, "retro_set_video_refresh");
    g_core.retro_set_audio_sample = sym<void (*)(retro_audio_sample_t)>(h, "retro_set_audio_sample");
    g_core.retro_set_audio_sample_batch = sym<void (*)(retro_audio_sample_batch_t)>(h, "retro_set_audio_sample_batch");
    g_core.retro_set_input_poll = sym<void (*)(retro_input_poll_t)>(h, "retro_set_input_poll");
    g_core.retro_set_input_state = sym<void (*)(retro_input_state_t)>(h, "retro_set_input_state");
    g_core.retro_set_controller_port_device = sym<void (*)(unsigned, unsigned)>(h, "retro_set_controller_port_device");
    g_core.retro_load_game = sym<bool (*)(const retro_game_info*)>(h, "retro_load_game");
    g_core.retro_unload_game = sym<void (*)()>(h, "retro_unload_game");
    g_core.retro_run = sym<void (*)()>(h, "retro_run");
    g_core.retro_serialize_size = sym<size_t (*)()>(h, "retro_serialize_size");
    g_core.retro_serialize = sym<bool (*)(void*, size_t)>(h, "retro_serialize");
    g_core.retro_unserialize = sym<bool (*)(const void*, size_t)>(h, "retro_unserialize");

    if (!g_core.retro_init || !g_core.retro_run || !g_core.retro_load_game ||
        !g_core.retro_set_environment) {
        LOGE("core missing required symbols");
        dlclose(g_core.handle);
        g_core = Core{};
        return false;
    }

    g_core.retro_set_environment(env_cb); // must precede retro_init
    g_core.retro_set_video_refresh(video_cb);
    if (g_core.retro_set_audio_sample) g_core.retro_set_audio_sample(audio_sample_cb);
    g_core.retro_set_audio_sample_batch(audio_batch_cb);
    g_core.retro_set_input_poll(input_poll_cb);
    g_core.retro_set_input_state(input_state_cb);
    g_core.retro_init();

    retro_system_info info{};
    if (g_core.retro_get_system_info) {
        g_core.retro_get_system_info(&info);
        LOGI("core initialized: %s %s", info.library_name, info.library_version);
    }
    return true;
}

void unloadCoreOnThread() {
    if (!g_core.handle) return;
    if (g_core.retro_unload_game) g_core.retro_unload_game();
    if (g_core.retro_deinit) g_core.retro_deinit();
    dlclose(g_core.handle);
    g_core = Core{};
}

} // namespace

// Swappy hook used by VideoGL::present().
bool pulsar_swappy_swap(EGLDisplay display, EGLSurface surface) {
    if (!g_swappyEnabled.load()) return false;
    return SwappyGL_swap(display, surface);
}

// ---------------------------------------------------------------------------- JNI

extern "C" {

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
    // Newline-separated: extensions are themselves pipe-delimited (e.g. "elf|iso|cso").
    char buf[512];
    snprintf(buf, sizeof(buf), "%s\n%s\n%s\n%u",
             info.library_name ? info.library_name : "?",
             info.library_version ? info.library_version : "?",
             info.valid_extensions ? info.valid_extensions : "",
             api_version());
    dlclose(handle);
    return env->NewStringUTF(buf);
}

JNIEXPORT void JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeInitSwappy(JNIEnv* env, jobject, jobject activity) {
    if (SwappyGL_init(env, activity)) {
        SwappyGL_setSwapIntervalNS(SWAPPY_SWAP_60FPS);
        g_swappyEnabled = true;
        LOGI("SwappyGL initialized");
    } else {
        g_swappyEnabled = false;
        LOGW("SwappyGL unavailable — falling back to eglSwapBuffers pacing");
    }
    g_video.stats.swappyActive = g_swappyEnabled.load();
}

JNIEXPORT void JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeStartSession(
    JNIEnv* env, jobject, jstring corePath, jstring gamePath, jstring systemDir, jstring saveDir) {
    auto grab = [&](jstring s) -> std::string {
        if (!s) return {};
        const char* c = env->GetStringUTFChars(s, nullptr);
        std::string out(c ? c : "");
        env->ReleaseStringUTFChars(s, c);
        return out;
    };
    g_corePath = grab(corePath);
    g_gamePath = grab(gamePath);
    g_systemDir = grab(systemDir);
    g_saveDir = grab(saveDir);
    // Reset per-session state so back-to-back sessions in one process start clean.
    g_stopRequested = false;
    g_supportsNoGame = false;
    g_coreFps = 60.0;
    g_audioFrames = 0;
    g_audioSourceRate = 0;
    g_buttons = 0;
    g_analogLX = 0;
    g_analogLY = 0;
    g_video.stats.framesPresented = 0;
    g_video.stats.framesDuped = 0;
    g_video.stats.avgFrameIntervalUs = 0;
    LOGI("session staged: core=%s game=%s", g_corePath.c_str(),
         g_gamePath.empty() ? "<none>" : g_gamePath.c_str());
}

JNIEXPORT void JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeSetVideoSurface(JNIEnv* env, jobject, jobject surface) {
    ANativeWindow* window = surface ? ANativeWindow_fromSurface(env, surface) : nullptr;
    g_video.setPendingWindow(window);
    if (g_swappyEnabled.load()) SwappyGL_setWindow(window);
}

JNIEXPORT jboolean JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeRunLoop(JNIEnv*, jobject) {
    if (g_running.exchange(true)) { LOGE("run loop already active"); return JNI_FALSE; }

    bool ok = false;
    do {
        if (!g_video.initDisplay()) break;
        if (!loadCoreOnThread()) break;

        retro_game_info gameInfo{};
        const retro_game_info* infoPtr = nullptr;
        if (!g_gamePath.empty()) {
            gameInfo.path = g_gamePath.c_str();
            infoPtr = &gameInfo;
        } else if (!g_supportsNoGame) {
            LOGE("no game path and core does not support no-game");
            break;
        }
        if (!g_core.retro_load_game(infoPtr)) {
            LOGE("retro_load_game failed");
            break;
        }

        retro_system_av_info av{};
        if (g_core.retro_get_system_av_info) {
            g_core.retro_get_system_av_info(&av);
            g_video.setGeometry(av.geometry.base_width, av.geometry.base_height,
                                av.geometry.aspect_ratio);
            if (av.timing.fps > 0) g_coreFps = av.timing.fps;
            LOGI("av_info: %ux%u aspect=%.3f fps=%.2f audio=%.0fHz", av.geometry.base_width,
                 av.geometry.base_height, av.geometry.aspect_ratio, av.timing.fps,
                 av.timing.sample_rate);
            // Audio starts LAZILY once the video pipeline is warm (see run loop) — starting
            // the audio clock while early frames are still slow drains the ring beyond what
            // ±0.5% rate control can recover from.
            g_audioSourceRate = (int)av.timing.sample_rate;
        }
        if (g_core.retro_set_controller_port_device) {
            g_core.retro_set_controller_port_device(0, RETRO_DEVICE_JOYPAD);
        }
        ok = true;
    } while (false);

    if (!ok) {
        g_audio.stop();
        g_video.notifyContextDestroy(); // while the core (if any) is still loaded
        unloadCoreOnThread();
        g_video.shutdown();
        g_running = false;
        return JNI_FALSE;
    }

    const auto framePeriod = std::chrono::nanoseconds(
        (int64_t)(1e9 / (g_coreFps > 1.0 ? g_coreFps : 60.0)));
    auto nextFrame = std::chrono::steady_clock::now();
    LOGI("entering run loop (fps=%.2f, swappy=%d)", g_coreFps, (int)g_swappyEnabled.load());

    // Audio starts after this many presented frames — lets JIT/driver warmup pass so the
    // producer cadence is stable before the audio clock starts consuming.
    constexpr uint64_t kAudioWarmupFrames = 15;
    uint64_t resumeFrameMark = 0;

    while (!g_stopRequested.load()) {
        g_video.applyPendingWindow();

        if (!g_video.hasWindowSurface()) {
            // Backgrounded: keep the core alive but paused; silence audio; don't burn CPU.
            if (g_audio.isRunning()) {
                g_audio.stop();
                resumeFrameMark = g_video.stats.framesPresented.load();
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(15));
            nextFrame = std::chrono::steady_clock::now();
            continue;
        }
        if (g_audio.needsRestart()) {
            g_audio.restart(); // output device changed (headphones/BT)
        }

        g_video.callContextResetOnce();
        g_audio.updateRateControl();
        g_core.retro_run();
        g_video.present();

        if (!g_audio.isRunning() && g_audioSourceRate > 0 &&
            g_video.stats.framesPresented.load() >= resumeFrameMark + kAudioWarmupFrames) {
            g_audio.start(g_audioSourceRate);
        }

        if (!g_swappyEnabled.load()) {
            // Manual pacing fallback: sleep out the remainder of the frame budget.
            nextFrame += framePeriod;
            auto now = std::chrono::steady_clock::now();
            if (nextFrame > now) {
                std::this_thread::sleep_until(nextFrame);
            } else if (now - nextFrame > std::chrono::milliseconds(100)) {
                nextFrame = now; // fell way behind — resync
            }
        }
    }

    LOGI("run loop exiting: %llu frames presented",
         (unsigned long long)g_video.stats.framesPresented.load());
    // Teardown order matters: audio stops first (its producer is the core thread), then
    // context_destroy fires while the core is still loaded and the GL context is current;
    // only then unload/dlclose the core; EGL goes down last.
    g_audio.stop();
    g_video.notifyContextDestroy();
    unloadCoreOnThread();
    g_video.shutdown();
    g_running = false;
    g_stopRequested = false;
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeRequestStop(JNIEnv*, jobject) {
    g_stopRequested = true;
}

JNIEXPORT jboolean JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeIsRunning(JNIEnv*, jobject) {
    return g_running.load() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeSetInput(JNIEnv*, jobject, jint port, jint buttons, jint lx, jint ly) {
    if (port == 0) {
        g_buttons = buttons;
        g_analogLX = lx;
        g_analogLY = ly;
    }
}

JNIEXPORT jlong JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeFramesPresented(JNIEnv*, jobject) {
    return (jlong)g_video.stats.framesPresented.load();
}

JNIEXPORT jlong JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeAvgFrameIntervalUs(JNIEnv*, jobject) {
    return (jlong)g_video.stats.avgFrameIntervalUs.load();
}

JNIEXPORT jboolean JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeSwappyActive(JNIEnv*, jobject) {
    return g_video.stats.swappyActive.load() ? JNI_TRUE : JNI_FALSE;
}

// ---- audio stats / config ----

JNIEXPORT jlong JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeAudioFramesOut(JNIEnv*, jobject) {
    return (jlong)g_audio.framesConsumed();
}

JNIEXPORT jlong JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeAudioUnderruns(JNIEnv*, jobject) {
    return (jlong)g_audio.underrunFills();
}

JNIEXPORT jint JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeAudioFillPct(JNIEnv*, jobject) {
    return (jint)(g_audio.fillRatio() * 100.0);
}

// rate-control deviation ×1e6 (e.g. +3000 = producing 0.3% extra frames)
JNIEXPORT jint JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeAudioRateDeltaPpm(JNIEnv*, jobject) {
    return (jint)(g_audio.rateDelta() * 1e6);
}

JNIEXPORT jint JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeAudioDeviceRate(JNIEnv*, jobject) {
    return (jint)g_audio.deviceSampleRate();
}

JNIEXPORT void JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeSetBtFriendlyAudio(JNIEnv*, jobject, jboolean bt) {
    g_audio.setBluetoothFriendly(bt == JNI_TRUE);
}

JNIEXPORT jint JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeSerializeSize(JNIEnv*, jobject) {
    return g_core.retro_serialize_size ? (jint)g_core.retro_serialize_size() : 0;
}

JNIEXPORT jbyteArray JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeSerialize(JNIEnv* env, jobject) {
    if (!g_core.retro_serialize || !g_core.retro_serialize_size) return nullptr;
    size_t n = g_core.retro_serialize_size();
    std::vector<uint8_t> buf(n);
    if (!g_core.retro_serialize(buf.data(), n)) return nullptr;
    jbyteArray arr = env->NewByteArray((jsize)n);
    env->SetByteArrayRegion(arr, 0, (jsize)n, (const jbyte*)buf.data());
    return arr;
}

JNIEXPORT jboolean JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeUnserialize(JNIEnv* env, jobject, jbyteArray data) {
    if (!g_core.retro_unserialize) return JNI_FALSE;
    jsize n = env->GetArrayLength(data);
    std::vector<uint8_t> buf((size_t)n);
    env->GetByteArrayRegion(data, 0, n, (jbyte*)buf.data());
    return g_core.retro_unserialize(buf.data(), (size_t)n) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
