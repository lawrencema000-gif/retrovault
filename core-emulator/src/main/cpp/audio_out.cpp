#include "audio_out.h"

#include <android/log.h>
#include <algorithm>
#include <cstring>

#define LOG_TAG "pulsar_audio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
// Maximum rate-control deviation (libretro convention: 0.5%).
constexpr double kMaxRateDelta = 0.005;

size_t nextPow2(size_t v) {
    size_t p = 1;
    while (p < v) p <<= 1;
    return p;
}
} // namespace

bool AudioOut::start(int sourceRateHz) {
    std::lock_guard<std::mutex> lock(streamMutex_);
    if (running_.load()) return true;
    // 0 = keep the previously configured source rate (resume after background pause).
    if (sourceRateHz > 0) sourceRate_ = sourceRateHz;

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::I16)
        ->setChannelCount(2)
        ->setUsage(oboe::Usage::Game)
        ->setDataCallback(this)
        ->setErrorCallback(this);

    std::shared_ptr<oboe::AudioStream> stream;
    oboe::Result result = builder.openStream(stream);
    if (result != oboe::Result::OK) {
        LOGE("openStream failed: %s", oboe::convertToText(result));
        return false;
    }

    deviceRate_ = stream->getSampleRate();

    // Low-latency: 2 bursts. Bluetooth-friendly: 8 bursts (BT sinks buffer heavily).
    int burst = stream->getFramesPerBurst();
    if (burst > 0) {
        int bursts = btFriendly_.load() ? 8 : 2;
        stream->setBufferSizeInFrames(burst * bursts);
    }

    // Ring capacity ~250 ms at device rate (or 500 ms for BT) — the DRC target is half-full.
    double seconds = btFriendly_.load() ? 0.5 : 0.25;
    capacityFrames_ = (int)nextPow2((size_t)(deviceRate_ * seconds));
    ring_.assign((size_t)capacityFrames_ * 2, 0);
    readPos_ = 0;
    // Prefill half a ring of silence: startup callbacks read silence instead of counting
    // underruns, and the DRC starts exactly at its half-full target.
    writePos_ = (size_t)capacityFrames_ / 2;
    resamplePhase_ = 0.0;
    ratio_ = (double)deviceRate_ / (double)sourceRate_;
    rateDelta_ = 0.0;
    framesConsumed_ = 0;
    underrunFills_ = 0;
    deviceXRuns_ = 0;

    result = stream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("requestStart failed: %s", oboe::convertToText(result));
        stream->close();
        return false;
    }

    stream_ = stream;
    running_ = true;
    needsRestart_ = false;
    LOGI("audio up: src=%dHz dev=%dHz burst=%d bufFrames=%d ring=%d bt=%d",
         sourceRate_, deviceRate_, burst, stream->getBufferSizeInFrames(),
         capacityFrames_, (int)btFriendly_.load());
    return true;
}

void AudioOut::stop() {
    std::shared_ptr<oboe::AudioStream> s;
    {
        std::lock_guard<std::mutex> lock(streamMutex_);
        s = stream_;
        stream_.reset();
        running_ = false;
    }
    if (s) {
        s->stop();
        s->close();
    }
}

bool AudioOut::restart() {
    LOGI("audio restart (device change?)");
    stop();
    needsRestart_ = false;
    return start(sourceRate_);
}

size_t AudioOut::ringAvailableRead() const {
    return writePos_.load(std::memory_order_acquire) - readPos_.load(std::memory_order_acquire);
}

size_t AudioOut::ringAvailableWrite() const {
    return (size_t)capacityFrames_ - ringAvailableRead();
}

double AudioOut::fillRatio() const {
    if (capacityFrames_ == 0) return 0.0;
    return (double)ringAvailableRead() / (double)capacityFrames_;
}

