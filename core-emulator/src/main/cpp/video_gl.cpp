#include "video_gl.h"

#include <android/log.h>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <vector>

#define LOG_TAG "pulsar_video"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

int64_t nowUs() {
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000 + ts.tv_nsec / 1000;
}

const char* kVertexShader = R"(#version 100
attribute vec2 aPos;
attribute vec2 aTex;
varying vec2 vTex;
uniform float uFlipY;
uniform mat2 uPosRot;
void main() {
    vTex = vec2(aTex.x, mix(aTex.y, 1.0 - aTex.y, uFlipY));
    gl_Position = vec4(uPosRot * aPos, 0.0, 1.0);
}
)";

const char* kFragmentShader = R"(#version 100
precision mediump float;
varying vec2 vTex;
uniform sampler2D uSampler;
uniform float uSwapRB;
void main() {
    vec4 c = texture2D(uSampler, vTex);
    gl_FragColor = mix(vec4(c.rgb, 1.0), vec4(c.bgr, 1.0), uSwapRB);
}
)";

// Post passes sample an already-oriented, color-corrected native-res scene texture (top-left
// origin) with identity UVs; rotation is applied to the on-screen geometry via uPosRot so that
// screen-space effects (scanlines) stay aligned to the physical display, not the game axis.
const char* kPostVertexShader = R"(#version 100
attribute vec2 aPos;
attribute vec2 aTex;
varying vec2 vTex;
uniform mat2 uPosRot;
void main() {
    vTex = aTex;
    gl_Position = vec4(uPosRot * aPos, 0.0, 1.0);
}
)";

// --- P19 post-shader fragment sources (authored or ported — lineage per NOTICE.md, GLSL ES 1.00) --

// CRT scanlines + subtle aperture mask. highp-guarded, fract-wrapped phase (mediump-safe).
const char* kFragScanlineCrt = R"(#version 100
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec2 vTex;
uniform sampler2D uSampler;
uniform vec2 uTexel;
uniform vec2 uOutputSize;
uniform float uScanlineIntensity;
uniform float uMaskIntensity;
void main() {
    vec4 src = texture2D(uSampler, vTex);
    vec3 col = src.rgb;
    float screenY = vTex.y * uOutputSize.y;
    float wave = sin(fract(screenY) * 3.14159265);
    float lineMask = wave * wave;
    float si = clamp(uScanlineIntensity, 0.0, 1.0);
    float scan = 1.0 - si * (1.0 - lineMask);
    col *= scan;
    float mi = clamp(uMaskIntensity, 0.0, 1.0);
    float screenX = vTex.x * uOutputSize.x;
    float phase = floor(fract(screenX / 3.0) * 3.0);
    vec3 maskTint = vec3(0.85);
    if (phase < 0.5) { maskTint.r = 1.0; }
    else if (phase < 1.5) { maskTint.g = 1.0; }
    else { maskTint.b = 1.0; }
    vec3 mask = mix(vec3(1.0), maskTint, mi);
    col *= mask;
    float loss = si * 0.5 + mi * 0.15;
    col *= 1.0 + loss * 0.6;
    col = clamp(col, 0.0, 1.0);
    gl_FragColor = vec4(col, src.a);
}
)";

// FSR 1.0 RCAS contrast-adaptive sharpen (5-tap). Overshoot clamp uses the FULL 5-tap envelope
// (center included) so isolated highlights survive.
// Port of AMD FidelityFX FSR 1.0 RCAS (ffx_fsr1.h). MIT License,
// Copyright (c) 2021 Advanced Micro Devices, Inc. See NOTICE.md.
const char* kFragFsrSharpen = R"(#version 100
precision mediump float;
varying vec2 vTex;
uniform sampler2D uSampler;
uniform vec2 uTexel;
uniform float uSharpenAmount;
const float RCAS_LIMIT = 0.1875;
const float EPS_POS = 1.0 / 32768.0;
const float EPS_NEG = -1.0 / 32768.0;
float rcasLuma(vec3 c) { return c.g + 0.5 * (c.r + c.b); }
void main() {
    vec4 eRGBA = texture2D(uSampler, vTex);
    vec3 e = eRGBA.rgb;
    vec3 b = texture2D(uSampler, vTex + vec2( 0.0,      -uTexel.y)).rgb;
    vec3 d = texture2D(uSampler, vTex + vec2(-uTexel.x,   0.0    )).rgb;
    vec3 f = texture2D(uSampler, vTex + vec2( uTexel.x,   0.0    )).rgb;
    vec3 h = texture2D(uSampler, vTex + vec2( 0.0,       uTexel.y)).rgb;
    vec3 mn4 = min(min(b, d), min(f, h));
    vec3 mx4 = max(max(b, d), max(f, h));
    vec3 hitMin = mn4 / max(4.0 * mx4, vec3(EPS_POS));
    vec3 hitMax = (vec3(1.0) - mx4) / min(4.0 * mn4 - 4.0, vec3(EPS_NEG));
    vec3 lobeRGB = max(-hitMin, hitMax);
    float lobe = max(-RCAS_LIMIT, min(max(lobeRGB.r, max(lobeRGB.g, lobeRGB.b)), 0.0));
    float bL = rcasLuma(b);
    float dL = rcasLuma(d);
    float eL = rcasLuma(e);
    float fL = rcasLuma(f);
    float hL = rcasLuma(h);
    float rng = max(max(max(bL, dL), max(fL, hL)), eL)
              - min(min(min(bL, dL), min(fL, hL)), eL);
    float nz = 0.25 * (bL + dL + fL + hL) - eL;
    nz = clamp(abs(nz) / max(rng, EPS_POS), 0.0, 1.0);
    nz = 1.0 - 0.5 * nz;
    lobe *= nz;
    lobe *= clamp(uSharpenAmount, 0.0, 1.0);
    float rcpL = 1.0 / (4.0 * lobe + 1.0);
    vec3 c = (e + lobe * (b + d + f + h)) * rcpL;
    vec3 mnAll = min(mn4, e);
    vec3 mxAll = max(mx4, e);
    c = clamp(c, mnAll, mxAll);
    gl_FragColor = vec4(c, eRGBA.a);
}
)";

