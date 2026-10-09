#pragma once
// MPU6050 / MPU6500 over I2C. +-16 g, +-2000 deg/s, DLPF 44 Hz, 100 Hz.
// Mount with +x pointing to the vehicle front and +z up (see docs/hardware.md).

#include <Arduino.h>
#include <Wire.h>

#include "interfaces.h"

namespace guardian {

class Mpu6050Imu : public ImuSensor {
public:
    Mpu6050Imu(int sda, int scl, uint8_t addr = 0x68) : sda_(sda), scl_(scl), addr_(addr) {}

    bool begin() override {
        Wire.begin(sda_, scl_, 400000);
        if (!write(0x6B, 0x00)) return false;  // PWR_MGMT_1: wake
        write(0x19, 9);                        // SMPLRT_DIV: 1 kHz / (1 + 9) = 100 Hz
        write(0x1A, 0x03);                     // CONFIG: DLPF 44 Hz
        write(0x1B, 0x18);                     // GYRO_CONFIG: +-2000 deg/s
        write(0x1C, 0x18);                     // ACCEL_CONFIG: +-16 g
        ok_ = true;
        return true;
    }

    SensorSample read() override {
        SensorSample s;
        s.timestampMs = millis();
        if (!ok_) return s;
        Wire.beginTransmission(addr_);
        Wire.write(0x3B);
        if (Wire.endTransmission(false) != 0) return s;
        if (Wire.requestFrom(static_cast<int>(addr_), 14) != 14) return s;
        int16_t raw[7];
        for (auto& r : raw) r = static_cast<int16_t>((Wire.read() << 8) | Wire.read());
        constexpr float ACCEL_SCALE = 9.80665f / 2048.0f;           // LSB/g at +-16 g
        constexpr float GYRO_SCALE = (3.14159265f / 180.0f) / 16.4f;  // LSB/(deg/s) at +-2000
        s.ax = raw[0] * ACCEL_SCALE;
        s.ay = raw[1] * ACCEL_SCALE;
        s.az = raw[2] * ACCEL_SCALE;
        // raw[3] is temperature
        s.gx = raw[4] * GYRO_SCALE;
        s.gy = raw[5] * GYRO_SCALE;
        s.gz = raw[6] * GYRO_SCALE;
        return s;
    }

private:
    bool write(uint8_t reg, uint8_t val) {
        Wire.beginTransmission(addr_);
        Wire.write(reg);
        Wire.write(val);
        return Wire.endTransmission() == 0;
    }

    int sda_, scl_;
    uint8_t addr_;
    bool ok_ = false;
};

}  // namespace guardian
