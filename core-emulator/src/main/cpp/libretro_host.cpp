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
#include <condition_variable>
#include <cstdarg>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <map>
#include <mutex>
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
std::atomic<uint64_t> g_audioFrames{0}; // produced by the core (fast-forward observable)
int g_audioSourceRate = 0;

std::atomic<bool> g_running{false};
std::atomic<bool> g_stopRequested{false};
std::atomic<bool> g_paused{false};
std::atomic<bool> g_swappyEnabled{false};

// Speed control: percent of realtime (100 = 1×). ≥200 batches N retro_runs per presented
// frame (intermediate frames are neither presented nor fed to audio — SKIP_FLIP battery
// mode by construction); 50 = slow-mo (core runs on alternate iterations).
std::atomic<int> g_speedPct{100};
// While true, audio_batch_cb drops samples (non-final runs of a fast-forward batch).
bool g_ffMuteAudio = false; // run-loop thread only
// RetroAchievements-ready interlock: while set, FF/slow-mo/rewind are refused.
std::atomic<bool> g_hardcoreActive{false};

// input snapshot (written by UI/gamepad threads via nativeSetInput)
std::atomic<int32_t> g_buttons{0};
std::atomic<int32_t> g_analogLX{0};
std::atomic<int32_t> g_analogLY{0};

// input->frame latency instrumentation: newest event timestamp not yet sampled by the core
std::atomic<int64_t> g_lastInputEventNs{0};
std::atomic<int64_t> g_inputLatencyUsEma{0};
std::atomic<int64_t> g_inputEventsSampled{0};

// Core variables (settings framework, P11): pushed from Kotlin via nativeSetCoreVariable
// before/at session start and updatable mid-session (GET_VARIABLE_UPDATE handshake).
// GET_VARIABLE hands out pointers into these strings — values are stable std::string
// storage that lives for the process (entries are only ever replaced, keys never erased).
std::mutex g_varsMutex;
std::map<std::string, std::string> g_coreVars;
std::atomic<bool> g_varsDirty{false};

// Run-loop ops: posted from any thread, executed by the run-loop thread between frames
// (retro_serialize/unserialize must run on the emu/GL thread — PPSSPP requires it).
enum class OpType { SAVE, LOAD, SCREENSHOT, REWIND_STEP };
std::mutex g_opMutex;
std::condition_variable g_opCv;
std::atomic<bool> g_opPending{false};
OpType g_opType = OpType::SAVE;
std::string g_opStatePath, g_opFramePath;
bool g_opDone = true;
bool g_opOk = false;

// Rewind: interval snapshots into an in-RAM ring, budgeted by bytes. Owned by the run-loop
// thread; config + count cross threads via atomics.
struct RewindRing {
    std::vector<std::vector<uint8_t>> slots;
    size_t head = 0;   // next write position
    size_t count = 0;  // valid snapshots
    uint64_t lastSnapFrame = 0;
    int intervalFrames = 0;
    bool enabled = false;
};
RewindRing g_rewind;
std::atomic<long long> g_rewindReqBudgetBytes{0}; // 0 = disable
std::atomic<int> g_rewindReqInterval{120};
std::atomic<bool> g_rewindConfigPending{false};
std::atomic<int> g_rewindCount{0};

// Settle gate: after an unserialize (load/rewind), the core must run a few frames before the
// next op is serviced — PPSSPP's threaded GL renderer deadlocks on back-to-back state ops.
// Wall-clock fallback keeps ops flowing when frames are frozen (paused/backgrounded).
uint64_t g_lastRestoreFrame = 0;   // run-loop thread only
int64_t g_lastRestoreTimeNs = 0;

int64_t monotonicNowNs() {
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
}

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

std::atomic<int64_t> g_dbgGetFb{0};
std::atomic<int64_t> g_dbgHwFrames{0}, g_dbgSwFrames{0}, g_dbgDupeFrames{0};