// Themaister sharp-bilinear: crisp texel edges on integer-ish upscale. Requires GL_LINEAR.
const char* kFragSharpBilinear = R"(#version 100
precision mediump float;
varying vec2 vTex;
uniform sampler2D uSampler;
uniform vec2 uTexel;
uniform vec2 uOutputSize;
void main() {
    vec2 texel = max(uTexel, vec2(1e-6));
    vec2 sourceSize = 1.0 / texel;
    vec2 scale = max(uOutputSize * texel, vec2(1.0));
    vec2 texelCoord = vTex * sourceSize;
    vec2 texelFloor = floor(texelCoord);
    vec2 frac = texelCoord - texelFloor;
    vec2 regionRange = 0.5 - 0.5 / scale;
    vec2 centerDist = frac - 0.5;
    vec2 ramp = (centerDist - clamp(centerDist, -regionRange, regionRange)) * scale + 0.5;
    vec2 modTexel = texelFloor + ramp;
    vec2 uv = modTexel * texel;
    gl_FragColor = texture2D(uSampler, uv);
}
)";

const char* kPostFragForId(int id) {
    switch (id) {
        case kPostScanlineCrt:  return kFragScanlineCrt;
        case kPostFsrSharpen:   return kFragFsrSharpen;
        case kPostSharpBilinear:return kFragSharpBilinear;
        default:                return nullptr;
    }
}

// Column-major mat2 for an image rotation by a multiple of 90°, snapped to exact {0,±1}.
void rotationMat2(int deg, float m[4]) {
    int d = ((deg % 360) + 360) % 360;
    float c = 1.0f, s = 0.0f;
    if (d == 90)  { c = 0.0f;  s = 1.0f; }
    else if (d == 180) { c = -1.0f; s = 0.0f; }
    else if (d == 270) { c = 0.0f;  s = -1.0f; }
    // uPosRot = [[c,-s],[s,c]] applied as uPosRot*aPos; GLSL mat2 is column-major.
    m[0] = c;  m[1] = s;   // column 0
    m[2] = -s; m[3] = c;   // column 1
}

GLuint compileShader(GLenum type, const char* src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    GLint ok = 0;
    glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[512];
        glGetShaderInfoLog(s, sizeof(log), nullptr, log);
        LOGE("shader compile failed: %s", log);
        glDeleteShader(s);
        return 0;
    }
    return s;
}

} // namespace

bool VideoGL::initDisplay() {
    display_ = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display_ == EGL_NO_DISPLAY || !eglInitialize(display_, nullptr, nullptr)) {
        LOGE("eglInitialize failed");
        return false;
    }

    const EGLint configAttrs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT | EGL_PBUFFER_BIT,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8, EGL_DEPTH_SIZE, 0, EGL_STENCIL_SIZE, 0,
        EGL_NONE
    };
    EGLint numConfigs = 0;
    if (!eglChooseConfig(display_, configAttrs, &config_, 1, &numConfigs) || numConfigs < 1) {
        LOGE("eglChooseConfig failed");
        return false;
    }

    const EGLint ctxAttrs[] = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE };
    context_ = eglCreateContext(display_, config_, EGL_NO_CONTEXT, ctxAttrs);
    if (context_ == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext(ES3) failed");
        return false;
    }

    // 1x1 pbuffer keeps the context current when no window exists (background, early boot).
    const EGLint pbAttrs[] = { EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE };
    pbufferSurface_ = eglCreatePbufferSurface(display_, config_, pbAttrs);
    if (pbufferSurface_ == EGL_NO_SURFACE) {
        LOGE("eglCreatePbufferSurface failed");
        return false;
    }
    if (!eglMakeCurrent(display_, pbufferSurface_, pbufferSurface_, context_)) {
        LOGE("eglMakeCurrent(pbuffer) failed");
        return false;
    }
    LOGI("EGL ES3 context up: %s / %s", glGetString(GL_RENDERER), glGetString(GL_VERSION));
    return true;
}

