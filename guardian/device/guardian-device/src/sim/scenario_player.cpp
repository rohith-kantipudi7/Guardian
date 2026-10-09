#include "sim/scenario_player.h"

#include <cmath>
#include <cstring>

#include "crash/crash_detector.h"

namespace guardian {

namespace {
constexpr float PI_F = 3.14159265f;
const char* const NAMES[] = {"normal", "hard_brake", "pothole", "minor_impact", "severe_crash", "rollover", "idle"};
}  // namespace

const char* const* ScenarioPlayer::names(size_t& count) {
    count = sizeof(NAMES) / sizeof(NAMES[0]);
    return NAMES;
}

void ScenarioPlayer::add(const Segment& s) {
    if (segCount_ < MAX_SEGMENTS) segs_[segCount_++] = s;
}

float ScenarioPlayer::uniform() {
    // xorshift32
    rng_ ^= rng_ << 13;
    rng_ ^= rng_ >> 17;
    rng_ ^= rng_ << 5;
    return (static_cast<float>(rng_ & 0xFFFFFF) + 0.5f) / 16777216.0f;
}

float ScenarioPlayer::gauss(float sigma) {
    float u1 = uniform(), u2 = uniform();
    return sigma * std::sqrt(-2.0f * std::log(u1)) * std::cos(2 * PI_F * u2);
}

bool ScenarioPlayer::load(const char* name, uint32_t seed) {
    segCount_ = seg_ = 0;
    segElapsed_ = 0;
    rng_ = seed ? seed * 2654435761u : 1;
    const float G = GRAVITY;
    auto cruise = [&](uint32_t ms, float yaw = 0) { add({Kind::CRUISE, ms, 0, {0, 0, 0}, {0, 0, yaw}, 0.3f, 0}); };
    auto constant = [&](uint32_t ms, float ax, float ay, float az, float gx, float gy, float gz, float noise) {
        add({Kind::CONST, ms, 0, {ax, ay, az}, {gx, gy, gz}, noise, 0});
    };
    auto until = [&](float target, float ax, float gx = 0, float gy = 0) {
        add({Kind::UNTIL_SPEED, 0, target, {ax, 0, 0}, {gx, gy, 0}, 0.3f, 0});
    };
    auto pulse = [&](uint32_t ms, float peakG, uint8_t axis, float gx, float gy, float gz) {
        add({Kind::PULSE, ms, 0, {peakG, 0, 0}, {gx, gy, gz}, 0.5f, axis});
    };

    if (std::strcmp(name, "normal") == 0) {
        speed_ = 50;
        cruise(4000);
        cruise(1500, 0.3f);
        cruise(4500);
    } else if (std::strcmp(name, "hard_brake") == 0) {
        speed_ = 60;
        cruise(2000);
        until(0, -0.9f * G, 0, 0.05f);
        cruise(1500);
    } else if (std::strcmp(name, "pothole") == 0) {
        speed_ = 40;
        cruise(2000);
        constant(DT_MS, 0, 0, 3.5f * G, 0, 0, 0, 0.5f);
        constant(DT_MS, 0, 0, 1.5f * G, 0, 0, 0, 0.5f);
        cruise(2500);
    } else if (std::strcmp(name, "minor_impact") == 0) {
        speed_ = 20;
        cruise(2000);
        pulse(80, 5.0f, 0, 0, 0, 0.5f);
        until(8, -0.4f * G);
        cruise(1500);
    } else if (std::strcmp(name, "severe_crash") == 0) {
        speed_ = 72;
        cruise(2000);
        pulse(120, 10.0f, 0, 0.5f, 0.5f, 2.0f);
        constant(300, -2.0f * G, 0, 0, 0, 0, 5.0f, 0.8f);
        pulse(60, 4.0f, 1, 0, 0, 3.0f);
        until(0, -1.5f * G);
        cruise(2000);
    } else if (std::strcmp(name, "rollover") == 0) {
        speed_ = 50;
        cruise(2000);
        for (int i = 0; i < 3; ++i) {
            pulse(50, 3.5f, 2, 7.0f, 0, 0);
            constant(150, -1.2f * G, 0, 0, 7.0f, 0, 0, 0.6f);
        }
        until(0, -1.0f * G);
        cruise(2000);
    } else if (std::strcmp(name, "idle") == 0) {
        speed_ = 0;
    } else {
        return false;
    }
    name_ = name;
    for (const char* n : NAMES)
        if (std::strcmp(n, name) == 0) name_ = n;  // keep a static pointer
    return true;
}

void ScenarioPlayer::next(uint32_t timestampMs, SensorSample& out, float& speedKmh) {
    float ax = 0, ay = 0, az = 0, gx = 0, gy = 0, gz = 0, noise = 0.3f;

    while (seg_ < segCount_) {
        const Segment& s = segs_[seg_];
        bool done = (s.kind == Kind::UNTIL_SPEED) ? (speed_ <= s.target) : (segElapsed_ >= s.ms);
        if (!done) break;
        ++seg_;
        segElapsed_ = 0;
    }

    if (seg_ < segCount_) {
        const Segment& s = segs_[seg_];
        noise = s.noise;
        gx = s.g[0];
        gy = s.g[1];
        gz = s.g[2];
        switch (s.kind) {
            case Kind::CRUISE: ax = gauss(0.2f); break;
            case Kind::CONST:
            case Kind::UNTIL_SPEED:
                ax = s.a[0];
                ay = s.a[1];
                az = s.a[2];
                break;
            case Kind::PULSE: {
                uint32_t n = s.ms / DT_MS;
                uint32_t i = segElapsed_ / DT_MS;
                float a = -s.a[0] * GRAVITY * std::sin(PI_F * (static_cast<float>(i) + 0.5f) / static_cast<float>(n));
                (s.axis == 0 ? ax : s.axis == 1 ? ay : az) = a;
                break;
            }
        }
        segElapsed_ += DT_MS;
    } else {
        ax = speed_ > 0 ? gauss(0.2f) : 0;
        noise = speed_ > 0 ? 0.3f : 0.05f;
    }

    speed_ = std::fmax(0.0f, speed_ + ax * (DT_MS / 1000.0f) * 3.6f);
    out.timestampMs = timestampMs;
    out.ax = ax + gauss(noise);
    out.ay = ay + gauss(noise);
    out.az = GRAVITY + az + gauss(noise);
    out.gx = gx + gauss(0.03f);
    out.gy = gy + gauss(0.03f);
    out.gz = gz + gauss(0.03f);
    speedKmh = speed_;
}

}  // namespace guardian
