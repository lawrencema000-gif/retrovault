// RetroAchievements bridge — rcheevos v11.6.0 rc_client, wired to the PPSSPP libretro core.
//
// Threading contract (single-owner): every rc_client_* call runs on the emulator run-loop thread.
// JNI setters only ENQUEUE commands; the OkHttp pump thread only enqueues HTTP requests/responses;
// rc_bridge_service() (run-loop thread) drains both queues and is the ONLY place rc_client
// callbacks are invoked. No native->JVM upcalls, so no JavaVM/AttachCurrentThread is needed.
//
// The live path (login + game load + real unlocks) needs an RA account and RA-side approval of
// Pulsar as a hardcore-compliant emulator; it is compiled here and driven by Kotlin, but exercised
// only in the staged RA-account session. The offline self-tests + the hardcore interlock are
// validated on the emulator.

#include "rc_bridge.h"
#include "rc_host.h"

#include "rc_client.h"
#include "rc_hash.h"
#include "rc_consoles.h"
#include "rc_version.h"                     // rc_version_string() ("11.6")
#include "rcheevos/src/rc_libretro.h"

#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <condition_variable>
#include <cstring>
#include <ctime>
#include <deque>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>

#define LOG_TAG "pulsar_ra"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// ---- client + memory state (run-loop thread owns g_raClient) --------------------------------
rc_client_t* g_raClient = nullptr;
rc_libretro_memory_regions_t g_raRegions{};
std::atomic<bool> g_raActive{false};
std::atomic<bool> g_raGameLoaded{false};
std::atomic<uint32_t> g_raGeneration{0};   // bumped on teardown; stale HTTP completions are dropped

int64_t nowNs() {
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
}

// ---- HTTP dispatcher: rc_client(run-loop) <-> Kotlin OkHttp pump ----------------------------
struct RaHttpRequest {
    uint64_t id = 0;
    std::string url, postData, contentType;
    rc_client_server_callback_t callback = nullptr;
    void* callbackData = nullptr;
    uint32_t generation = 0;
};
struct RaHttpResponse {
    uint64_t id = 0;
    int httpStatus = 0;
    std::vector<uint8_t> body;
};

std::mutex g_httpMx;
std::condition_variable g_httpCv;
std::deque<RaHttpRequest> g_httpOutbox;                       // run-loop -> pump
std::unordered_map<uint64_t, RaHttpRequest> g_httpInflight;   // dispatched, awaiting completion
std::deque<RaHttpResponse> g_httpInbox;                       // pump -> run-loop
std::atomic<uint64_t> g_httpNextId{1};

// ---- command queue: JNI(any thread) -> run-loop ---------------------------------------------
enum class RaCmd { Begin, LoginPw, LoginToken, Logout, StartGamePath, StartGameHash, ChangeMedia,
                   SetHardcore, DropToSoftcore, Unload };
struct RaCommand {
    RaCmd cmd;
    std::string a, b;
    uint32_t console = 0;
    int flag = 0;
};
std::mutex g_cmdMx;
std::deque<RaCommand> g_cmdQueue;
rc_client_async_handle_t* g_loginHandle = nullptr;
rc_client_async_handle_t* g_loadHandle = nullptr;

// ---- event channel + UI snapshots -----------------------------------------------------------
std::mutex g_eventMx;
std::deque<std::string> g_eventQueue;
std::mutex g_snapMx;
std::string g_listJson = "{}";
std::string g_summaryJson = "{}";

void pushEvent(const std::string& json) {
    std::lock_guard<std::mutex> lk(g_eventMx);
    g_eventQueue.push_back(json);
}

// Minimal JSON string escaper (control chars + quote + backslash).
std::string jesc(const char* s) {
    std::string out;
    if (!s) return out;
    for (const char* p = s; *p; ++p) {
        unsigned char c = (unsigned char)*p;
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default:
                if (c < 0x20) { char b[8]; snprintf(b, sizeof(b), "\\u%04x", c); out += b; }
                else out += (char)c;
        }
    }
    return out;
}

// ---- rc_client callback shims (all RC_CCONV, all on the run-loop thread) ---------------------

void RC_CCONV get_core_memory_info(uint32_t id, rc_libretro_core_memory_info_t* info) {
    unsigned char* data = nullptr;
    size_t size = 0;
    rc_host_get_core_memory(id, &data, &size);
    info->data = data;
    info->size = size;
}

uint32_t RC_CCONV ra_read_memory(uint32_t address, uint8_t* buffer, uint32_t num_bytes, rc_client_t*) {
    return rc_libretro_memory_read(&g_raRegions, address, buffer, num_bytes);
}

