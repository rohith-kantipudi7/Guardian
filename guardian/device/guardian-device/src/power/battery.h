#pragma once
// Backup battery level via ADC and a 1:1 divider (single Li-ion cell, 3.3-4.2 V).

#include <Arduino.h>

#include "interfaces.h"

namespace guardian {

class AdcBattery : public BatteryProvider {
public:
    explicit AdcBattery(int pin) : pin_(pin) {}

    uint8_t percent() override {
        float volts = analogReadMilliVolts(pin_) * 2.0f / 1000.0f;
        float pct = (volts - 3.3f) / (4.2f - 3.3f) * 100.0f;
        if (pct < 0) pct = 0;
        if (pct > 100) pct = 100;
        return static_cast<uint8_t>(pct);
    }

private:
    int pin_;
};

}  // namespace guardian