uintptr_t hw_get_current_framebuffer() {
    g_dbgGetFb.fetch_add(1);
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

        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            auto* var = (retro_variable*)data;
            if (!var || !var->key) return false;
            std::lock_guard<std::mutex> lk(g_varsMutex);
            auto it = g_coreVars.find(var->key);
            if (it == g_coreVars.end()) return false; // core uses its own default
            var->value = it->second.c_str();
            return true;
        }

        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
            // The core polls this each frame; a true response makes it re-query GET_VARIABLE
            // for everything (live mid-session setting changes).
            *(bool*)data = g_varsDirty.exchange(false);
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
            LOGI("SET_GEOMETRY %ux%u aspect=%.3f", geo->base_width, geo->base_height, geo->aspect_ratio);
            g_video.setGeometry(geo->base_width, geo->base_height, geo->aspect_ratio);
            return true;
        }

        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO: {
            auto* av = (const retro_system_av_info*)data;
            LOGI("SET_SYSTEM_AV_INFO %ux%u aspect=%.3f fps=%.2f",
                 av->geometry.base_width, av->geometry.base_height,
                 av->geometry.aspect_ratio, av->timing.fps);
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
        int64_t n = g_dbgHwFrames.fetch_add(1);
        if (n < 3 || n == 100) LOGI("video_cb HW #%lld %ux%u", (long long)n, width, height);
        g_video.markHwFrame(width, height);
    } else if (data != nullptr) {
        int64_t n = g_dbgSwFrames.fetch_add(1);
        if (n < 3) LOGI("video_cb SW #%lld %ux%u pitch=%zu", (long long)n, width, height, pitch);
        g_video.submitSoftwareFrame(data, width, height, pitch);
    } else {
        g_dbgDupeFrames.fetch_add(1);
        g_video.markDupeFrame();
    }
}

size_t audio_batch_cb(const int16_t* data, size_t frames) {
    g_audioFrames.fetch_add(frames);
    if (g_ffMuteAudio) return frames; // fast-forward batch: only the final run is audible
    if (data && frames) g_audio.writeFrames(data, frames);
    return frames;
}

void audio_sample_cb(int16_t left, int16_t right) {
    int16_t buf[2] = { left, right };
    audio_batch_cb(buf, 1);
}

void input_poll_cb() {
    // Latency metric: time from the Android input event to the core sampling it here.
    int64_t eventNs = g_lastInputEventNs.exchange(0);
    if (eventNs > 0) {
        int64_t latencyUs = (monotonicNowNs() - eventNs) / 1000;
        if (latencyUs >= 0 && latencyUs < 1000000) {
            int64_t ema = g_inputLatencyUsEma.load();
            g_inputLatencyUsEma = ema == 0 ? latencyUs : (ema * 7 + latencyUs) / 8;
            g_inputEventsSampled.fetch_add(1);
        }
    }
}

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

// ---------------------------------------------------------------------------- state ops

bool writeFileAtomic(const std::string& path, const void* data, size_t len) {
    std::string tmp = path + ".tmp";
    FILE* f = fopen(tmp.c_str(), "wb");
    if (!f) { LOGE("state: fopen(%s) failed", tmp.c_str()); return false; }
    bool ok = fwrite(data, 1, len, f) == len;
    ok = (fclose(f) == 0) && ok;
    if (ok) ok = rename(tmp.c_str(), path.c_str()) == 0;
    if (!ok) { remove(tmp.c_str()); LOGE("state: write %s failed", path.c_str()); }
    return ok;
}

bool doSaveStateOnThread(const std::string& statePath, const std::string& framePath) {
    if (!g_core.retro_serialize || !g_core.retro_serialize_size) return false;
    size_t n = g_core.retro_serialize_size();
    if (n == 0) { LOGE("state: serialize_size = 0"); return false; }
    std::vector<uint8_t> buf(n);
    if (!g_core.retro_serialize(buf.data(), n)) { LOGE("state: retro_serialize failed"); return false; }
    if (!writeFileAtomic(statePath, buf.data(), n)) return false;
    LOGI("state saved: %s (%zu bytes)", statePath.c_str(), n);

    if (!framePath.empty()) {
        // Raw RGBA dump (int32 w, int32 h, then top-down rows); Kotlin turns it into a PNG.
        std::vector<uint8_t> rgba;
        unsigned w = 0, h = 0;
        if (g_video.captureFrame(rgba, w, h)) {
            std::vector<uint8_t> blob(8 + rgba.size());
            int32_t wh[2] = { (int32_t)w, (int32_t)h };
            memcpy(blob.data(), wh, 8);
            memcpy(blob.data() + 8, rgba.data(), rgba.size());
            writeFileAtomic(framePath, blob.data(), blob.size()); // best-effort
        }
    }
    return true;
}