bool VideoGL::captureFrame(std::vector<uint8_t>& out, unsigned& w, unsigned& h) {
    if (!haveFrame_ || frameW_ == 0 || frameH_ == 0) {
        LOGW("captureFrame: no frame yet (presented=%llu duped=%llu hw=%d)",
             (unsigned long long)stats.framesPresented.load(),
             (unsigned long long)stats.framesDuped.load(), (int)frameIsHw_);
        return false;
    }
    GLuint srcTex = frameIsHw_ ? fboTexture_ : swTexture_;
    if (!srcTex) {
        LOGW("captureFrame: no source texture (hw=%d)", (int)frameIsHw_);
        return false;
    }

    w = frameW_;
    h = frameH_;
    out.resize((size_t)w * h * 4);

    // GL error flags are sticky — drain anything the core left behind so the check below
    // reflects OUR readback, not its rendering.
    while (glGetError() != GL_NO_ERROR) {}

    // Read through a transient FBO so both paths (core FBO texture / sw upload texture)
    // use the same glReadPixels; ES3 guarantees GL_RGBA+GL_UNSIGNED_BYTE readback for
    // normalized color attachments.
    GLint prevFbo = 0;
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &prevFbo);
    GLuint readFbo = 0;
    glGenFramebuffers(1, &readFbo);
    glBindFramebuffer(GL_FRAMEBUFFER, readFbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, srcTex, 0);
    GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    bool ok = status == GL_FRAMEBUFFER_COMPLETE;
    if (!ok) {
        LOGW("captureFrame: transient FBO incomplete (0x%x)", status);
    } else {
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadPixels(0, 0, (GLsizei)w, (GLsizei)h, GL_RGBA, GL_UNSIGNED_BYTE, out.data());
        GLenum err = glGetError();
        ok = err == GL_NO_ERROR;
        if (!ok) LOGW("captureFrame: glReadPixels error 0x%x (%ux%u)", err, w, h);
    }
    glBindFramebuffer(GL_FRAMEBUFFER, (GLuint)prevFbo);
    glDeleteFramebuffers(1, &readFbo);
    if (!ok) return false;

    if (frameIsHw_) {
        // hw frames are GL bottom-up — flip rows so the dump is top-down.
        const size_t stride = (size_t)w * 4;
        std::vector<uint8_t> rowTmp(stride);
        for (unsigned y = 0; y < h / 2; y++) {
            uint8_t* a = out.data() + (size_t)y * stride;
            uint8_t* b = out.data() + (size_t)(h - 1 - y) * stride;
            memcpy(rowTmp.data(), a, stride);
            memcpy(a, b, stride);
            memcpy(b, rowTmp.data(), stride);
        }
    }
    return true;
}

void VideoGL::notifyContextDestroy() {
    if (hwEnabled_ && contextResetDone_ && hwCb_.context_destroy) {
        LOGI("calling hw context_destroy");
        hwCb_.context_destroy();
    }
    hwCb_ = retro_hw_render_callback{};
    hwEnabled_ = false;
    contextResetDone_ = false;
}

