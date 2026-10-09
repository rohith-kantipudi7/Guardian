#pragma once
// Mock sensors driven by ScenarioPlayer / fixed values. Used when hardware is absent.

#include <Arduino.h>

#include "interfaces.h"
#include "sim/scenario_player.h"

namespace guardian {

// Steady cruising vehicle. Crash scenarios are injected by GuardianDevice::simulate().
class MockImu : public ImuSensor {
public:
    bool begin() override { return player_.load("normal", esp_random()); }
    SensorSample read() override {
        SensorSample s;
        player_.next(millis(), s, speed_);
        return s;
    }
    float speedKmh() const { return speed_; }

private:
    ScenarioPlayer player_;
    float speed_ = 0;
};

class MockGnss : public GnssProvider {
public:
    bool begin() override { return true; }
    void update() override {}
    Location getLocation() override {
        Location l;
        l.valid = true;
        l.latitude = 12.9716;
        l.longitude = 77.5946;
        l.accuracyM = 5.0f;
        l.timestampMs = millis();
        return l;
    }
    bool hasFix() override { return true; }
    float getAccuracy() override { return 5.0f; }
};

class MockVehicleTelemetry : public VehicleTelemetryProvider {
public:
    explicit MockVehicleTelemetry(const MockImu* imu) : imu_(imu) {}
    bool begin() override { return true; }
    void update() override {}
    VehicleTelemetry read() override {
        VehicleTelemetry t;
        t.connected = available_;
        t.speedValid = available_ && imu_ != nullptr;
        t.speedKmh = imu_ ? imu_->speedKmh() : 0;
        return t;
    }
    // Demo: simulate a vehicle without OBD data (crash engine must still work).
    void setAvailable(bool available) { available_ = available; }

private:
    const MockImu* imu_;
    bool available_ = true;
};

class MockBattery : public BatteryProvider {
public:
    uint8_t percent() override { return 92; }
};

}  // namespace guardian