bool doLoadStateOnThread(const std::string& statePath) {
    if (!g_core.retro_unserialize) return false;
    FILE* f = fopen(statePath.c_str(), "rb");
    if (!f) { LOGE("state: open(%s) failed", statePath.c_str()); return false; }
    fseek(f, 0, SEEK_END);
    long len = ftell(f);
    fseek(f, 0, SEEK_SET);
    if (len <= 0) { fclose(f); return false; }
    std::vector<uint8_t> buf((size_t)len);
    bool ok = fread(buf.data(), 1, (size_t)len, f) == (size_t)len;
    fclose(f);
    if (!ok) return false;
    ok = g_core.retro_unserialize(buf.data(), buf.size());
    LOGI("state load %s: %s", statePath.c_str(), ok ? "ok" : "FAILED");
    if (ok) {
        g_lastRestoreFrame = g_video.stats.framesPresented.load();
        g_lastRestoreTimeNs = monotonicNowNs();
    }
    return ok;
}

bool doScreenshotOnThread(const std::string& framePath) {
    std::vector<uint8_t> rgba;
    unsigned w = 0, h = 0;
    if (!g_video.captureFrame(rgba, w, h)) return false;
    std::vector<uint8_t> blob(8 + rgba.size());
    int32_t wh[2] = { (int32_t)w, (int32_t)h };
    memcpy(blob.data(), wh, 8);
    memcpy(blob.data() + 8, rgba.data(), rgba.size());
    return writeFileAtomic(framePath, blob.data(), blob.size());
}

// ---------------------------------------------------------------- rewind (run-loop thread)

void applyRewindConfig() {
    if (!g_rewindConfigPending.exchange(false)) return;
    long long budget = g_rewindReqBudgetBytes.load();
    g_rewind = RewindRing{}; // drop old snapshots
    if (budget <= 0 || !g_core.retro_serialize_size) {
        g_rewindCount = 0;
        LOGI("rewind disabled");
        return;
    }
    size_t snapSize = g_core.retro_serialize_size();
    if (snapSize == 0) {
        g_rewindCount = 0;
        LOGW("rewind unavailable: serialize_size = 0");
        return;
    }
    size_t slots = (size_t)(budget / (long long)snapSize);
    if (slots < 1) slots = 1;
    if (slots > 120) slots = 120;
    g_rewind.slots.resize(slots);
    g_rewind.intervalFrames = g_rewindReqInterval.load();
    if (g_rewind.intervalFrames < 10) g_rewind.intervalFrames = 10;
    g_rewind.enabled = true;
    g_rewind.lastSnapFrame = g_video.stats.framesPresented.load();
    LOGI("rewind enabled: %zu slots x %zu bytes, every %d frames",
         slots, snapSize, g_rewind.intervalFrames);
}

void maybeTakeRewindSnapshot() {
    if (!g_rewind.enabled || g_hardcoreActive.load()) return;
    uint64_t now = g_video.stats.framesPresented.load();
    if (now - g_rewind.lastSnapFrame < (uint64_t)g_rewind.intervalFrames) return;
    g_rewind.lastSnapFrame = now;
    size_t n = g_core.retro_serialize_size();
    if (n == 0) { LOGW("rewind snapshot: serialize_size 0"); return; }
    auto& slot = g_rewind.slots[g_rewind.head];
    slot.resize(n);
    if (!g_core.retro_serialize(slot.data(), n)) {
        LOGW("rewind snapshot: retro_serialize failed at frame %llu", (unsigned long long)now);
        return;
    }
    g_rewind.head = (g_rewind.head + 1) % g_rewind.slots.size();
    if (g_rewind.count < g_rewind.slots.size()) g_rewind.count++;
    g_rewindCount = (int)g_rewind.count;
    LOGI("rewind snapshot %zu/%zu at frame %llu", g_rewind.count, g_rewind.slots.size(),
         (unsigned long long)now);
}