void VideoGL::shutdown() {
    if (display_ == EGL_NO_DISPLAY) return;
    // context_destroy must have been fired via notifyContextDestroy() BEFORE the core was
    // dlclosed; by now the callback pointers are cleared.

    eglMakeCurrent(display_, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    if (fboDepthStencil_) glDeleteRenderbuffers(1, &fboDepthStencil_);
    if (fboTexture_) glDeleteTextures(1, &fboTexture_);
    if (fbo_) glDeleteFramebuffers(1, &fbo_);
    if (swTexture_) glDeleteTextures(1, &swTexture_);
    if (program_) glDeleteProgram(program_);
    if (vbo_) glDeleteBuffers(1, &vbo_);
    for (auto& p : post_) if (p.program) glDeleteProgram(p.program);
    if (postVbo_) glDeleteBuffers(1, &postVbo_);
    if (sceneTex_) glDeleteTextures(1, &sceneTex_);
    if (sceneFbo_) glDeleteFramebuffers(1, &sceneFbo_);
    if (pingTex_) glDeleteTextures(1, &pingTex_);
    if (pingFbo_) glDeleteFramebuffers(1, &pingFbo_);
    if (windowSurface_ != EGL_NO_SURFACE) eglDestroySurface(display_, windowSurface_);
    if (pbufferSurface_ != EGL_NO_SURFACE) eglDestroySurface(display_, pbufferSurface_);
    if (context_ != EGL_NO_CONTEXT) eglDestroyContext(display_, context_);
    eglTerminate(display_);
    display_ = EGL_NO_DISPLAY;
    context_ = EGL_NO_CONTEXT;
    windowSurface_ = pbufferSurface_ = EGL_NO_SURFACE;
    if (window_) { ANativeWindow_release(window_); window_ = nullptr; }
    {
        // Drop any unconsumed pending window handed over after the loop exited.
        std::lock_guard<std::mutex> lock(pendingMutex_);
        if (pendingWindowChanged_ && pendingWindow_) ANativeWindow_release(pendingWindow_);
        pendingWindow_ = nullptr;
        pendingWindowChanged_ = false;
    }
    free(convertScratch_);
    convertScratch_ = nullptr;
    convertScratchSize_ = 0;

    // Reset all GL object ids + frame state so a subsequent session starts clean.
    fbo_ = fboTexture_ = fboDepthStencil_ = 0;
    fboW_ = fboH_ = 0;
    swTexture_ = 0;
    swTexW_ = swTexH_ = 0;
    swTexIs565_ = false;
    program_ = 0;
    vbo_ = 0;
    locPos_ = locTex_ = locSampler_ = locFlipY_ = locSwapRB_ = locPosRot_ = -1;
    for (auto& p : post_) p = PostProgram{};
    postBuilt_ = false;
    postVbo_ = 0;
    sceneFbo_ = sceneTex_ = 0; sceneW_ = sceneH_ = 0;
    pingFbo_ = pingTex_ = 0; pingW_ = pingH_ = 0;
    hwEnabled_ = false;
    hwCb_ = retro_hw_render_callback{};
    contextResetDone_ = false;
    haveFrame_ = false;
    frameIsHw_ = false;
    frameW_ = frameH_ = 0;
    pixelFormat_ = RETRO_PIXEL_FORMAT_0RGB1555;
    lastPresentUs_ = 0;
    config_ = nullptr;
}

void VideoGL::setPendingWindow(ANativeWindow* window) {
    std::lock_guard<std::mutex> lock(pendingMutex_);
    // Drop an unconsumed previous pending window.
    if (pendingWindowChanged_ && pendingWindow_ && pendingWindow_ != window) {
        ANativeWindow_release(pendingWindow_);
    }
    pendingWindow_ = window;
    pendingWindowChanged_ = true;
}

void VideoGL::applyPendingWindow() {
    ANativeWindow* next = nullptr;
    {
        std::lock_guard<std::mutex> lock(pendingMutex_);
        if (!pendingWindowChanged_) return;
        next = pendingWindow_;
        pendingWindow_ = nullptr;
        pendingWindowChanged_ = false;
    }

    // Tear down the old window surface.
    if (windowSurface_ != EGL_NO_SURFACE) {
        eglMakeCurrent(display_, pbufferSurface_, pbufferSurface_, context_);
        eglDestroySurface(display_, windowSurface_);
        windowSurface_ = EGL_NO_SURFACE;
    }
    if (window_) { ANativeWindow_release(window_); window_ = nullptr; }

    window_ = next;
    if (!window_) {
        stats.surfaceWidth = 0;
        stats.surfaceHeight = 0;
        LOGI("window surface released (backgrounded)");
        return;
    }

    windowSurface_ = eglCreateWindowSurface(display_, config_, window_, nullptr);
    if (windowSurface_ == EGL_NO_SURFACE) {
        LOGE("eglCreateWindowSurface failed: 0x%x", eglGetError());
        ANativeWindow_release(window_);
        window_ = nullptr;
        return;
    }
    if (!eglMakeCurrent(display_, windowSurface_, windowSurface_, context_)) {
        LOGE("eglMakeCurrent(window) failed: 0x%x", eglGetError());
        return;
    }
    EGLint w = 0, h = 0;
    eglQuerySurface(display_, windowSurface_, EGL_WIDTH, &w);
    eglQuerySurface(display_, windowSurface_, EGL_HEIGHT, &h);
    stats.surfaceWidth = w;
    stats.surfaceHeight = h;
    LOGI("window surface up: %dx%d", w, h);
}

bool VideoGL::enableHwRender(retro_hw_render_callback* cb, unsigned maxWidth, unsigned maxHeight) {
    if (cb->context_type != RETRO_HW_CONTEXT_OPENGLES3 &&
        cb->context_type != RETRO_HW_CONTEXT_OPENGLES2 &&
        cb->context_type != RETRO_HW_CONTEXT_OPENGLES_VERSION) {
        LOGE("SET_HW_RENDER: unsupported context type %d", (int)cb->context_type);
        return false;
    }
    hwCb_ = *cb;
    hwEnabled_ = true;
    contextResetDone_ = false;

    fboW_ = maxWidth ? maxWidth : 1920;
    fboH_ = maxHeight ? maxHeight : 1088;

    glGenFramebuffers(1, &fbo_);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo_);

    glGenTextures(1, &fboTexture_);
    glBindTexture(GL_TEXTURE_2D, fboTexture_);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, (GLsizei)fboW_, (GLsizei)fboH_, 0,
                 GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, fboTexture_, 0);

    if (cb->depth) {
        glGenRenderbuffers(1, &fboDepthStencil_);
        glBindRenderbuffer(GL_RENDERBUFFER, fboDepthStencil_);
        glRenderbufferStorage(GL_RENDERBUFFER,
                              cb->stencil ? GL_DEPTH24_STENCIL8 : GL_DEPTH_COMPONENT24,
                              (GLsizei)fboW_, (GLsizei)fboH_);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER,
                                  cb->stencil ? GL_DEPTH_STENCIL_ATTACHMENT : GL_DEPTH_ATTACHMENT,
                                  GL_RENDERBUFFER, fboDepthStencil_);
    }

    GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    if (status != GL_FRAMEBUFFER_COMPLETE) {
        LOGE("hw-render FBO incomplete: 0x%x", status);
        return false;
    }
    LOGI("hw-render FBO ready: %ux%u (depth=%d stencil=%d bottom_left_origin=%d)",
         fboW_, fboH_, cb->depth, cb->stencil, (int)cb->bottom_left_origin);
    return true;
}

void VideoGL::callContextResetOnce() {
    if (hwEnabled_ && !contextResetDone_ && hwCb_.context_reset) {
        LOGI("calling hw context_reset");
        hwCb_.context_reset();
        contextResetDone_ = true;
    }
}

void VideoGL::setGeometry(unsigned baseW, unsigned baseH, float aspect) {
    baseW_ = baseW ? baseW : 320;
    baseH_ = baseH ? baseH : 240;
    aspect_ = aspect > 0.0f ? aspect : (float)baseW_ / (float)baseH_;
}

