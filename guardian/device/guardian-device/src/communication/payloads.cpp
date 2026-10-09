#include "communication/payloads.h"

#include <ArduinoJson.h>
#include <time.h>

namespace guardian::payloads {

namespace {
constexpr time_t MIN_VALID_EPOCH = 1704067200;  // 2024-01-01

String dump(const JsonDocument& doc) {
    String out;
    serializeJson(doc, out);
    return out;
}
}  // namespace

bool clockValid() { return time(nullptr) > MIN_VALID_EPOCH; }

void isoTimestamp(char* buf, size_t len) {
    time_t now = clockValid() ? time(nullptr) : static_cast<time_t>(millis() / 1000);
    struct tm t;
    gmtime_r(&now, &t);
    strftime(buf, len, "%Y-%m-%dT%H:%M:%SZ", &t);
}

String registerJson(const char* deviceId, const char* secret, const char* firmware, const char* vehicleId) {
    JsonDocument d;
    d["deviceId"] = deviceId;
    d["deviceSecret"] = secret;
    d["firmwareVersion"] = firmware;
    d["vehicleId"] = vehicleId;
    return dump(d);
}

String heartbeatJson(const DeviceStatus& s, const char* timestamp) {
    JsonDocument d;
    d["deviceId"] = s.deviceId;
    d["timestamp"] = timestamp;
    d["batteryLevel"] = s.batteryPercent;
    d["gpsStatus"] = toString(s.gps);
    d["cellularStatus"] = toString(s.cellular);
    d["bleStatus"] = s.phoneConnected ? "CONNECTED" : "ADVERTISING";
    d["phoneConnected"] = s.phoneConnected;
    d["vehicleConnected"] = s.vehicleConnected;
    d["firmwareVersion"] = s.firmwareVersion;
    if (s.location.valid) {
        d["latitude"] = s.location.latitude;
        d["longitude"] = s.location.longitude;
    }
    return dump(d);
}

String crashJson(const CrashEvent& e) {
    JsonDocument d;
    d["eventId"] = e.eventId;
    d["deviceId"] = e.deviceId;
    d["vehicleId"] = e.vehicleId;
    d["timestamp"] = e.timestamp;
    if (e.locationValid) {
        d["latitude"] = e.latitude;
        d["longitude"] = e.longitude;
    }
    if (e.speedValid) d["speed"] = e.speedKmh;
    d["accelerationPeak"] = e.accelerationPeakG;
    d["rotationPeak"] = e.rotationPeakRadS;
    d["confidence"] = roundf(e.confidence * 100) / 100;
    d["severity"] = toString(e.severity);
    d["phoneConnected"] = e.phoneConnected;
    d["cellularConnected"] = e.cellularConnected;
    return dump(d);
}

String bleDeviceStatus(const DeviceStatus& s) {
    JsonDocument d;
    d["deviceId"] = s.deviceId;
    d["vehicleId"] = s.vehicleId;
    d["firmware"] = s.firmwareVersion;
    d["battery"] = s.batteryPercent;
    d["gps"] = toString(s.gps);
    d["cellular"] = toString(s.cellular);
    d["vehicleConnected"] = s.vehicleConnected;
    d["crashState"] = toString(s.crashState);
    d["pendingEvents"] = s.pendingEvents;
    return dump(d);
}

String bleBattery(const DeviceStatus& s) {
    JsonDocument d;
    d["battery"] = s.batteryPercent;
    return dump(d);
}

String bleGps(const DeviceStatus& s) {
    JsonDocument d;
    d["status"] = toString(s.gps);
    if (s.location.valid) {
        d["lat"] = s.location.latitude;
        d["lon"] = s.location.longitude;
        d["accuracy"] = s.location.accuracyM;
    }
    return dump(d);
}

String bleCellular(const DeviceStatus& s) {
    JsonDocument d;
    d["status"] = toString(s.cellular);
    d["pendingEvents"] = s.pendingEvents;
    return dump(d);
}

String bleVehicle(const DeviceStatus& s) {
    JsonDocument d;
    d["connected"] = s.vehicle.connected;
    if (s.vehicle.speedValid) d["speed"] = s.vehicle.speedKmh;
    else d["speed"] = nullptr;
    if (s.vehicle.rpmValid) d["rpm"] = s.vehicle.rpm;
    else d["rpm"] = nullptr;
    return dump(d);
}

String bleCrashStatus(CrashState state, const CrashResult* r, uint32_t verifyRemainingMs) {
    JsonDocument d;
    d["state"] = toString(state);
    if (r) {
        d["severity"] = toString(r->severity);
        d["confidence"] = roundf(r->confidence * 100) / 100;
        char reasons[160];
        reasonsToString(r->reasons, reasons, sizeof(reasons));
        d["reasons"] = reasons;
    }
    if (state == CrashState::VERIFYING) d["verifyRemainingMs"] = verifyRemainingMs;
    return dump(d);
}

String bleEvent(const CrashEvent& e, bool delivered) {
    JsonDocument d;
    d["eventId"] = e.eventId;
    d["timestamp"] = e.timestamp;
    d["severity"] = toString(e.severity);
    d["confidence"] = roundf(e.confidence * 100) / 100;
    if (e.locationValid) {
        d["lat"] = e.latitude;
        d["lon"] = e.longitude;
    }
    d["phoneConnected"] = e.phoneConnected;
    d["delivered"] = delivered;
    return dump(d);
}

}  // namespace guardian::payloads