// rc_client emits an API request on the run-loop thread; copy it and hand it to the pump.
void RC_CCONV ra_server_call(const rc_api_request_t* req, rc_client_server_callback_t cb,
                             void* cbdata, rc_client_t*) {
    RaHttpRequest r;
    r.id = g_httpNextId.fetch_add(1);
    r.url = req->url ? req->url : "";
    r.postData = req->post_data ? req->post_data : "";
    r.contentType = req->content_type ? req->content_type : "";
    r.callback = cb;
    r.callbackData = cbdata;
    r.generation = g_raGeneration.load();
    {
        std::lock_guard<std::mutex> lk(g_httpMx);
        g_httpOutbox.push_back(std::move(r));
    }
    g_httpCv.notify_one();
}

rc_clock_t RC_CCONV ra_get_time_ms(const rc_client_t*) {
    return (rc_clock_t)(nowNs() / 1000000LL);
}

void RC_CCONV ra_log(const char* message, const rc_client_t*) {
    LOGI("rc_client: %s", message ? message : "");
}

// Build the per-game achievement list snapshot (run-loop thread) into g_listJson.
void rebuildAchievementList() {
    std::string json = "{\"buckets\":[";
    if (g_raClient && rc_client_has_achievements(g_raClient)) {
        rc_client_achievement_list_t* list = rc_client_create_achievement_list(
            g_raClient, RC_CLIENT_ACHIEVEMENT_CATEGORY_CORE,
            RC_CLIENT_ACHIEVEMENT_LIST_GROUPING_LOCK_STATE);
        if (list) {
            for (uint32_t b = 0; b < list->num_buckets; ++b) {
                const rc_client_achievement_bucket_t& bk = list->buckets[b];
                if (b) json += ",";
                json += "{\"label\":\"" + jesc(bk.label) + "\",\"type\":" + std::to_string(bk.bucket_type) + ",\"achievements\":[";
                for (uint32_t i = 0; i < bk.num_achievements; ++i) {
                    const rc_client_achievement_t* a = bk.achievements[i];
                    if (i) json += ",";
                    json += "{\"id\":" + std::to_string(a->id) +
                            ",\"title\":\"" + jesc(a->title) + "\"" +
                            ",\"description\":\"" + jesc(a->description) + "\"" +
                            ",\"badge\":\"" + jesc(a->badge_name) + "\"" +
                            ",\"points\":" + std::to_string(a->points) +
                            ",\"state\":" + std::to_string(a->state) +
                            ",\"unlocked\":" + std::to_string(a->unlocked) +
                            ",\"progress\":\"" + jesc(a->measured_progress) + "\"" +
                            ",\"percent\":" + std::to_string(a->measured_percent) + "}";
                }
                json += "]}";
            }
            rc_client_destroy_achievement_list(list);
        }
    }
    json += "]}";
    std::lock_guard<std::mutex> lk(g_snapMx);
    g_listJson = std::move(json);
}

// Build the "unlocked X of Y" summary snapshot (run-loop thread) into g_summaryJson. Counts come
// from rc_client_get_user_game_summary; title/badge come from rc_client_get_game_info.
void rebuildSummary() {
    std::string json = "{}";
    if (g_raClient) {
        rc_client_user_game_summary_t s{};
        rc_client_get_user_game_summary(g_raClient, &s);
        const rc_client_game_t* g = rc_client_get_game_info(g_raClient);
        json = std::string("{\"title\":\"") + jesc(g ? g->title : "") + "\"" +
               ",\"badge\":\"" + jesc(g ? g->badge_name : "") + "\"" +
               ",\"num_core\":" + std::to_string(s.num_core_achievements) +
               ",\"num_unlocked\":" + std::to_string(s.num_unlocked_achievements) +
               ",\"num_unsupported\":" + std::to_string(s.num_unsupported_achievements) +
               ",\"points_core\":" + std::to_string(s.points_core) +
               ",\"points_unlocked\":" + std::to_string(s.points_unlocked) + "}";
    }
    std::lock_guard<std::mutex> lk(g_snapMx);
    g_summaryJson = std::move(json);
}