void VideoGL::submitSoftwareFrame(const void* data, unsigned width, unsigned height, size_t pitch) {
    uploadSoftwareFrame(data, width, height, pitch);
    frameW_ = width;
    frameH_ = height;
    frameIsHw_ = false;
    haveFrame_ = true;
}

void VideoGL::markHwFrame(unsigned width, unsigned height) {
    // Some cores (PPSSPP) submit RETRO_HW_FRAME_BUFFER_VALID with 0×0 dims — fall back to
    // the core-reported base geometry (the rendered sub-rect of our FBO). Without this the
    // UV sub-rect math degenerates to zero and the screen stays black.
    frameW_ = width ? width : baseW_;
    frameH_ = height ? height : baseH_;
    frameIsHw_ = true;
    haveFrame_ = true;
}

void VideoGL::markDupeFrame() {
    stats.framesDuped.fetch_add(1);
}

bool VideoGL::ensureQuadPipeline() {
    if (program_) return true;

    GLuint vs = compileShader(GL_VERTEX_SHADER, kVertexShader);
    GLuint fs = compileShader(GL_FRAGMENT_SHADER, kFragmentShader);
    if (!vs || !fs) return false;
    program_ = glCreateProgram();
    glAttachShader(program_, vs);
    glAttachShader(program_, fs);
    glLinkProgram(program_);
    glDeleteShader(vs);
    glDeleteShader(fs);
    GLint ok = 0;
    glGetProgramiv(program_, GL_LINK_STATUS, &ok);
    if (!ok) {
        LOGE("program link failed");
        glDeleteProgram(program_);
        program_ = 0;
        return false;
    }
    locPos_ = glGetAttribLocation(program_, "aPos");
    locTex_ = glGetAttribLocation(program_, "aTex");
    locSampler_ = glGetUniformLocation(program_, "uSampler");
    locFlipY_ = glGetUniformLocation(program_, "uFlipY");
    locSwapRB_ = glGetUniformLocation(program_, "uSwapRB");
    locPosRot_ = glGetUniformLocation(program_, "uPosRot");

    // x, y, u, v — fullscreen quad as a triangle strip
    const float verts[] = {
        -1.f, -1.f, 0.f, 1.f,
         1.f, -1.f, 1.f, 1.f,
        -1.f,  1.f, 0.f, 0.f,
         1.f,  1.f, 1.f, 0.f,
    };
    glGenBuffers(1, &vbo_);
    glBindBuffer(GL_ARRAY_BUFFER, vbo_);
    glBufferData(GL_ARRAY_BUFFER, sizeof(verts), verts, GL_STATIC_DRAW);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    return true;
}

void VideoGL::uploadSoftwareFrame(const void* data, unsigned width, unsigned height, size_t pitch) {
    const bool is565 = pixelFormat_ == RETRO_PIXEL_FORMAT_RGB565 ||
                       pixelFormat_ == RETRO_PIXEL_FORMAT_0RGB1555; // 1555 converted to 565 below
    const void* uploadData = data;
    size_t uploadPitch = pitch;

    if (pixelFormat_ == RETRO_PIXEL_FORMAT_0RGB1555) {
        // Rare legacy format — CPU-convert to 565.
        size_t needed = (size_t)width * height * 2;
        if (convertScratchSize_ < needed) {
            convertScratch_ = (uint16_t*)realloc(convertScratch_, needed);
            convertScratchSize_ = needed;
        }
        const auto* src = (const uint8_t*)data;
        for (unsigned y = 0; y < height; y++) {
            const auto* row = (const uint16_t*)(src + y * pitch);
            uint16_t* dst = convertScratch_ + (size_t)y * width;
            for (unsigned x = 0; x < width; x++) {
                uint16_t p = row[x];
                uint16_t r = (p >> 10) & 0x1F, g = (p >> 5) & 0x1F, b = p & 0x1F;
                dst[x] = (uint16_t)((r << 11) | (g << 6) | b);
            }
        }
        uploadData = convertScratch_;
        uploadPitch = (size_t)width * 2;
    }

    const unsigned bpp = is565 ? 2 : 4;
    if (!swTexture_ || swTexW_ != width || swTexH_ != height || swTexIs565_ != is565) {
        if (swTexture_) glDeleteTextures(1, &swTexture_);
        glGenTextures(1, &swTexture_);
        glBindTexture(GL_TEXTURE_2D, swTexture_);
        glTexImage2D(GL_TEXTURE_2D, 0, is565 ? GL_RGB565 : GL_RGBA8,
                     (GLsizei)width, (GLsizei)height, 0,
                     is565 ? GL_RGB : GL_RGBA,
                     is565 ? GL_UNSIGNED_SHORT_5_6_5 : GL_UNSIGNED_BYTE, nullptr);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        swTexW_ = width;
        swTexH_ = height;
        swTexIs565_ = is565;
    } else {
        glBindTexture(GL_TEXTURE_2D, swTexture_);
    }

    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glPixelStorei(GL_UNPACK_ROW_LENGTH, (GLint)(uploadPitch / bpp));
    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, (GLsizei)width, (GLsizei)height,
                    is565 ? GL_RGB : GL_RGBA,
                    is565 ? GL_UNSIGNED_SHORT_5_6_5 : GL_UNSIGNED_BYTE, uploadData);
    glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
}