void AudioOut::updateRateControl() {
    if (!running_.load() || capacityFrames_ == 0) return;
    // libretro dynamic rate control: buffer below half-full -> produce more output frames
    // (ratio up); above half-full -> fewer. Deviation capped at ±0.5%.
    double fill = fillRatio();
    double delta = kMaxRateDelta * (1.0 - 2.0 * fill);
    rateDelta_ = delta;
    ratio_ = ((double)deviceRate_ / (double)sourceRate_) * (1.0 + delta);
}

void AudioOut::writeFrames(const int16_t* data, size_t frames) {
    if (!running_.load() || frames == 0 || capacityFrames_ == 0) return;

    const size_t mask = (size_t)capacityFrames_ - 1;
    const double ratio = ratio_.load();
    size_t wp = writePos_.load(std::memory_order_relaxed);
    size_t freeFrames = (size_t)capacityFrames_ - (wp - readPos_.load(std::memory_order_acquire));

    // Linear-interpolation resampler: source rate -> device rate * (1 + rateDelta).
    // phase walks the source in steps of 1/ratio; lastL_/lastR_ carry across calls.
    double step = 1.0 / ratio;
    double phase = resamplePhase_;
    size_t produced = 0;
    size_t srcIndex = 0; // integer part of phase already consumed

    while (true) {
        size_t s0 = (size_t)phase;
        if (s0 >= frames) break;
        if (freeFrames == 0) break; // ring full — drop the remainder (DRC will pull us back)

        double frac = phase - (double)s0;
        int16_t l0 = (s0 == 0) ? lastL_ : data[(s0 - 1) * 2];
        int16_t r0 = (s0 == 0) ? lastR_ : data[(s0 - 1) * 2 + 1];
        // interpolate between previous frame (l0/r0) and current source frame
        int16_t l1 = data[s0 * 2];
        int16_t r1 = data[s0 * 2 + 1];
        int16_t outL = (int16_t)(l0 + (double)(l1 - l0) * frac);
        int16_t outR = (int16_t)(r0 + (double)(r1 - r0) * frac);

        size_t idx = (wp & mask) * 2;
        ring_[idx] = outL;
        ring_[idx + 1] = outR;
        wp++;
        freeFrames--;
        produced++;
        phase += step;
        srcIndex = s0;
    }

    (void)srcIndex;
    // Carry resampler state across batches.
    lastL_ = data[(frames - 1) * 2];
    lastR_ = data[(frames - 1) * 2 + 1];
    resamplePhase_ = phase - (double)frames; // relative to the NEXT batch's frame 0
    if (resamplePhase_ < 0.0) resamplePhase_ = 0.0;

    writePos_.store(wp, std::memory_order_release);
}

oboe::DataCallbackResult AudioOut::onAudioReady(
    oboe::AudioStream* stream, void* audioData, int32_t numFrames) {
    auto* out = (int16_t*)audioData;
    const size_t mask = (size_t)capacityFrames_ - 1;
    size_t rp = readPos_.load(std::memory_order_relaxed);
    size_t avail = writePos_.load(std::memory_order_acquire) - rp;

    size_t toCopy = std::min((size_t)numFrames, avail);
    for (size_t i = 0; i < toCopy; i++) {
        size_t idx = ((rp + i) & mask) * 2;
        out[i * 2] = ring_[idx];
        out[i * 2 + 1] = ring_[idx + 1];
    }
    if (toCopy < (size_t)numFrames) {
        memset(out + toCopy * 2, 0, ((size_t)numFrames - toCopy) * 2 * sizeof(int16_t));
        underrunFills_.fetch_add(1);
    }
    readPos_.store(rp + toCopy, std::memory_order_release);
    framesConsumed_.fetch_add(numFrames);

    auto xruns = stream->getXRunCount();
    if (xruns) deviceXRuns_ = xruns.value();
    return oboe::DataCallbackResult::Continue;
}

void AudioOut::onErrorAfterClose(oboe::AudioStream* /*stream*/, oboe::Result error) {
    LOGW("stream error-after-close: %s — flagging restart", oboe::convertToText(error));
    running_ = false;
    needsRestart_ = true;
}
