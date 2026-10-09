#pragma once
// Plain data types shared by every Guardian firmware module. No Arduino dependencies.

#include <cstddef>
#include <cstdint>

namespace guardian {

// Accelerations in m/s^2, angular rates in rad/s (body frame).
struct SensorSample {
    uint32_t timestampMs = 0;
    float ax = 0, ay = 0, az = 0;
    float gx = 0, gy = 0, gz = 0;
};

struct Location {
    bool valid = false;
    double latitude = 0;
    double longitude = 0;
    float accuracyM = 0;
    uint32_t timestampMs = 0;
};

// OBD-II data is optional: every field carries its own validity flag.
struct VehicleTelemetry {
    bool connected = false;
    bool speedValid = false;
    float speedKmh = 0;
    bool rpmValid = false;
    float rpm = 0;
};

enum class Severity : uint8_t { NORMAL, MINOR, MODERATE, CRITICAL };

enum class CrashState : uint8_t {
    NORMAL,
    SUSPECTED_IMPACT,
    ANALYZING,
    FALSE_POSITIVE,
    PROBABLE_CRASH,
    VERIFYING,
    CANCELLED,
    EMERGENCY_ACTIVE,
    TRANSMIT_EVENT,
    WAIT_FOR_ACK,
    COOLDOWN,
};

enum class GpsStatus : uint8_t { AVAILABLE, NO_FIX, UNAVAILABLE };
enum class CellularStatus : uint8_t { CONNECTED, CONNECTING, DISCONNECTED, UNAVAILABLE };

// Reason flags reported with each crash result.
enum CrashReason : uint16_t {
    REASON_NONE = 0,
    REASON_IMPACT = 1 << 0,
    REASON_HIGH_DECELERATION = 1 << 1,
    REASON_RAPID_ROTATION = 1 << 2,
    REASON_LARGE_SPEED_DROP = 1 << 3,
    REASON_REPEATED_IMPACTS = 1 << 4,
    REASON_SPEED_BEFORE_EVENT = 1 << 5,
    REASON_SPEED_UNAVAILABLE = 1 << 6,
    REASON_SINGLE_SPIKE = 1 << 7,
};

struct CrashResult {
    uint32_t triggerMs = 0;
    float accelerationPeakG = 0;
    float rotationPeakRadS = 0;
    float robustImpactG = 0;
    float robustRotationRadS = 0;
    uint8_t impactCount = 0;
    bool speedValid = false;
    float speedBeforeKmh = 0;
    float speedAfterKmh = 0;
    float confidence = 0;
    Severity severity = Severity::NORMAL;
    uint16_t reasons = REASON_NONE;
};

// Fixed-size POD so it can be persisted to flash byte-for-byte.
struct CrashEvent {
    char eventId[48] = {0};
    char deviceId[24] = {0};
    char vehicleId[24] = {0};
    char timestamp[32] = {0};  // ISO-8601 UTC, e.g. 2026-10-05T10:30:00Z
    bool locationValid = false;
    double latitude = 0;
    double longitude = 0;
    bool speedValid = false;
    float speedKmh = 0;
    float accelerationPeakG = 0;
    float rotationPeakRadS = 0;
    float confidence = 0;
    Severity severity = Severity::NORMAL;
    bool phoneConnected = false;
    bool cellularConnected = false;
};

struct DeviceStatus {
    const char* deviceId = "";
    const char* vehicleId = "";
    const char* firmwareVersion = "";
    uint8_t batteryPercent = 0;
    GpsStatus gps = GpsStatus::UNAVAILABLE;
    CellularStatus cellular = CellularStatus::UNAVAILABLE;
    bool phoneConnected = false;
    bool vehicleConnected = false;
    CrashState crashState = CrashState::NORMAL;
    uint8_t pendingEvents = 0;
    Location location;
    VehicleTelemetry vehicle;
};

// Outcome of a backend request, from the device's point of view.
enum class SendResult : uint8_t {
    ACKED,      // 201 created, or 200 duplicate (server already has it)
    RETRY,      // connectivity problem or 5xx/429
    REJECTED,   // permanent 4xx (validation / auth)
};

const char* toString(Severity s);
const char* toString(CrashState s);
const char* toString(GpsStatus s);
const char* toString(CellularStatus s);
// Writes comma-separated reason names into buf.
void reasonsToString(uint16_t reasons, char* buf, size_t len);

}  // namespace guardian