void VideoGL::resetPresentGlState() {
    // The libretro core shares this context and may leave state enabled; never assume.
    // Scissor MUST be disabled before any glClear or letterbox bars get clipped away.
    glDisable(GL_SCISSOR_TEST);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);
    glDisable(GL_BLEND);
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
    glClearColor(0.f, 0.f, 0.f, 1.f);
    glActiveTexture(GL_TEXTURE0);
}

void VideoGL::computeViewport(float srcAspect, int intSrcW, int intSrcH, ScaleMode mode,
                              int rotationDeg, int surfW, int surfH,
                              int& vpX, int& vpY, int& vpW, int& vpH) {
    const int d = ((rotationDeg % 360) + 360) % 360;
    if (d == 90 || d == 270) {                  // 90/270 swap the displayed aspect + pixel dims
        srcAspect = srcAspect > 0.f ? 1.f / srcAspect : 1.f;
        const int t = intSrcW; intSrcW = intSrcH; intSrcH = t;
    }
    if (srcAspect <= 0.f) srcAspect = 1.f;

    if (mode == ScaleMode::Stretch) {
        vpX = 0; vpY = 0; vpW = surfW; vpH = surfH;
        return;
    }
    if (mode == ScaleMode::Integer && intSrcW > 0 && intSrcH > 0 &&
        surfW >= intSrcW && surfH >= intSrcH) {
        int sx = surfW / intSrcW, sy = surfH / intSrcH;
        int scale = sx < sy ? sx : sy;
        if (scale >= 1) {
            vpW = intSrcW * scale; vpH = intSrcH * scale;
            vpX = (surfW - vpW) / 2; vpY = (surfH - vpH) / 2;
            return;
        }
        // scale would be 0 (screen smaller than one native frame): fall through to FIT.
    }
    // FIT (letterbox / pillarbox), also the Integer-downscale fallback.
    const float dstAspect = (float)surfW / (float)surfH;
    if (dstAspect > srcAspect) {                 // screen wider than source -> pillarbox
        vpH = surfH; vpW = (int)(surfH * srcAspect + 0.5f);
    } else {                                     // screen taller -> letterbox
        vpW = surfW; vpH = (int)(surfW / srcAspect + 0.5f);
    }
    if (vpW > surfW) vpW = surfW;
    if (vpH > surfH) vpH = surfH;
    vpX = (surfW - vpW) / 2; vpY = (surfH - vpH) / 2;
}

void VideoGL::bindGameSource(float& u, float& vSpan, float& swapRB,
                             bool& bottomLeftOrigin, GLuint& tex) {
    // The core renders its frame into a sub-rectangle [0,u]×[0,vSpan] of a possibly-oversized
    // texture (the hw FBO is sized to a max; PPSSPP reports its true size via geometry, and
    // passes 0×0 in the frame callback so frameW_/frameH_ come from that geometry).
    if (frameIsHw_) {
        tex = fboTexture_;
        swapRB = 0.0f;
        u = fboW_ ? (float)frameW_ / (float)fboW_ : 1.0f;
        vSpan = fboH_ ? (float)frameH_ / (float)fboH_ : 1.0f;
        bottomLeftOrigin = hwCb_.bottom_left_origin; // GL FBO: row 0 is the image bottom
    } else {
        tex = swTexture_;
        swapRB = swTexIs565_ ? 0.0f : 1.0f;          // XRGB8888 memory order is B,G,R,X
        u = 1.0f; vSpan = 1.0f;
        bottomLeftOrigin = false;                    // software frames are top-left origin
    }
    if (u <= 0.0f) u = 1.0f;
    if (vSpan <= 0.0f) vSpan = 1.0f;
}

void VideoGL::drawGameQuad(int, int, int, int, int rotationDeg, bool forResolve) {
    float u, vSpan, swapRB; bool bottomLeftOrigin; GLuint tex = 0;
    bindGameSource(u, vSpan, swapRB, bottomLeftOrigin, tex);

    glUseProgram(program_);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, tex);

    // We bake the vertical orientation straight into the quad's V coords — NOT via uFlipY,
    // which would flip across the FULL texture and land on the empty top of an oversized FBO.
    const float vBottom = bottomLeftOrigin ? 0.0f : vSpan;
    const float vTop    = bottomLeftOrigin ? vSpan : 0.0f;
    const float verts[] = {
        -1.f, -1.f, 0.f, vBottom,
         1.f, -1.f, u,   vBottom,
        -1.f,  1.f, 0.f, vTop,
         1.f,  1.f, u,   vTop,
    };
    glBindBuffer(GL_ARRAY_BUFFER, vbo_);
    glBufferData(GL_ARRAY_BUFFER, sizeof(verts), verts, GL_DYNAMIC_DRAW);

    // Rotation applies only when drawing to the screen; the scene-resolve pass stays upright.
    float rot[4]; rotationMat2(forResolve ? 0 : rotationDeg, rot);
    glUniform1i(locSampler_, 0);
    glUniform1f(locFlipY_, 0.0f);
    glUniform1f(locSwapRB_, swapRB);
    if (locPosRot_ >= 0) glUniformMatrix2fv(locPosRot_, 1, GL_FALSE, rot);
    glEnableVertexAttribArray((GLuint)locPos_);
    glEnableVertexAttribArray((GLuint)locTex_);
    glVertexAttribPointer((GLuint)locPos_, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), (void*)0);
    glVertexAttribPointer((GLuint)locTex_, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float),
                          (void*)(2 * sizeof(float)));
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    glDisableVertexAttribArray((GLuint)locPos_);
    glDisableVertexAttribArray((GLuint)locTex_);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
}

