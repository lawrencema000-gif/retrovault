#include "video_gl.h"

#include <android/log.h>
#include <cstdlib>
#include <cstring>
#include <ctime>

#define LOG_TAG "pulsar_video"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
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
void main() {
    vTex = vec2(aTex.x, mix(aTex.y, 1.0 - aTex.y, uFlipY));
    gl_Position = vec4(aPos, 0.0, 1.0);
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
    locPos_ = locTex_ = locSampler_ = locFlipY_ = locSwapRB_ = -1;
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
    LOGI("hw-render FBO ready: %ux%u (depth=%d stencil=%d)", fboW_, fboH_, cb->depth, cb->stencil);
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
    frameW_ = width;
    frameH_ = height;
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

void VideoGL::drawFrame() {
    if (!ensureQuadPipeline() || !haveFrame_) {
        glClearColor(0.03f, 0.04f, 0.07f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        return;
    }

    const int sw = stats.surfaceWidth.load(), sh = stats.surfaceHeight.load();
    if (sw <= 0 || sh <= 0) return;

    // Letterbox to the core-reported aspect ratio.
    float targetAspect = aspect_;
    int vw = sw, vh = (int)(sw / targetAspect + 0.5f);
    if (vh > sh) { vh = sh; vw = (int)(sh * targetAspect + 0.5f); }
    glViewport((sw - vw) / 2, (sh - vh) / 2, vw, vh);

    glDisable(GL_DEPTH_TEST);
    glDisable(GL_BLEND);
    glClearColor(0.f, 0.f, 0.f, 1.f);
    glClear(GL_COLOR_BUFFER_BIT);

    glUseProgram(program_);
    glActiveTexture(GL_TEXTURE0);

    float flipY, swapRB;
    if (frameIsHw_) {
        glBindTexture(GL_TEXTURE_2D, fboTexture_);
        // GL FBO content is bottom-left origin; base quad UVs already flip (v: 1 at bottom).
        flipY = hwCb_.bottom_left_origin ? 1.0f : 0.0f;
        swapRB = 0.0f;
    } else {
        glBindTexture(GL_TEXTURE_2D, swTexture_);
        flipY = 0.0f;
        // libretro XRGB8888 memory order is B,G,R,X — swizzle in the shader.
        swapRB = swTexIs565_ ? 0.0f : 1.0f;
    }

    // For hw frames the core may render into a sub-rect of the FBO — adjust UVs.
    if (frameIsHw_ && (frameW_ != fboW_ || frameH_ != fboH_)) {
        float u = fboW_ ? (float)frameW_ / (float)fboW_ : 1.0f;
        float v = fboH_ ? (float)frameH_ / (float)fboH_ : 1.0f;
        const float verts[] = {
            -1.f, -1.f, 0.f, v,
             1.f, -1.f, u,   v,
            -1.f,  1.f, 0.f, 0.f,
             1.f,  1.f, u,   0.f,
        };
        glBindBuffer(GL_ARRAY_BUFFER, vbo_);
        glBufferData(GL_ARRAY_BUFFER, sizeof(verts), verts, GL_DYNAMIC_DRAW);
    } else {
        const float verts[] = {
            -1.f, -1.f, 0.f, 1.f,
             1.f, -1.f, 1.f, 1.f,
            -1.f,  1.f, 0.f, 0.f,
             1.f,  1.f, 1.f, 0.f,
        };
        glBindBuffer(GL_ARRAY_BUFFER, vbo_);
        glBufferData(GL_ARRAY_BUFFER, sizeof(verts), verts, GL_DYNAMIC_DRAW);
    }

    glUniform1i(locSampler_, 0);
    glUniform1f(locFlipY_, flipY);
    glUniform1f(locSwapRB_, swapRB);
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
