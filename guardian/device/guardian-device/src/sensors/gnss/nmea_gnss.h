#pragma once
// NMEA GNSS module (u-blox NEO-6M/M8N, ATGM336H, ...) on a hardware UART, parsed by TinyGPSPlus.

#include <Arduino.h>
#include <TinyGPSPlus.h>
#include <time.h>

#include "interfaces.h"

namespace guardian {

class NmeaGnss : public GnssProvider {
public:
    NmeaGnss(HardwareSerial& serial, int rx, int tx, uint32_t baud) : serial_(serial), rx_(rx), tx_(tx), baud_(baud) {}

    bool begin() override {
        serial_.begin(baud_, SERIAL_8N1, rx_, tx_);
        return true;
    }

    void update() override {
        while (serial_.available()) gps_.encode(static_cast<char>(serial_.read()));
    }

    bool hasFix() override { return gps_.location.isValid() && gps_.location.age() < FIX_MAX_AGE_MS; }

    // Rough horizontal accuracy: HDOP x typical UERE (~5 m).
    float getAccuracy() override { return gps_.hdop.isValid() ? gps_.hdop.hdop() * 5.0f : 99.0f; }

    Location getLocation() override {
        Location l;
        l.valid = hasFix();
        if (l.valid) {
            l.latitude = gps_.location.lat();
            l.longitude = gps_.location.lng();
            l.accuracyM = getAccuracy();
            l.timestampMs = millis() - gps_.location.age();
        }
        return l;
    }

    bool getUtcEpoch(uint32_t& epoch) override {
        if (!gps_.date.isValid() || !gps_.time.isValid() || gps_.date.year() < 2024) return false;
        struct tm t = {};
        t.tm_year = gps_.date.year() - 1900;
        t.tm_mon = gps_.date.month() - 1;
        t.tm_mday = gps_.date.day();
        t.tm_hour = gps_.time.hour();
        t.tm_min = gps_.time.minute();
        t.tm_sec = gps_.time.second();
        // ESP32 newlib: TZ defaults to UTC, so mktime() behaves like timegm().
        epoch = static_cast<uint32_t>(mktime(&t));
        return true;
    }

private:
    static constexpr uint32_t FIX_MAX_AGE_MS = 5000;
    HardwareSerial& serial_;
    int rx_, tx_;
    uint32_t baud_;
    TinyGPSPlus gps_;
};

}  // namespace guardian