void RC_CCONV ra_event_handler(const rc_client_event_t* event, rc_client_t* client) {
    switch (event->type) {
        case RC_CLIENT_EVENT_ACHIEVEMENT_TRIGGERED: {
            const rc_client_achievement_t* a = event->achievement;
            pushEvent(std::string("{\"type\":\"unlock\",\"id\":") + std::to_string(a ? a->id : 0) +
                      ",\"title\":\"" + jesc(a ? a->title : "") + "\"" +
                      ",\"points\":" + std::to_string(a ? a->points : 0) +
                      ",\"badge\":\"" + jesc(a ? a->badge_name : "") + "\"" +
                      ",\"hardcore\":" + std::to_string(rc_client_get_hardcore_enabled(client)) + "}");
            rebuildAchievementList();
            rebuildSummary();
            break;
        }
        case RC_CLIENT_EVENT_GAME_COMPLETED:
            pushEvent("{\"type\":\"mastery\"}");
            rebuildSummary();
            break;
        case RC_CLIENT_EVENT_RESET:
            // Enabling hardcore with a game loaded: reset the emulated system, then the runtime.
            rc_host_reset_core();
            rc_client_reset(client);
            break;
        case RC_CLIENT_EVENT_SERVER_ERROR: {
            const rc_client_server_error_t* e = event->server_error;
            pushEvent(std::string("{\"type\":\"server_error\",\"message\":\"") +
                      jesc(e ? e->error_message : "") + "\"}");
            break;
        }
        case RC_CLIENT_EVENT_DISCONNECTED:
            pushEvent("{\"type\":\"disconnected\"}");
            break;
        case RC_CLIENT_EVENT_RECONNECTED:
            pushEvent("{\"type\":\"reconnected\"}");
            break;
        default:
            break;
    }
}

void RC_CCONV ra_login_cb(int result, const char* error_message, rc_client_t* client, void*) {
    g_loginHandle = nullptr;
    if (result == RC_OK) {
        const rc_client_user_t* u = rc_client_get_user_info(client);
        pushEvent(std::string("{\"type\":\"login_ok\",\"user\":\"") + jesc(u ? u->username : "") + "\"" +
                  ",\"display\":\"" + jesc(u ? u->display_name : "") + "\"" +
                  ",\"token\":\"" + jesc(u ? u->token : "") + "\"" +
                  ",\"score\":" + std::to_string(u ? u->score : 0) + "}");
    } else {
        pushEvent(std::string("{\"type\":\"login_failed\",\"code\":") + std::to_string(result) +
                  ",\"message\":\"" + jesc(error_message) + "\"}");
    }
}

void RC_CCONV ra_load_cb(int result, const char* error_message, rc_client_t* client, void*) {
    g_loadHandle = nullptr;
    if (result == RC_OK) {
        g_raGameLoaded.store(true);
        rebuildAchievementList();
        rebuildSummary();
        pushEvent("{\"type\":\"load_ok\"}");
    } else {
        pushEvent(std::string("{\"type\":\"load_failed\",\"code\":") + std::to_string(result) +
                  ",\"message\":\"" + jesc(error_message) + "\"}");
    }
}

// ---- command processing (run-loop thread) ---------------------------------------------------

void createClient(bool hardcore) {
    if (g_raClient) return;
    g_raClient = rc_client_create(ra_read_memory, ra_server_call);
    if (!g_raClient) { LOGE("rc_client_create failed"); return; }
    rc_client_set_event_handler(g_raClient, ra_event_handler);
    rc_client_set_get_time_millisecs_function(g_raClient, ra_get_time_ms);
    rc_client_enable_logging(g_raClient, RC_CLIENT_LOG_LEVEL_WARN, ra_log);
    rc_client_set_hardcore_enabled(g_raClient, hardcore ? 1 : 0);
    rc_hash_init_default_cdreader();  // PSP disc images (.iso/.cso/.chd) hash via the CD reader
    // Map the core's RAM into the flat RA address space (SET_MEMORY_MAPS preferred, else SYSTEM_RAM).
    rc_libretro_memory_init(&g_raRegions, rc_host_memory_map(), get_core_memory_info, RC_CONSOLE_PSP);
    g_raActive.store(true);
    rc_host_apply_hardcore(rc_client_get_hardcore_enabled(g_raClient) != 0);
    LOGI("rc_client session begun (hardcore=%d)", (int)hardcore);
}

