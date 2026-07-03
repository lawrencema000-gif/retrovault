// Pulsar GL video backbone: owns the EGL display/context/surface and presents libretro
// frames — software framebuffers (uploaded as a texture) or hardware frames the core has
// rendered into our FBO via RETRO_ENVIRONMENT_SET_HW_RENDER.
//
// Threading contract: everything here runs on the single render/emu thread, except
// setPendingWindow() which may be called from any thread (mutex-guarded handoff).

#pragma once

#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/native_window.h>
#include <atomic>
#include <cstdint>
#include <mutex>
#include "libretro.h"

struct VideoStats {
    std::atomic<uint64_t> framesPresented{0};
    std::atomic<uint64_t> framesDuped{0};
    // exponential moving average of frame-to-frame present interval, in ms (x1000 fixed point)
    std::atomic<uint64_t> avgFrameIntervalUs{0};
    std::atomic<bool> swappyActive{false};
    std::atomic<int> surfaceWidth{0};
    std::atomic<int> surfaceHeight{0};
};

class VideoGL {
public:
    // ---- lifecycle (render thread) ----
    bool initDisplay();          // EGL display + ES3 context + 1x1 pbuffer fallback surface
    void shutdown();             // destroys FBO, surfaces, context

    // ---- window handoff (any thread -> render thread) ----
    void setPendingWindow(ANativeWindow* window);  // takes ownership; null = surface lost
    void applyPendingWindow();                     // render thread: swap EGL surface if changed
    bool hasWindowSurface() const { return windowSurface_ != EGL_NO_SURFACE; }

    // ---- hw render (SET_HW_RENDER) ----
    bool enableHwRender(retro_hw_render_callback* cb, unsigned maxWidth, unsigned maxHeight);
    bool hwRenderEnabled() const { return hwEnabled_; }
    uintptr_t currentFramebuffer() const { return (uintptr_t)fbo_; }
    void callContextResetOnce();                   // fires hw context_reset exactly once

    // ---- frame submission (render thread, inside retro_run's video_cb) ----
    void setPixelFormat(retro_pixel_format fmt) { pixelFormat_ = fmt; }
    void setGeometry(unsigned baseW, unsigned baseH, float aspect);
    void submitSoftwareFrame(const void* data, unsigned width, unsigned height, size_t pitch);
    void markHwFrame(unsigned width, unsigned height);
    void markDupeFrame();

    // ---- present (render thread, once per retro_run) ----
    void present();

    VideoStats stats;

private:
    bool ensureQuadPipeline();
    void uploadSoftwareFrame(const void* data, unsigned width, unsigned height, size_t pitch);
    void drawFrame();

    // EGL
    EGLDisplay display_ = EGL_NO_DISPLAY;
    EGLConfig config_ = nullptr;
    EGLContext context_ = EGL_NO_CONTEXT;
    EGLSurface windowSurface_ = EGL_NO_SURFACE;
    EGLSurface pbufferSurface_ = EGL_NO_SURFACE;
    ANativeWindow* window_ = nullptr;

    std::mutex pendingMutex_;
    ANativeWindow* pendingWindow_ = nullptr;
    bool pendingWindowChanged_ = false;

    // frame source state
    retro_pixel_format pixelFormat_ = RETRO_PIXEL_FORMAT_0RGB1555;
    unsigned frameW_ = 0, frameH_ = 0;   // dimensions of the latest frame
    unsigned baseW_ = 320, baseH_ = 240;
    float aspect_ = 4.0f / 3.0f;
    bool frameIsHw_ = false;
    bool haveFrame_ = false;

    // software path
    GLuint swTexture_ = 0;
    unsigned swTexW_ = 0, swTexH_ = 0;
    bool swTexIs565_ = false;
    uint16_t* convertScratch_ = nullptr;
    size_t convertScratchSize_ = 0;

    // hw path (core renders into this FBO)
    bool hwEnabled_ = false;
    retro_hw_render_callback hwCb_{};
    bool contextResetDone_ = false;
    GLuint fbo_ = 0;
    GLuint fboTexture_ = 0;
    GLuint fboDepthStencil_ = 0;
    unsigned fboW_ = 0, fboH_ = 0;

    // present pipeline
    GLuint program_ = 0;
    GLuint vbo_ = 0;
    GLint locPos_ = -1, locTex_ = -1, locSampler_ = -1, locFlipY_ = -1, locSwapRB_ = -1;

    // pacing
    int64_t lastPresentUs_ = 0;
};
