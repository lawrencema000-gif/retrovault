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
#include <vector>
#include "libretro.h"

// P19 display polish. Post-shader ids (0 = none / passthrough).
enum PostShaderId {
    kPostNone = 0,
    kPostScanlineCrt = 1,
    kPostFsrSharpen = 2,
    kPostSharpBilinear = 3,
    kPostShaderCount = 4, // array size (ids 0..3)
};

enum class ScaleMode { Fit = 0, Stretch = 1, Integer = 2 };

// User-facing display configuration, applied on the render thread at present time.
struct DisplayConfig {
    int rotationDeg = 0;                 // 0 / 90 / 180 / 270 (internal image rotation)
    ScaleMode scaleMode = ScaleMode::Fit;
    int pass1 = kPostNone;               // first post pass (or none)
    int pass2 = kPostNone;               // second post pass, stacked after pass1 (or none)
    float scanlineIntensity = 0.35f;     // scanline_crt uScanlineIntensity
    float maskIntensity = 0.10f;         // scanline_crt uMaskIntensity
    float sharpenAmount = 0.50f;         // fsr_rcas_sharpen uSharpenAmount
};

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

    /**
     * Fire the core's context_destroy WHILE the core is still loaded and the GL context is
     * current, then forget the hw callbacks. MUST be called before dlclose(core) — the
     * callback is a pointer into the core library.
     */
    void notifyContextDestroy();

    // ---- frame submission (render thread, inside retro_run's video_cb) ----
    void setPixelFormat(retro_pixel_format fmt) { pixelFormat_ = fmt; }
    void setGeometry(unsigned baseW, unsigned baseH, float aspect);
    void submitSoftwareFrame(const void* data, unsigned width, unsigned height, size_t pitch);
    void markHwFrame(unsigned width, unsigned height);
    void markDupeFrame();

    // ---- present (render thread, once per retro_run) ----
    void present();

    // ---- display polish (P19) ----
    // Thread-safe: may be called from any thread; latched and applied on the render thread.
    void setDisplayConfig(const DisplayConfig& cfg);

    /**
     * Compile + link every built-in present program (base + all post shaders) against the live
     * GL context and report which succeeded as a bitmask: bit (1<<id) is set when that program
     * built. Bit 0 is the base present program. All-good == (1<<kPostShaderCount)-1. Render
     * thread only; needs a current context but no frame. Used by the ShaderTest instrumentation.
     */
    int shaderSelfTest();

    /**
     * Read back the latest frame as tightly-packed RGBA8888, top-down rows (render thread
     * only). Works for both hw frames (read from the core FBO, flipped) and sw frames
     * (read via a transient FBO attach). Returns false if no frame has been submitted yet.
     */
    bool captureFrame(std::vector<uint8_t>& out, unsigned& w, unsigned& h);

    VideoStats stats;

private:
    bool ensureQuadPipeline();
    void uploadSoftwareFrame(const void* data, unsigned width, unsigned height, size_t pitch);
    void drawFrame();

    // ---- P19 present helpers ----
    // Bind the game frame's source texture + compute its sub-rect / orientation / swizzle.
    void bindGameSource(float& u, float& vSpan, float& swapRB, bool& bottomLeftOrigin, GLuint& tex);
    // Draw the game frame (base program) into the currently-bound framebuffer at [vp], applying
    // rotation via uPosRot. When forResolve, samples full identity UVs into a native scene target.
    void drawGameQuad(int vpX, int vpY, int vpW, int vpH, int rotationDeg, bool forResolve);
    // Draw a post pass: sample [srcTex] (native, clean) through post_[id] into the bound FBO at
    // [vp], with rotation + output size for screen-space effects.
    void drawPostQuad(int id, GLuint srcTex, int vpX, int vpY, int vpW, int vpH,
                      int rotationDeg, const DisplayConfig& cfg);
    bool ensurePostPipeline();               // lazily build post programs + the shared post quad
    bool ensureSceneTarget(GLuint& fbo, GLuint& tex, int& w, int& h, int wantW, int wantH);
    // Letterbox/integer/stretch viewport for an oriented source into the drawable.
    void computeViewport(float srcAspect, int intSrcW, int intSrcH, ScaleMode mode,
                         int rotationDeg, int surfW, int surfH,
                         int& vpX, int& vpY, int& vpW, int& vpH);
    void resetPresentGlState();              // scissor/depth/cull/blend off, black clear color

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
    GLint locPosRot_ = -1;               // mat2 image rotation (identity by default)

    // ---- P19 display polish ----
    DisplayConfig displayCfg_;
    std::mutex displayCfgMutex_;

    // Post-shader programs, indexed by PostShaderId (index 0 unused / passthrough).
    struct PostProgram {
        GLuint program = 0;
        GLint locPos = -1, locTex = -1, locPosRot = -1;
        GLint locSampler = -1, locTexel = -1, locOutputSize = -1;
        GLint locScanlineIntensity = -1, locMaskIntensity = -1, locSharpenAmount = -1;
    };
    PostProgram post_[kPostShaderCount]{};
    bool postBuilt_ = false;
    GLuint postVbo_ = 0;                  // static identity quad (pos + upright UV) for post passes

    // Native-res clean scene target (game frame resolved here before post passes) + ping-pong.
    GLuint sceneFbo_ = 0, sceneTex_ = 0;
    int sceneW_ = 0, sceneH_ = 0;
    GLuint pingFbo_ = 0, pingTex_ = 0;
    int pingW_ = 0, pingH_ = 0;

    // pacing
    int64_t lastPresentUs_ = 0;
};
