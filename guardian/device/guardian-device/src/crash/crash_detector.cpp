#include "crash/crash_detector.h"

#include <algorithm>
#include <cmath>
#include <functional>

namespace guardian {

namespace {

float kthLargest(const float* values, size_t n, size_t k) {
    if (n == 0) return 0.0f;
    std::array<float, MAX_WINDOW_SAMPLES> copy{};
    std::copy(values, values + n, copy.begin());
    size_t idx = std::min(k, n) - 1;
    std::nth_element(copy.begin(), copy.begin() + idx, copy.begin() + n, std::greater<float>());
    return copy[idx];
}

float clamp01(float v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

}  // namespace

Severity classify(float confidence, const CrashConfig& cfg) {
    if (confidence >= cfg.criticalConfidence) return Severity::CRITICAL;
    if (confidence >= cfg.moderateConfidence) return Severity::MODERATE;
    if (confidence >= cfg.minorConfidence) return Severity::MINOR;
    return Severity::NORMAL;
}

CrashResult analyzeWindow(const uint32_t* times, const float* dynG, const float* rot, size_t n, bool speedValid,
                          float speedBefore, float speedAfter, uint32_t triggerMs, const CrashConfig& cfg) {
    CrashResult r;
    r.triggerMs = triggerMs;
    r.robustImpactG = kthLargest(dynG, n, cfg.minImpactSamples);
    r.robustRotationRadS = kthLargest(rot, n, cfg.minImpactSamples);
    for (size_t i = 0; i < n; ++i) {
        r.accelerationPeakG = std::max(r.accelerationPeakG, dynG[i]);
        r.rotationPeakRadS = std::max(r.rotationPeakRadS, rot[i]);
    }

    bool haveAbove = false;
    uint32_t lastAbove = 0;
    for (size_t i = 0; i < n; ++i) {
        if (dynG[i] >= cfg.impactThresholdG) {
            if (!haveAbove || times[i] - lastAbove >= cfg.repeatedImpactGapMs) r.impactCount++;
            haveAbove = true;
            lastAbove = times[i];
        }
    }

    r.speedValid = speedValid;
    r.speedBeforeKmh = speedBefore;
    r.speedAfterKmh = speedAfter;

    bool sustained = r.robustImpactG >= cfg.impactThresholdG || r.robustRotationRadS >= cfg.rotationThresholdRadS;
    if (!sustained) {
        r.confidence = 0;
        r.reasons = REASON_SINGLE_SPIKE;
    } else {
        float weighted = cfg.weightImpact * clamp01(r.robustImpactG / cfg.severeImpactG) +
                         cfg.weightRotation * clamp01(r.robustRotationRadS / cfg.severeRotationRadS) +
                         cfg.weightRepeated * clamp01((static_cast<float>(r.impactCount) - 1.0f) / 2.0f);
        float total = cfg.weightImpact + cfg.weightRotation + cfg.weightRepeated;
        if (speedValid) {
            float drop = std::max(0.0f, speedBefore - speedAfter);
            weighted += cfg.weightSpeed * clamp01(drop / cfg.severeSpeedDropKmh);
            total += cfg.weightSpeed;
            if (drop >= cfg.speedDropThresholdKmh) r.reasons |= REASON_LARGE_SPEED_DROP;
            if (speedBefore >= 30.0f) r.reasons |= REASON_SPEED_BEFORE_EVENT;
        } else {
            r.reasons |= REASON_SPEED_UNAVAILABLE;
        }
        r.confidence = weighted / total;
        if (r.robustImpactG >= cfg.impactThresholdG)
            r.reasons |= (r.robustImpactG >= cfg.severeImpactG / 2) ? REASON_HIGH_DECELERATION : REASON_IMPACT;
        if (r.robustRotationRadS >= cfg.rotationThresholdRadS) r.reasons |= REASON_RAPID_ROTATION;
        if (r.impactCount >= 2) r.reasons |= REASON_REPEATED_IMPACTS;
    }
    r.severity = classify(r.confidence, cfg);
    return r;
}

void CrashDetector::go(CrashState s) {
    state_ = s;
    if (listener_) listener_(s, listenerCtx_);
}

void CrashDetector::features(const SensorSample& s, float& dyn, float& rot) {
    if (!gravityInit_) {
        gX_ = s.ax;
        gY_ = s.ay;
        gZ_ = s.az;
        gravityInit_ = true;
    }
    float dx = s.ax - gX_, dy = s.ay - gY_, dz = s.az - gZ_;
    dyn = std::sqrt(dx * dx + dy * dy + dz * dz) / GRAVITY;
    rot = std::sqrt(s.gx * s.gx + s.gy * s.gy + s.gz * s.gz);
}

bool CrashDetector::process(const SensorSample& s, bool speedValid, float speedKmh) {
    now_ = s.timestampMs;
    float dyn, rot;
    features(s, dyn, rot);

    if (state_ == CrashState::TRANSMIT_EVENT || state_ == CrashState::WAIT_FOR_ACK || state_ == CrashState::COOLDOWN) {
        // Same incident: ignore secondary impacts / vehicle settling.
        if (static_cast<int32_t>(now_ - cooldownUntil_) >= 0) {
            go(CrashState::NORMAL);
        } else {
            speedValid_ = speedValid;
            speed_ = speedKmh;
            return false;
        }
    }

    if (state_ == CrashState::VERIFYING) {
        speedValid_ = speedValid;
        speed_ = speedKmh;
        return tick(now_);
    }

    if (state_ == CrashState::NORMAL) {
        if (dyn >= cfg_.impactThresholdG || rot >= cfg_.rotationThresholdRadS) {
            triggerMs_ = s.timestampMs;
            speedBeforeValid_ = speedValid_ || speedValid;
            speedBefore_ = speedValid_ ? speed_ : speedKmh;
            n_ = 0;
            go(CrashState::SUSPECTED_IMPACT);
        } else {
            gX_ += cfg_.gravityAlpha * (s.ax - gX_);
            gY_ += cfg_.gravityAlpha * (s.ay - gY_);
            gZ_ += cfg_.gravityAlpha * (s.az - gZ_);
            speedValid_ = speedValid;
            speed_ = speedKmh;
            return false;
        }
    }

    // SUSPECTED_IMPACT: collect the analysis window.
    if (n_ < MAX_WINDOW_SAMPLES) {
        times_[n_] = s.timestampMs;
        dyn_[n_] = dyn;
        rot_[n_] = rot;
        ++n_;
    }
    speedValid_ = speedValid;
    speed_ = speedKmh;
    if (s.timestampMs - triggerMs_ < cfg_.analysisWindowMs) return false;
    return analyze();
}

bool CrashDetector::analyze() {
    go(CrashState::ANALYZING);
    bool speedOk = speedBeforeValid_ && speedValid_;
    last_ = analyzeWindow(times_.data(), dyn_.data(), rot_.data(), n_, speedOk, speedBefore_, speed_, triggerMs_, cfg_);
    hasResult_ = true;
    if (last_.severity == Severity::NORMAL || last_.severity == Severity::MINOR) {
        if (last_.severity == Severity::MINOR) ++minorEvents_;
        go(CrashState::FALSE_POSITIVE);
        go(CrashState::NORMAL);
        return false;
    }
    go(CrashState::PROBABLE_CRASH);
    if (last_.severity == Severity::CRITICAL && cfg_.criticalSkipsVerification) return raiseEmergency();
    verifyDeadline_ = now_ + cfg_.verificationWindowMs;
    go(CrashState::VERIFYING);
    return false;
}

bool CrashDetector::raiseEmergency() {
    go(CrashState::EMERGENCY_ACTIVE);
    cooldownUntil_ = now_ + cfg_.postEventCooldownMs;
    go(CrashState::TRANSMIT_EVENT);
    return true;
}

bool CrashDetector::tick(uint32_t nowMs) {
    if (static_cast<int32_t>(nowMs - now_) > 0) now_ = nowMs;
    if (state_ == CrashState::VERIFYING && static_cast<int32_t>(now_ - verifyDeadline_) >= 0) return raiseEmergency();
    if (state_ == CrashState::SUSPECTED_IMPACT && now_ - triggerMs_ >= cfg_.analysisWindowMs) return analyze();
    return false;
}

bool CrashDetector::cancel() {
    if (state_ != CrashState::VERIFYING) return false;
    go(CrashState::CANCELLED);
    go(CrashState::NORMAL);
    return true;
}

void CrashDetector::onTransmitStarted() {
    if (state_ == CrashState::TRANSMIT_EVENT) go(CrashState::WAIT_FOR_ACK);
}

void CrashDetector::onAck() {
    if (state_ == CrashState::WAIT_FOR_ACK) go(CrashState::COOLDOWN);
}

}  // namespace guardian
