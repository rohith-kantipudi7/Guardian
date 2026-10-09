#pragma once
// Local audible alarm for the Guardian device. GpioBuzzer drives a real active buzzer; the mock
// logs the alarm state so builds without hardware (and the simulator) behave identically.

#include <Arduino.h>

#include "interfaces.h"

namespace guardian {

namespace detail {

// Beep cadence for each urgency level (ms on / ms off). 0,0 = silent.
struct BeepPattern {
    uint32_t onMs;
    uint32_t offMs;
};

// How long the device keeps sounding after the phone hands off its black box (phone may be dead).
constexpr uint32_t PHONE_ALARM_MS = 60000;

inline BeepPattern patternFor(CrashState state, uint32_t now, uint32_t phoneAlarmUntil) {
    const bool phoneAlarm = now < phoneAlarmUntil;
    switch (state) {
        case CrashState::EMERGENCY_ACTIVE:
        case CrashState::TRANSMIT_EVENT:
        case CrashState::WAIT_FOR_ACK:
            return {400, 150};  // urgent, near-continuous
        case CrashState::VERIFYING:
            return phoneAlarm ? BeepPattern{400, 150} : BeepPattern{120, 880};  // slow warning beep
        default:
            return phoneAlarm ? BeepPattern{400, 150} : BeepPattern{0, 0};
    }
}

}  // namespace detail

// Real active buzzer on a GPIO. Non-blocking: toggled from update() so 100 Hz sensing never stalls.
class GpioBuzzer : public AlertSignal {
public:
    explicit GpioBuzzer(int pin) : pin_(pin) {}

    void begin() override {
        pinMode(pin_, OUTPUT);
        digitalWrite(pin_, LOW);
    }

    void triggerFromPhone(uint32_t nowMs) override {
        phoneAlarmUntil_ = nowMs + detail::PHONE_ALARM_MS;
    }

    void update(CrashState state, uint32_t nowMs) override {
        detail::BeepPattern p = detail::patternFor(state, nowMs, phoneAlarmUntil_);
        if (p.onMs == 0 && p.offMs == 0) {
            set(false);
            return;
        }
        const uint32_t period = on_ ? p.onMs : p.offMs;
        if (nowMs - lastToggleMs_ >= period) {
            lastToggleMs_ = nowMs;
            set(!on_);
        }
    }

private:
    void set(bool on) {
        if (on == on_) return;
        on_ = on;
        digitalWrite(pin_, on ? HIGH : LOW);
    }

    int pin_;
    bool on_ = false;
    uint32_t lastToggleMs_ = 0;
    uint32_t phoneAlarmUntil_ = 0;
};

// No hardware: report alarm transitions on the serial console so demos still show the behaviour.
class MockAlertSignal : public AlertSignal {
public:
    void begin() override {}

    void triggerFromPhone(uint32_t nowMs) override {
        phoneAlarmUntil_ = nowMs + detail::PHONE_ALARM_MS;
        Serial.println("[buzzer] phone black box received - LOCAL ALARM ON (mock)");
    }

    void update(CrashState state, uint32_t nowMs) override {
        detail::BeepPattern p = detail::patternFor(state, nowMs, phoneAlarmUntil_);
        const bool sounding = !(p.onMs == 0 && p.offMs == 0);
        if (sounding != sounding_) {
            sounding_ = sounding;
            Serial.printf("[buzzer] %s (mock)\n", sounding ? "ALARM ON" : "alarm off");
        }
    }

private:
    bool sounding_ = false;
    uint32_t phoneAlarmUntil_ = 0;
};

}  // namespace guardian
