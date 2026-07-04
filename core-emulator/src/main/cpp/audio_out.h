// Pulsar audio output: Oboe (AAudio) low-latency stream fed by a lock-free SPSC ring buffer,
// with libretro-style dynamic rate control — the emu thread resamples core audio by a ratio
// nudged ±0.5% from buffer occupancy, so the buffer never drains (crackle) or fills (latency).
//
// Threading: writeFrames() is called on the emu/render thread (single producer);
// onAudioReady() runs on Oboe's callback thread (single consumer). Ring indices are atomics.

#pragma once

#include <oboe/Oboe.h>
#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <vector>

class AudioOut : public oboe::AudioStreamDataCallback,
                 public oboe::AudioStreamErrorCallback {
public:
    // Start the output stream. sourceRateHz = the core's reported audio rate.
    bool start(int sourceRateHz);
    void stop();
    bool isRunning() const { return running_.load(); }

    // Emu thread: push interleaved stereo int16 frames from retro_audio_sample_batch.
    // Resamples to the device rate using the current rate-control ratio.
    void writeFrames(const int16_t* data, size_t frames);

    // Call once per retro_run: recompute the rate-control ratio from buffer occupancy.
    void updateRateControl();

    // Larger buffering for Bluetooth routes (applied on next start()).
    void setBluetoothFriendly(bool bt) { btFriendly_.store(bt); }
    bool bluetoothFriendly() const { return btFriendly_.load(); }

    // If the stream died (device change), the run loop calls this to restart.
    bool needsRestart() const { return needsRestart_.load(); }
    bool restart();

    // ---- stats (for tests + future debug overlay) ----
    int64_t framesConsumed() const { return framesConsumed_.load(); }
    int64_t underrunFills() const { return underrunFills_.load(); }
    int32_t deviceXRuns() const { return deviceXRuns_.load(); }
    double fillRatio() const;
    // current rate-control deviation from 1.0 (e.g. +0.003 = producing 0.3% more frames)
    double rateDelta() const { return rateDelta_.load(); }
    int deviceSampleRate() const { return deviceRate_; }
    int bufferCapacityFrames() const { return capacityFrames_; }

    // oboe::AudioStreamDataCallback
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* stream, void* audioData, int32_t numFrames) override;

    // oboe::AudioStreamErrorCallback
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    // SPSC ring of interleaved stereo int16 frames.
    size_t ringAvailableRead() const;
    size_t ringAvailableWrite() const;

    std::shared_ptr<oboe::AudioStream> stream_;
    std::mutex streamMutex_;

    std::vector<int16_t> ring_;          // capacity * 2 samples
    int capacityFrames_ = 0;             // power of two
    std::atomic<size_t> readPos_{0};     // frame index (monotonic, wraps via mask)
    std::atomic<size_t> writePos_{0};

    int sourceRate_ = 44100;
    int deviceRate_ = 48000;
    std::atomic<bool> running_{false};
    std::atomic<bool> btFriendly_{false};
    std::atomic<bool> needsRestart_{false};

    // resampler state (emu thread only)
    double resamplePhase_ = 0.0;
    std::atomic<double> ratio_{1.0};      // output frames per input frame (incl. rate control)
    std::atomic<double> rateDelta_{0.0};
    int16_t lastL_ = 0, lastR_ = 0;

    std::atomic<int64_t> framesConsumed_{0};
    std::atomic<int64_t> underrunFills_{0};
    std::atomic<int32_t> deviceXRuns_{0};
};
