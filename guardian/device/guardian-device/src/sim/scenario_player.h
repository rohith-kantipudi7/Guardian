#pragma once
// Synthetic 100 Hz sensor scenarios (pure C++). Port of simulator/vehicle-simulator/scenarios.py.
// Used by MockImu / MockVehicleTelemetry on the device and by native unit tests.

#include <cstddef>
#include <cstdint>

#include "guardian_types.h"

namespace guardian {

class ScenarioPlayer {
public:
    static constexpr uint32_t DT_MS = 10;

    // Known names: normal, hard_brake, pothole, minor_impact, severe_crash, rollover, idle
    bool load(const char* name, uint32_t seed = 1);
    // Produces the next sample. After the scenario ends the vehicle keeps cruising steadily.
    void next(uint32_t timestampMs, SensorSample& out, float& speedKmh);
    bool finished() const { return seg_ >= segCount_; }
    float speed() const { return speed_; }
    const char* name() const { return name_; }

    static const char* const* names(size_t& count);

private:
    enum class Kind : uint8_t { CRUISE, CONST, PULSE, UNTIL_SPEED };
    struct Segment {
        Kind kind;
        uint32_t ms;       // duration (CRUISE / CONST / PULSE)
        float target;      // UNTIL_SPEED target km/h
        float a[3];        // body accel offset (m/s^2), or PULSE peak in g on `axis`
        float g[3];        // gyro rad/s
        float noise;
        uint8_t axis;      // PULSE axis 0=x 1=y 2=z
    };

    void add(const Segment& s);
    float gauss(float sigma);
    float uniform();

    static constexpr size_t MAX_SEGMENTS = 16;
    Segment segs_[MAX_SEGMENTS]{};
    size_t segCount_ = 0;
    size_t seg_ = 0;
    uint32_t segElapsed_ = 0;
    float speed_ = 0;
    uint32_t rng_ = 1;
    const char* name_ = "idle";
};

}  // namespace guardian