void VideoGL::drawPostQuad(int id, GLuint srcTex, int, int, int vpW, int vpH,
                           int rotationDeg, const DisplayConfig& cfg) {
    if (id <= 0 || id >= kPostShaderCount) return;
    const PostProgram& p = post_[id];
    if (!p.program) return;

    glUseProgram(p.program);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, srcTex);

    float rot[4]; rotationMat2(rotationDeg, rot);
    if (p.locSampler >= 0) glUniform1i(p.locSampler, 0);
    if (p.locPosRot >= 0) glUniformMatrix2fv(p.locPosRot, 1, GL_FALSE, rot);
    if (p.locTexel >= 0) {
        // All offscreen targets are native-res, so the scene texel size is correct every pass.
        const float tw = sceneW_ > 0 ? 1.0f / (float)sceneW_ : 1.0f / 480.0f;
        const float th = sceneH_ > 0 ? 1.0f / (float)sceneH_ : 1.0f / 272.0f;
        glUniform2f(p.locTexel, tw, th);
    }
    if (p.locOutputSize >= 0) glUniform2f(p.locOutputSize, (float)vpW, (float)vpH);
    if (p.locScanlineIntensity >= 0) glUniform1f(p.locScanlineIntensity, cfg.scanlineIntensity);
    if (p.locMaskIntensity >= 0) glUniform1f(p.locMaskIntensity, cfg.maskIntensity);
    if (p.locSharpenAmount >= 0) glUniform1f(p.locSharpenAmount, cfg.sharpenAmount);

    glBindBuffer(GL_ARRAY_BUFFER, postVbo_);
    glEnableVertexAttribArray((GLuint)p.locPos);
    glEnableVertexAttribArray((GLuint)p.locTex);
    glVertexAttribPointer((GLuint)p.locPos, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), (void*)0);
    glVertexAttribPointer((GLuint)p.locTex, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float),
                          (void*)(2 * sizeof(float)));
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    glDisableVertexAttribArray((GLuint)p.locPos);
    glDisableVertexAttribArray((GLuint)p.locTex);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
}

