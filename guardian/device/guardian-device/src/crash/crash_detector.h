#pragma once
// Streaming crash detector + state machine. Pure C++ (no Arduino), unit-tested natively.
// Reference implementation: simulator/vehicle-simulator/crash_engine.py

#include <array>

#include "config/crash_config.h"
#include "guardian_types.h"

namespace guardian {

constexpr float GRAVITY = 9.80665f;
constexpr size_t MAX_WINDOW_SAMPLES = 256;

// Pure scoring function over a captured window.
CrashResult analyzeWindow(const uint32_t* times, const float* dynG, const float* rot, size_t n, bool speedValid,
                          float speedBefore, float speedAfter, uint32_t triggerMs, const CrashConfig& cfg);

Severity classify(float confidence, const CrashConfig& cfg);

class CrashDetector {
public:
    explicit CrashDetector(const CrashConfig& cfg = CrashConfig()) : cfg_(cfg) {}

    // Feed one IMU sample (+ optional vehicle speed). Returns true exactly once per incident
    // when an emergency must be transmitted; the result is then available via lastResult().
    bool process(const SensorSample& s, bool speedValid, float speedKmh);
    // Advance time without a sample (verification timeout).
    bool tick(uint32_t nowMs);
    // Occupant pressed I'M SAFE during verification.
    bool cancel();
    void onTransmitStarted();
    void onAck();

    CrashState state() const { return state_; }
    const CrashResult& lastResult() const { return last_; }
    bool hasResult() const { return hasResult_; }
    uint32_t verifyDeadlineMs() const { return verifyDeadline_; }
    uint32_t minorEventCount() const { return minorEvents_; }
    const CrashConfig& config() const { return cfg_; }

    // Optional transition observer (logging / BLE notify).
    using Listener = void (*)(CrashState, void* ctx);
    void setListener(Listener l, void* ctx) { listener_ = l; listenerCtx_ = ctx; }

private:
    void go(CrashState s);
    bool analyze();
    bool raiseEmergency();
    void features(const SensorSample& s, float& dyn, float& rot);

    CrashConfig cfg_;
    CrashState state_ = CrashState::NORMAL;
    CrashResult last_{};
    bool hasResult_ = false;
    uint32_t minorEvents_ = 0;

    bool gravityInit_ = false;
    float gX_ = 0, gY_ = 0, gZ_ = 0;

    bool speedValid_ = false;
    float speed_ = 0;
    bool speedBeforeValid_ = false;
    float speedBefore_ = 0;

    uint32_t now_ = 0;
    uint32_t triggerMs_ = 0;
    uint32_t verifyDeadline_ = 0;
    uint32_t cooldownUntil_ = 0;

    size_t n_ = 0;
    std::array<uint32_t, MAX_WINDOW_SAMPLES> times_{};
    std::array<float, MAX_WINDOW_SAMPLES> dyn_{};
    std::array<float, MAX_WINDOW_SAMPLES> rot_{};

    Listener listener_ = nullptr;
    void* listenerCtx_ = nullptr;
};

}  // namespace guardian