bool doRewindStepOnThread() {
    if (!g_rewind.enabled || g_rewind.count == 0 || g_hardcoreActive.load()) return false;
    g_rewind.head = (g_rewind.head + g_rewind.slots.size() - 1) % g_rewind.slots.size();
    g_rewind.count--;
    g_rewindCount = (int)g_rewind.count;
    auto& slot = g_rewind.slots[g_rewind.head];
    bool ok = !slot.empty() && g_core.retro_unserialize &&
        g_core.retro_unserialize(slot.data(), slot.size());
    // Snapshots resume from the restored point.
    g_rewind.lastSnapFrame = g_video.stats.framesPresented.load();
    if (ok) {
        g_lastRestoreFrame = g_video.stats.framesPresented.load();
        g_lastRestoreTimeNs = monotonicNowNs();
    }
    return ok;
}

/** Run-loop thread: execute a posted op, then wake the waiting caller. */
void processStateOp() {
    if (!g_opPending.load(std::memory_order_acquire)) return;
    // Settle gate: give the core ~12 frames (or 1s wall-clock when frozen) after a restore
    // before the next op touches its state. The op stays pending; the caller keeps waiting.
    if (g_lastRestoreTimeNs != 0) {
        uint64_t framesSince = g_video.stats.framesPresented.load() - g_lastRestoreFrame;
        int64_t nsSince = monotonicNowNs() - g_lastRestoreTimeNs;
        if (framesSince < 12 && nsSince < 1000000000LL) return;
    }
    std::string statePath, framePath;
    OpType type;
    {
        std::lock_guard<std::mutex> lk(g_opMutex);
        statePath = g_opStatePath;
        framePath = g_opFramePath;
        type = g_opType;
    }
    bool ok = false;
    switch (type) {
        case OpType::SAVE: ok = doSaveStateOnThread(statePath, framePath); break;
        case OpType::LOAD: ok = doLoadStateOnThread(statePath); break;
        case OpType::SCREENSHOT: ok = doScreenshotOnThread(framePath); break;
        case OpType::REWIND_STEP: ok = doRewindStepOnThread(); break;
    }
    {
        std::lock_guard<std::mutex> lk(g_opMutex);
        g_opOk = ok;
        g_opDone = true;
        g_opPending = false;
    }
    g_opCv.notify_all();
}