bool VideoGL::ensurePostPipeline() {
    if (postBuilt_) {
        return post_[kPostScanlineCrt].program || post_[kPostFsrSharpen].program ||
               post_[kPostSharpBilinear].program;
    }
    postBuilt_ = true;

    if (!postVbo_) {
        // Static identity quad: pos (-1..1) + upright UV; sampled scene texture is already clean.
        const float verts[] = {
            -1.f, -1.f, 0.f, 0.f,
             1.f, -1.f, 1.f, 0.f,
            -1.f,  1.f, 0.f, 1.f,
             1.f,  1.f, 1.f, 1.f,
        };
        glGenBuffers(1, &postVbo_);
        glBindBuffer(GL_ARRAY_BUFFER, postVbo_);
        glBufferData(GL_ARRAY_BUFFER, sizeof(verts), verts, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    GLuint vs = compileShader(GL_VERTEX_SHADER, kPostVertexShader);
    if (!vs) return false;
    bool any = false;
    for (int id = 1; id < kPostShaderCount; id++) {
        const char* frag = kPostFragForId(id);
        if (!frag) continue;
        GLuint fs = compileShader(GL_FRAGMENT_SHADER, frag);
        if (!fs) continue;
        GLuint prog = glCreateProgram();
        glAttachShader(prog, vs);
        glAttachShader(prog, fs);
        glLinkProgram(prog);
        glDeleteShader(fs);
        GLint ok = 0;
        glGetProgramiv(prog, GL_LINK_STATUS, &ok);
        if (!ok) {
            char log[512]; glGetProgramInfoLog(prog, sizeof(log), nullptr, log);
            LOGE("post shader %d link failed: %s", id, log);
            glDeleteProgram(prog);
            continue;
        }
        PostProgram& p = post_[id];
        p.program = prog;
        p.locPos = glGetAttribLocation(prog, "aPos");
        p.locTex = glGetAttribLocation(prog, "aTex");
        p.locPosRot = glGetUniformLocation(prog, "uPosRot");
        p.locSampler = glGetUniformLocation(prog, "uSampler");
        p.locTexel = glGetUniformLocation(prog, "uTexel");
        p.locOutputSize = glGetUniformLocation(prog, "uOutputSize");
        p.locScanlineIntensity = glGetUniformLocation(prog, "uScanlineIntensity");
        p.locMaskIntensity = glGetUniformLocation(prog, "uMaskIntensity");
        p.locSharpenAmount = glGetUniformLocation(prog, "uSharpenAmount");
        any = true;
    }
    glDeleteShader(vs); // safe: kept alive while attached, freed with each program
    return any;
}

bool VideoGL::ensureSceneTarget(GLuint& fbo, GLuint& tex, int& w, int& h, int wantW, int wantH) {
    if (wantW <= 0 || wantH <= 0) return false;
    if (fbo && tex && w == wantW && h == wantH) return true;
    if (tex) { glDeleteTextures(1, &tex); tex = 0; }
    if (fbo) { glDeleteFramebuffers(1, &fbo); fbo = 0; }

    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, wantW, wantH, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
    GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    if (status != GL_FRAMEBUFFER_COMPLETE) {
        LOGE("scene FBO incomplete 0x%x (%dx%d)", status, wantW, wantH);
        glDeleteTextures(1, &tex); tex = 0;
        glDeleteFramebuffers(1, &fbo); fbo = 0;
        return false;
    }
    w = wantW; h = wantH;
    return true;
}

void VideoGL::setDisplayConfig(const DisplayConfig& cfg) {
    std::lock_guard<std::mutex> lock(displayCfgMutex_);
    displayCfg_ = cfg;
}

int VideoGL::shaderSelfTest() {
    int mask = 0;
    if (ensureQuadPipeline() && program_) mask |= (1 << 0);
    ensurePostPipeline();
    for (int id = 1; id < kPostShaderCount; id++) {
        if (post_[id].program) mask |= (1 << id);
    }
    return mask;
}

void VideoGL::drawFrame() {
    if (!ensureQuadPipeline() || !haveFrame_) {
        glDisable(GL_SCISSOR_TEST);
        glClearColor(0.03f, 0.04f, 0.07f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        return;
    }

    const int sw = stats.surfaceWidth.load(), sh = stats.surfaceHeight.load();
    if (sw <= 0 || sh <= 0) return;

    DisplayConfig cfg;
    { std::lock_guard<std::mutex> lock(displayCfgMutex_); cfg = displayCfg_; }

    const int srcW = (int)frameW_, srcH = (int)frameH_;
    int vpX, vpY, vpW, vpH;
    computeViewport(aspect_, srcW, srcH, cfg.scaleMode, cfg.rotationDeg, sw, sh, vpX, vpY, vpW, vpH);

    resetPresentGlState();

    const bool wantPost = (cfg.pass1 != kPostNone || cfg.pass2 != kPostNone) &&
                          srcW > 0 && srcH > 0 && ensurePostPipeline();

    // Fast path (no post shaders): draw the game frame straight to the drawable, rotated+scaled.
    if (!wantPost) {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(vpX, vpY, vpW, vpH);
        glClear(GL_COLOR_BUFFER_BIT);
        drawGameQuad(vpX, vpY, vpW, vpH, cfg.rotationDeg, /*forResolve=*/false);
        return;
    }

    // Post path: resolve the game frame into a clean native scene texture (single V-flip, clamped
    // edges), then run 1..2 stacked passes; only the final pass rotates + scales to the screen.
    if (!ensureSceneTarget(sceneFbo_, sceneTex_, sceneW_, sceneH_, srcW, srcH)) {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(vpX, vpY, vpW, vpH);
        glClear(GL_COLOR_BUFFER_BIT);
        drawGameQuad(vpX, vpY, vpW, vpH, cfg.rotationDeg, false);
        return;
    }
    glBindFramebuffer(GL_FRAMEBUFFER, sceneFbo_);
    glViewport(0, 0, srcW, srcH);
    glClear(GL_COLOR_BUFFER_BIT);
    drawGameQuad(0, 0, srcW, srcH, /*rotationDeg=*/0, /*forResolve=*/true);

    int passes[2]; int n = 0;
    if (cfg.pass1 != kPostNone) passes[n++] = cfg.pass1;
    if (cfg.pass2 != kPostNone) passes[n++] = cfg.pass2;

    GLuint curTex = sceneTex_;
    for (int i = 0; i < n; i++) {
        const bool last = (i == n - 1);
        if (!last && ensureSceneTarget(pingFbo_, pingTex_, pingW_, pingH_, srcW, srcH)) {
            // Intermediate pass: axis-aligned, native-res, no rotation, into the ping target.
            glBindFramebuffer(GL_FRAMEBUFFER, pingFbo_);
            glViewport(0, 0, srcW, srcH);
            glClear(GL_COLOR_BUFFER_BIT);
            drawPostQuad(passes[i], curTex, 0, 0, srcW, srcH, /*rotationDeg=*/0, cfg);
            curTex = pingTex_;
        } else {
            // Final pass (or ping alloc failed): rotate + scale to the drawable.
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glViewport(vpX, vpY, vpW, vpH);
            glClear(GL_COLOR_BUFFER_BIT);
            drawPostQuad(passes[i], curTex, vpX, vpY, vpW, vpH, cfg.rotationDeg, cfg);
            return;
        }
    }
}

// Swappy hooks are provided by the host (libretro_host.cpp) via these weak-ish externs.
bool pulsar_swappy_swap(EGLDisplay display, EGLSurface surface);

void VideoGL::present() {
    if (windowSurface_ == EGL_NO_SURFACE) return;

    drawFrame();

    bool swapped = pulsar_swappy_swap(display_, windowSurface_);
    if (!swapped) {
        eglSwapBuffers(display_, windowSurface_);
    }

    int64_t t = nowUs();
    if (lastPresentUs_ > 0) {
        uint64_t interval = (uint64_t)(t - lastPresentUs_);
        uint64_t avg = stats.avgFrameIntervalUs.load();
        stats.avgFrameIntervalUs = avg == 0 ? interval : (avg * 7 + interval) / 8;
    }
    lastPresentUs_ = t;
    stats.framesPresented.fetch_add(1);
}