void processCommand(const RaCommand& c) {
    switch (c.cmd) {
        case RaCmd::Begin:
            createClient(c.flag != 0);
            break;
        case RaCmd::LoginPw:
            if (g_raClient)
                g_loginHandle = rc_client_begin_login_with_password(g_raClient, c.a.c_str(), c.b.c_str(), ra_login_cb, nullptr);
            break;
        case RaCmd::LoginToken:
            if (g_raClient)
                g_loginHandle = rc_client_begin_login_with_token(g_raClient, c.a.c_str(), c.b.c_str(), ra_login_cb, nullptr);
            break;
        case RaCmd::Logout:
            if (g_raClient) rc_client_logout(g_raClient);
            break;
        case RaCmd::StartGamePath:
            if (g_raClient)
                // PSP requires the file path + data=NULL (buffer hashing is unsupported for console 41).
                g_loadHandle = rc_client_begin_identify_and_load_game(
                    g_raClient, c.console, c.a.c_str(), nullptr, 0, ra_load_cb, nullptr);
            break;
        case RaCmd::StartGameHash:
            if (g_raClient)
                g_loadHandle = rc_client_begin_load_game(g_raClient, c.a.c_str(), ra_load_cb, nullptr);
            break;
        case RaCmd::ChangeMedia:
            if (g_raClient)
                g_loadHandle = rc_client_begin_change_media(g_raClient, c.a.c_str(), nullptr, 0, ra_load_cb, nullptr);
            break;
        case RaCmd::SetHardcore:
            if (g_raClient) {
                rc_client_set_hardcore_enabled(g_raClient, c.flag ? 1 : 0);
                rc_host_apply_hardcore(rc_client_get_hardcore_enabled(g_raClient) != 0);
            }
            break;
        case RaCmd::DropToSoftcore:
            if (g_raClient) {
                rc_client_set_hardcore_enabled(g_raClient, 0);   // one-way; RA permits mid-session downgrade
                rc_host_apply_hardcore(false);
                pushEvent("{\"type\":\"hardcore_changed\",\"hardcore\":0}");
            }
            break;
        case RaCmd::Unload:
            if (g_raClient) rc_client_unload_game(g_raClient);
            g_raGameLoaded.store(false);
            break;
    }
}

void drainCommands() {
    for (;;) {
        RaCommand c;
        {
            std::lock_guard<std::mutex> lk(g_cmdMx);
            if (g_cmdQueue.empty()) break;
            c = std::move(g_cmdQueue.front());
            g_cmdQueue.pop_front();
        }
        processCommand(c);
    }
}

// Drain HTTP completions, invoking each owed rc_client callback ON THIS (run-loop) thread. The
// callback re-enters rc_client (and ra_server_call, which locks g_httpMx), so it MUST run with no
// bridge mutex held; pop the response + matching inflight request under the lock, then release.
void drainHttp() {
    for (;;) {
        RaHttpResponse resp;
        RaHttpRequest req;
        bool haveReq = false;
        {
            std::lock_guard<std::mutex> lk(g_httpMx);
            if (g_httpInbox.empty()) break;
            resp = std::move(g_httpInbox.front());
            g_httpInbox.pop_front();
            auto it = g_httpInflight.find(resp.id);
            if (it != g_httpInflight.end()) {
                req = std::move(it->second);
                g_httpInflight.erase(it);
                haveReq = true;
            }
        }
        if (!haveReq) continue;                                   // already failed at teardown; drop
        // Generation is the staleness guard (teardown bumps it before nulling g_raClient), so a
        // late completion for a torn-down session is dropped here without a use-after-free.
        if (req.generation != g_raGeneration.load()) continue;
        rc_api_server_response_t sr{};
        sr.body = resp.body.empty() ? nullptr : (const char*)resp.body.data();
        sr.body_length = resp.body.size();
        sr.http_status_code = resp.httpStatus;
        req.callback(&sr, req.callbackData);                      // re-enters rc_client on this thread
    }
}

void enqueueCommand(RaCommand c) {
    {
        std::lock_guard<std::mutex> lk(g_cmdMx);
        g_cmdQueue.push_back(std::move(c));
    }
}

// ---- save-state RA-progress container --------------------------------------------------------
// Layout when an RA game is loaded: [core_state N][rc_progress M][FOOTER 28].
//   footer (LE): u32 rc_len | u64 core_len | u32 version(1) | u32 flags | char magic[8]="PULSARRA"
constexpr size_t kFooterLen = 4 + 8 + 4 + 4 + 8;   // = 28
const char kFooterMagic[8] = { 'P','U','L','S','A','R','R','A' };

bool parseFooter(const uint8_t* file, size_t fileLen, uint64_t& coreLen, uint32_t& rcLen) {
    if (fileLen < kFooterLen) return false;
    const uint8_t* f = file + fileLen - kFooterLen;
    if (memcmp(f + 20, kFooterMagic, 8) != 0) return false;
    uint32_t rc = 0, ver = 0, flags = 0;
    uint64_t core = 0;
    memcpy(&rc, f + 0, 4);
    memcpy(&core, f + 4, 8);
    memcpy(&ver, f + 12, 4);
    memcpy(&flags, f + 16, 4);
    (void)flags;
    if (ver != 1) return false;
    if ((uint64_t)core + rc + kFooterLen != fileLen) return false;  // unambiguous size invariant
    coreLen = core;
    rcLen = rc;
    return true;
}

}  // namespace

// ================================ public bridge API (run-loop thread) ========================

void rc_bridge_service() {
    drainCommands();
    drainHttp();
}