/** Run-loop teardown: fail any op still queued so its caller doesn't wait out the timeout. */
void failPendingStateOp() {
    if (!g_opPending.load()) return;
    {
        std::lock_guard<std::mutex> lk(g_opMutex);
        g_opOk = false;
        g_opDone = true;
        g_opPending = false;
    }
    g_opCv.notify_all();
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
    g_paused = false;
    g_speedPct = 100;
    g_hardcoreActive = false;
    g_rewindReqBudgetBytes = 0;
    g_rewindConfigPending = true; // run loop clears any previous session's ring
    g_lastRestoreFrame = 0;
    g_lastRestoreTimeNs = 0;
    g_supportsNoGame = false;
    g_coreFps = 60.0;
    g_audioFrames = 0;
    g_audioSourceRate = 0;
    g_buttons = 0;
    g_analogLX = 0;
    g_analogLY = 0;
    g_lastInputEventNs = 0;
    g_inputLatencyUsEma = 0;
    g_inputEventsSampled = 0;
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

        if (!g_video.hasWindowSurface() || g_paused.load()) {
            // Backgrounded or explicitly paused (menu open / gamepad unplugged): keep the
            // core alive but frozen; silence audio; don't burn CPU.
            if (g_audio.isRunning()) {
                g_audio.stop();
                resumeFrameMark = g_video.stats.framesPresented.load();
            }
            processStateOp(); // save/load still serviced while frozen (auto-save, menu saves)
            std::this_thread::sleep_for(std::chrono::milliseconds(15));
            nextFrame = std::chrono::steady_clock::now();
            continue;
        }
        if (g_audio.needsRestart()) {
            g_audio.restart(); // output device changed (headphones/BT)
        }

        g_video.callContextResetOnce();
        applyRewindConfig();

        // Speed: ≥200% batches N runs per presented frame (only the last is audible/shown);
        // 50% slow-mo runs the core on alternate iterations.
        const int speed = g_hardcoreActive.load() ? 100 : g_speedPct.load();
        int runs = speed >= 200 ? speed / 100 : 1;
        static bool slowmoSkip = false;
        if (speed == 50) {
            slowmoSkip = !slowmoSkip;
            if (slowmoSkip) runs = 0;
        }
        for (int i = 0; i < runs; i++) {
            g_ffMuteAudio = (i < runs - 1);
            g_audio.updateRateControl();
            g_core.retro_run();
        }
        g_ffMuteAudio = false;
        g_video.present();
        if (speed == 100) maybeTakeRewindSnapshot();
        processStateOp();

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
    LOGI("video_cb tally: hw=%lld sw=%lld dupe=%lld get_fb_calls=%lld",
         (long long)g_dbgHwFrames.load(), (long long)g_dbgSwFrames.load(),
         (long long)g_dbgDupeFrames.load(), (long long)g_dbgGetFb.load());
    // Last chance for a queued op (e.g. auto-save posted right before requestStop), then
    // fail anything that raced in after it.
    processStateOp();
    failPendingStateOp();
    g_rewind = RewindRing{}; // release snapshot RAM
    g_rewindCount = 0;
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

JNIEXPORT void JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeSetPaused(JNIEnv*, jobject, jboolean paused) {
    g_paused = paused == JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeIsPaused(JNIEnv*, jobject) {
    return g_paused.load() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeIsRunning(JNIEnv*, jobject) {
    return g_running.load() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeSetInput(
    JNIEnv*, jobject, jint port, jint buttons, jint lx, jint ly, jlong eventTimeNs) {
    if (port == 0) {
        g_buttons = buttons;
        g_analogLX = lx;
        g_analogLY = ly;
        if (eventTimeNs > 0) g_lastInputEventNs = eventTimeNs;
    }
}

JNIEXPORT jint JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeDebugButtons(JNIEnv*, jobject) {
    return (jint)g_buttons.load();
}

JNIEXPORT jlong JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeInputLatencyUsEma(JNIEnv*, jobject) {
    return (jlong)g_inputLatencyUsEma.load();
}

JNIEXPORT jlong JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeInputEventsSampled(JNIEnv*, jobject) {
    return (jlong)g_inputEventsSampled.load();
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

// Audio frames PRODUCED by the core (counts muted FF batches too) — the observable that
// scales with emulation speed, unlike framesPresented which stays at the display rate.
JNIEXPORT jlong JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeAudioFramesProduced(JNIEnv*, jobject) {
    return (jlong)g_audioFrames.load();
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

// ---- run-loop ops (P8/P10): blocking, executed by the run-loop thread ----

static jboolean postOp(JNIEnv* env, OpType type, jstring statePath, jstring framePath) {
    if (!g_running.load()) return JNI_FALSE;
    auto grab = [&](jstring s) -> std::string {
        if (!s) return {};
        const char* c = env->GetStringUTFChars(s, nullptr);
        std::string out(c ? c : "");
        env->ReleaseStringUTFChars(s, c);
        return out;
    };
    std::string statePathStr = grab(statePath);
    std::string framePathStr = grab(framePath);
    if (type == OpType::SAVE || type == OpType::LOAD) {
        if (statePathStr.empty()) return JNI_FALSE;
    }
    if (type == OpType::SCREENSHOT && framePathStr.empty()) return JNI_FALSE;

    std::unique_lock<std::mutex> lk(g_opMutex);
    if (g_opPending.load()) return JNI_FALSE; // one op at a time
    g_opType = type;
    g_opStatePath = statePathStr;
    g_opFramePath = framePathStr;
    g_opDone = false;
    g_opOk = false;
    g_opPending.store(true, std::memory_order_release);
    // PPSSPP serialize of a big session can take a moment; 20s is generous headroom.
    bool done = g_opCv.wait_for(lk, std::chrono::seconds(20), [] { return g_opDone; });
    return (done && g_opOk) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeSaveState(
    JNIEnv* env, jobject, jstring statePath, jstring rawFramePath) {
    return postOp(env, OpType::SAVE, statePath, rawFramePath);
}

JNIEXPORT jboolean JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeLoadState(JNIEnv* env, jobject, jstring statePath) {
    return postOp(env, OpType::LOAD, statePath, nullptr);
}

JNIEXPORT jboolean JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeScreenshot(JNIEnv* env, jobject, jstring rawFramePath) {
    return postOp(env, OpType::SCREENSHOT, nullptr, rawFramePath);
}

// ---- speed / rewind / hardcore (P10) ----

JNIEXPORT void JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeSetSpeed(JNIEnv*, jobject, jint pct) {
    if (g_hardcoreActive.load()) { g_speedPct = 100; return; }
    int v = pct;
    if (v != 50 && v < 100) v = 100;
    if (v > 500) v = 500;
    g_speedPct = v;
}

JNIEXPORT jint JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeGetSpeed(JNIEnv*, jobject) {
    return g_speedPct.load();
}

JNIEXPORT void JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeSetRewind(
    JNIEnv*, jobject, jlong budgetBytes, jint intervalFrames) {
    g_rewindReqBudgetBytes = (long long)budgetBytes;
    g_rewindReqInterval = intervalFrames;
    g_rewindConfigPending = true;
}

JNIEXPORT jint JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeRewindCount(JNIEnv*, jobject) {
    return g_rewindCount.load();
}

JNIEXPORT jboolean JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeRewindStep(JNIEnv* env, jobject) {
    return postOp(env, OpType::REWIND_STEP, nullptr, nullptr);
}

JNIEXPORT void JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeSetHardcore(JNIEnv*, jobject, jboolean on) {
    g_hardcoreActive = on == JNI_TRUE;
    if (on == JNI_TRUE) g_speedPct = 100;
}

// ---- core variables (P11 settings framework) ----

JNIEXPORT void JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeSetCoreVariable(
    JNIEnv* env, jobject, jstring key, jstring value) {
    const char* k = env->GetStringUTFChars(key, nullptr);
    const char* v = env->GetStringUTFChars(value, nullptr);
    if (k && v) {
        std::lock_guard<std::mutex> lk(g_varsMutex);
        auto it = g_coreVars.find(k);
        if (it == g_coreVars.end() || it->second != v) {
            g_coreVars[k] = v;
            g_varsDirty = true;
            LOGI("core var %s = %s", k, v);
        }
    }
    env->ReleaseStringUTFChars(key, k);
    env->ReleaseStringUTFChars(value, v);
}

JNIEXPORT jstring JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeGetCoreVariable(JNIEnv* env, jobject, jstring key) {
    const char* k = env->GetStringUTFChars(key, nullptr);
    std::string out;
    bool found = false;
    if (k) {
        std::lock_guard<std::mutex> lk(g_varsMutex);
        auto it = g_coreVars.find(k);
        if (it != g_coreVars.end()) { out = it->second; found = true; }
    }
    env->ReleaseStringUTFChars(key, k);
    return found ? env->NewStringUTF(out.c_str()) : nullptr;
}

JNIEXPORT jboolean JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeVariablesDirty(JNIEnv*, jobject) {
    return g_varsDirty.load() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_retrovault_emulator_LibretroBridge_nativeIsHardcore(JNIEnv*, jobject) {
    return g_hardcoreActive.load() ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
