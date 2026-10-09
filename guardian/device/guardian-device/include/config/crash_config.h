#pragma once
// ALL crash-detection thresholds live here. Keep identical to
// simulator/vehicle-simulator/crash_engine.py (CrashConfig) and docs/crash-detection.md.

#include <cstdint>

namespace guardian {

struct CrashConfig {
    float gravityAlpha = 0.01f;            // EMA factor for gravity estimate (NORMAL state only)
    float impactThresholdG = 2.5f;         // dynamic acceleration that opens an analysis window
    float severeImpactG = 8.0f;            // impact score saturates here
    float rotationThresholdRadS = 3.0f;    // rotation that opens an analysis window
    float severeRotationRadS = 6.0f;
    float speedDropThresholdKmh = 10.0f;
    float severeSpeedDropKmh = 60.0f;
    uint8_t minImpactSamples = 3;          // k-th largest sample = robust peak (rejects single spikes)
    uint32_t repeatedImpactGapMs = 150;
    uint32_t analysisWindowMs = 1000;
    uint32_t verificationWindowMs = 10000;
    bool criticalSkipsVerification = true;
    uint32_t postEventCooldownMs = 30000;  // suppress re-triggers from the same incident
    float weightImpact = 0.45f;
    float weightRotation = 0.20f;
    float weightSpeed = 0.25f;
    float weightRepeated = 0.10f;
    float minorConfidence = 0.25f;
    float moderateConfidence = 0.50f;
    float criticalConfidence = 0.85f;
};

}  // namespace guardian