void rc_bridge_on_emulated_frame() {
    if (g_raClient && g_raGameLoaded.load()) rc_client_do_frame(g_raClient);
}

void rc_bridge_idle() {
    if (g_raClient) rc_client_idle(g_raClient);
}

bool rc_bridge_is_active() { return g_raActive.load(); }
bool rc_bridge_ra_game_loaded() { return g_raGameLoaded.load(); }

void rc_bridge_end_session() {
    if (!g_raClient && g_httpInflight.empty() && g_httpOutbox.empty()) { g_raActive.store(false); return; }

    // 1. Invalidate the session so late HTTP completions are dropped, and abort in-flight async ops
    //    FIRST (before failing owed callbacks) so we never abort a handle whose callback already ran.
    g_raGeneration.fetch_add(1);
    if (g_raClient) {
        if (g_loginHandle) { rc_client_abort_async(g_raClient, g_loginHandle); g_loginHandle = nullptr; }
        if (g_loadHandle) { rc_client_abort_async(g_raClient, g_loadHandle); g_loadHandle = nullptr; }
    }

    // 2. Fail every owed HTTP callback so rc_client finalizes/frees each async op. Invoke with NO
    //    bridge mutex held (the callback re-enters ra_server_call -> locks g_httpMx). Loop until
    //    both queues are empty, since a failing callback may enqueue a new request.
    for (;;) {
        RaHttpRequest req;
        bool have = false;
        {
            std::lock_guard<std::mutex> lk(g_httpMx);
            if (!g_httpInflight.empty()) {
                auto it = g_httpInflight.begin();
                req = std::move(it->second);
                g_httpInflight.erase(it);
                have = true;
            } else if (!g_httpOutbox.empty()) {
                req = std::move(g_httpOutbox.front());
                g_httpOutbox.pop_front();
                have = true;
            }
        }
        if (!have) break;
        if (g_raClient && req.callback) {
            rc_api_server_response_t sr{};
            sr.body = nullptr;
            sr.body_length = 0;
            sr.http_status_code = RC_API_SERVER_RESPONSE_CLIENT_ERROR;
            req.callback(&sr, req.callbackData);
        }
    }
    {
        std::lock_guard<std::mutex> lk(g_httpMx);
        g_httpInbox.clear();
    }
    { std::lock_guard<std::mutex> lk(g_cmdMx); g_cmdQueue.clear(); }

    // 3. Now safe to unload + destroy; free the memory regions LAST (read_memory won't fire again).
    if (g_raClient) {
        rc_client_unload_game(g_raClient);
        rc_client_destroy(g_raClient);
        g_raClient = nullptr;
    }
    rc_libretro_memory_destroy(&g_raRegions);
    g_raRegions = rc_libretro_memory_regions_t{};
    g_raGameLoaded.store(false);
    g_raActive.store(false);
    { std::lock_guard<std::mutex> lk(g_eventMx); g_eventQueue.clear(); }
    { std::lock_guard<std::mutex> lk(g_snapMx); g_listJson = "{}"; g_summaryJson = "{}"; }
    LOGI("rc_client session ended");
}

void rc_bridge_pack_state(std::vector<uint8_t>& blob) {
    if (!g_raClient || !g_raGameLoaded.load()) return;   // legacy format when RA inactive
    size_t coreLen = blob.size();
    size_t m = rc_client_progress_size(g_raClient);
    std::vector<uint8_t> prog(m);
    if (m > 0 && rc_client_serialize_progress_sized(g_raClient, prog.data(), m) != RC_OK) {
        LOGW("rc_client_serialize_progress failed; writing legacy state");
        return;
    }
    blob.insert(blob.end(), prog.begin(), prog.end());
    uint8_t footer[kFooterLen];
    uint32_t rcLen = (uint32_t)m, ver = 1, flags = rc_client_get_hardcore_enabled(g_raClient) ? 1 : 0;
    uint64_t core64 = (uint64_t)coreLen;
    memcpy(footer + 0, &rcLen, 4);
    memcpy(footer + 4, &core64, 8);
    memcpy(footer + 12, &ver, 4);
    memcpy(footer + 16, &flags, 4);
    memcpy(footer + 20, kFooterMagic, 8);
    blob.insert(blob.end(), footer, footer + kFooterLen);
}

size_t rc_bridge_state_core_len(const uint8_t* file, size_t fileLen) {
    uint64_t coreLen = 0;
    uint32_t rcLen = 0;
    if (parseFooter(file, fileLen, coreLen, rcLen)) return (size_t)coreLen;
    return fileLen;   // legacy: whole file is the core state
}

void rc_bridge_state_restore_progress(const uint8_t* file, size_t fileLen) {
    if (!g_raClient) return;
    uint64_t coreLen = 0;
    uint32_t rcLen = 0;
    if (parseFooter(file, fileLen, coreLen, rcLen)) {
        rc_client_deserialize_progress_sized(g_raClient, file + coreLen, rcLen);
    } else {
        // Legacy / no embedded progress: reset the runtime trackers cleanly.
        rc_client_deserialize_progress_sized(g_raClient, nullptr, 0);
    }
}

// ================================ JNI (com.retrovault.emulator.RaBridge) ======================

extern "C" {

static std::string jstr(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out(c ? c : "");
    env->ReleaseStringUTFChars(s, c);
    return out;
}

JNIEXPORT jstring JNICALL
Java_com_retrovault_emulator_RaBridge_nativeRaVersionString(JNIEnv* env, jobject) {
    // rc_version_string() is "11.6" (patch 0 -> short form); never hardcode a 3-part string.
    std::string v = std::string("rcheevos/") + rc_version_string();
    return env->NewStringUTF(v.c_str());
}

JNIEXPORT void JNICALL
Java_com_retrovault_emulator_RaBridge_nativeRaBeginSession(JNIEnv*, jobject, jboolean hardcore) {
    RaCommand c; c.cmd = RaCmd::Begin; c.flag = hardcore == JNI_TRUE ? 1 : 0;
    enqueueCommand(std::move(c));
}

JNIEXPORT void JNICALL
Java_com_retrovault_emulator_RaBridge_nativeRaEndSession(JNIEnv*, jobject) {
    // The run loop drains this and calls rc_bridge_end_session() during teardown; also enqueue an
    // explicit Unload so a still-running session drops the game promptly.
    RaCommand c; c.cmd = RaCmd::Unload;
    enqueueCommand(std::move(c));
}

JNIEXPORT void JNICALL
Java_com_retrovault_emulator_RaBridge_nativeRaLoginWithPassword(JNIEnv* env, jobject, jstring user, jstring pass) {
    RaCommand c; c.cmd = RaCmd::LoginPw; c.a = jstr(env, user); c.b = jstr(env, pass);
    enqueueCommand(std::move(c));
}

JNIEXPORT void JNICALL
Java_com_retrovault_emulator_RaBridge_nativeRaLoginWithToken(JNIEnv* env, jobject, jstring user, jstring token) {
    RaCommand c; c.cmd = RaCmd::LoginToken; c.a = jstr(env, user); c.b = jstr(env, token);
    enqueueCommand(std::move(c));
}

JNIEXPORT void JNICALL
Java_com_retrovault_emulator_RaBridge_nativeRaLogout(JNIEnv*, jobject) {
    RaCommand c; c.cmd = RaCmd::Logout; enqueueCommand(std::move(c));
}

JNIEXPORT void JNICALL
Java_com_retrovault_emulator_RaBridge_nativeRaStartGameByPath(JNIEnv* env, jobject, jint consoleId, jstring filePath) {
    RaCommand c; c.cmd = RaCmd::StartGamePath; c.console = (uint32_t)consoleId; c.a = jstr(env, filePath);
    enqueueCommand(std::move(c));
}

JNIEXPORT void JNICALL
Java_com_retrovault_emulator_RaBridge_nativeRaStartGameByHash(JNIEnv* env, jobject, jstring hash) {
    RaCommand c; c.cmd = RaCmd::StartGameHash; c.a = jstr(env, hash);
    enqueueCommand(std::move(c));
}

JNIEXPORT void JNICALL
Java_com_retrovault_emulator_RaBridge_nativeRaChangeMedia(JNIEnv* env, jobject, jstring filePath) {
    RaCommand c; c.cmd = RaCmd::ChangeMedia; c.a = jstr(env, filePath);
    enqueueCommand(std::move(c));
}

JNIEXPORT void JNICALL
Java_com_retrovault_emulator_RaBridge_nativeRaSetHardcore(JNIEnv*, jobject, jboolean on) {
    RaCommand c; c.cmd = RaCmd::SetHardcore; c.flag = on == JNI_TRUE ? 1 : 0;
    enqueueCommand(std::move(c));
}

JNIEXPORT jboolean JNICALL
Java_com_retrovault_emulator_RaBridge_nativeRaGetHardcore(JNIEnv*, jobject) {
    return (g_raClient && rc_client_get_hardcore_enabled(g_raClient)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_retrovault_emulator_RaBridge_nativeRaDropToSoftcore(JNIEnv*, jobject) {
    RaCommand c; c.cmd = RaCmd::DropToSoftcore; enqueueCommand(std::move(c));
}

// ---- HTTP dispatcher (called by the Kotlin RaHttpPump thread) --------------------------------

JNIEXPORT jobjectArray JNICALL
Java_com_retrovault_emulator_RaBridge_nativeRaWaitHttpRequest(JNIEnv* env, jobject, jint timeoutMs) {
    RaHttpRequest r;
    {
        std::unique_lock<std::mutex> lk(g_httpMx);
        if (!g_httpCv.wait_for(lk, std::chrono::milliseconds(timeoutMs > 0 ? timeoutMs : 0),
                               [] { return !g_httpOutbox.empty(); })) {
            return nullptr;  // timeout
        }
        r = std::move(g_httpOutbox.front());
        g_httpOutbox.pop_front();
        uint64_t id = r.id;
        g_httpInflight[id] = std::move(r);
        r = g_httpInflight[id];  // copy back the fields we return (callback ptr stays in inflight)
    }
    jclass strCls = env->FindClass("java/lang/String");
    jobjectArray arr = env->NewObjectArray(5, strCls, nullptr);
    std::string idStr = std::to_string(r.id);
    std::string method = r.postData.empty() ? "GET" : "POST";
    env->SetObjectArrayElement(arr, 0, env->NewStringUTF(idStr.c_str()));
    env->SetObjectArrayElement(arr, 1, env->NewStringUTF(r.url.c_str()));
    env->SetObjectArrayElement(arr, 2, env->NewStringUTF(r.postData.c_str()));
    env->SetObjectArrayElement(arr, 3, env->NewStringUTF(r.contentType.c_str()));
    env->SetObjectArrayElement(arr, 4, env->NewStringUTF(method.c_str()));
    return arr;
}

JNIEXPORT void JNICALL
Java_com_retrovault_emulator_RaBridge_nativeRaCompleteHttpRequest(JNIEnv* env, jobject, jlong id, jint httpStatus, jbyteArray body) {
    RaHttpResponse resp;
    resp.id = (uint64_t)id;
    resp.httpStatus = (int)httpStatus;
    if (body) {
        jsize n = env->GetArrayLength(body);
        resp.body.resize((size_t)n);
        if (n > 0) env->GetByteArrayRegion(body, 0, n, (jbyte*)resp.body.data());
    }
    {
        std::lock_guard<std::mutex> lk(g_httpMx);
        g_httpInbox.push_back(std::move(resp));
    }
}

// ---- event channel + UI snapshots (UI thread) ------------------------------------------------

JNIEXPORT jstring JNICALL
Java_com_retrovault_emulator_RaBridge_nativeRaPollEvent(JNIEnv* env, jobject) {
    std::string ev;
    {
        std::lock_guard<std::mutex> lk(g_eventMx);
        if (g_eventQueue.empty()) return nullptr;
        ev = std::move(g_eventQueue.front());
        g_eventQueue.pop_front();
    }
    return env->NewStringUTF(ev.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_retrovault_emulator_RaBridge_nativeRaAchievementListJson(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lk(g_snapMx);
    return env->NewStringUTF(g_listJson.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_retrovault_emulator_RaBridge_nativeRaGameSummaryJson(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lk(g_snapMx);
    return env->NewStringUTF(g_summaryJson.c_str());
}

// ---- memory (standalone: reads the core's stable RAM pointer; no rc_client needed) -----------

JNIEXPORT jlong JNICALL
Java_com_retrovault_emulator_RaBridge_nativeRaMemInit(JNIEnv*, jobject) {
    rc_libretro_memory_destroy(&g_raRegions);
    g_raRegions = rc_libretro_memory_regions_t{};
    int valid = rc_libretro_memory_init(&g_raRegions, rc_host_memory_map(), get_core_memory_info, RC_CONSOLE_PSP);
    return ((jlong)(valid ? 1 : 0) << 32) | (jlong)(g_raRegions.total_size & 0xFFFFFFFFULL);
}

JNIEXPORT jint JNICALL
Java_com_retrovault_emulator_RaBridge_nativeRaMemPeek(JNIEnv*, jobject, jint flatAddr) {
    uint8_t b = 0;
    uint32_t n = rc_libretro_memory_read(&g_raRegions, (uint32_t)flatAddr, &b, 1);
    return n ? (jint)b : (jint)-1;
}

JNIEXPORT jint JNICALL
Java_com_retrovault_emulator_RaBridge_nativeRaInflightCount(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lk(g_httpMx);
    return (jint)(g_httpInflight.size() + g_httpOutbox.size());
}

// ---- offline self-tests (androidTest) --------------------------------------------------------

JNIEXPORT jboolean JNICALL
Java_com_retrovault_emulator_RaBridge_nativeRaSelfTestCreate(JNIEnv*, jobject) {
    rc_client_t* c = rc_client_create(ra_read_memory, ra_server_call);
    if (!c) return JNI_FALSE;
    rc_client_set_event_handler(c, ra_event_handler);
    rc_client_set_get_time_millisecs_function(c, ra_get_time_ms);
    rc_client_set_hardcore_enabled(c, 1);
    bool hc = rc_client_get_hardcore_enabled(c) != 0;
    rc_client_destroy(c);
    return hc ? JNI_TRUE : JNI_FALSE;
}

// Loopback: prove an HTTP completion coming from ANOTHER thread invokes the stored callback on the
// DRAINING (this) thread, exactly once — the marshalling contract that keeps rc_client single-owner.
static std::atomic<std::thread::id> g_selfTestCbThread;
static std::atomic<int> g_selfTestCbCount{0};
static void RC_CCONV selfTestCb(const rc_api_server_response_t*, void*) {
    g_selfTestCbThread.store(std::this_thread::get_id());
    g_selfTestCbCount.fetch_add(1);
}

JNIEXPORT jboolean JNICALL
Java_com_retrovault_emulator_RaBridge_nativeRaSelfTestHttp(JNIEnv*, jobject) {
    g_selfTestCbCount.store(0);
    // Enqueue a request straight into the outbox with our recording callback.
    RaHttpRequest r;
    r.id = g_httpNextId.fetch_add(1);
    r.url = "http://loopback";
    r.callback = selfTestCb;
    r.callbackData = nullptr;
    r.generation = g_raGeneration.load();
    uint64_t id = r.id;
    { std::lock_guard<std::mutex> lk(g_httpMx); g_httpInflight[id] = std::move(r); }
    // Complete it from a DIFFERENT thread (simulating the OkHttp pump).
    std::thread([id] {
        RaHttpResponse resp; resp.id = id; resp.httpStatus = 200;
        std::lock_guard<std::mutex> lk(g_httpMx); g_httpInbox.push_back(std::move(resp));
    }).join();
    // Drain on THIS thread; the callback must run here, once.
    std::thread::id thisThread = std::this_thread::get_id();
    drainHttp();
    bool ranOnce = g_selfTestCbCount.load() == 1;
    bool ranHere = g_selfTestCbThread.load() == thisThread;
    return (ranOnce && ranHere) ? JNI_TRUE : JNI_FALSE;
}

// Framing round-trip: pack synthetic [core][rc] with a footer, split it back, and confirm a
// footer-less legacy blob reports its whole length as core. No rc_client / server needed.
JNIEXPORT jboolean JNICALL
Java_com_retrovault_emulator_RaBridge_nativeRaTestContainer(JNIEnv*, jobject) {
    const size_t N = 777, M = 123;
    std::vector<uint8_t> blob(N);
    for (size_t i = 0; i < N; ++i) blob[i] = (uint8_t)(i * 31 + 7);
    std::vector<uint8_t> rc(M);
    for (size_t i = 0; i < M; ++i) rc[i] = (uint8_t)(i * 53 + 11);
    // Build a footered container by hand (mirrors rc_bridge_pack_state's framing).
    std::vector<uint8_t> file = blob;
    file.insert(file.end(), rc.begin(), rc.end());
    uint8_t footer[kFooterLen];
    uint32_t rcLen = (uint32_t)M, ver = 1, flags = 0;
    uint64_t core64 = (uint64_t)N;
    memcpy(footer + 0, &rcLen, 4); memcpy(footer + 4, &core64, 8);
    memcpy(footer + 12, &ver, 4); memcpy(footer + 16, &flags, 4);
    memcpy(footer + 20, kFooterMagic, 8);
    file.insert(file.end(), footer, footer + kFooterLen);

    if (rc_bridge_state_core_len(file.data(), file.size()) != N) return JNI_FALSE;
    uint64_t pc = 0; uint32_t pr = 0;
    if (!parseFooter(file.data(), file.size(), pc, pr) || pc != N || pr != M) return JNI_FALSE;
    if (memcmp(file.data(), blob.data(), N) != 0) return JNI_FALSE;
    if (memcmp(file.data() + N, rc.data(), M) != 0) return JNI_FALSE;
    // Legacy: a footer-less blob reports its full length as core, none as rc.
    if (rc_bridge_state_core_len(blob.data(), blob.size()) != blob.size()) return JNI_FALSE;
    // A near-miss (magic but wrong size invariant) must be treated as legacy.
    std::vector<uint8_t> bad = blob;
    bad.insert(bad.end(), footer, footer + kFooterLen);  // footer whose core_len(N) != actual
    if (rc_bridge_state_core_len(bad.data(), bad.size()) != bad.size()) return JNI_FALSE;
    return JNI_TRUE;
}

}  // extern "C"
