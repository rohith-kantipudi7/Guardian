#pragma once
// JSON payloads for the backend (docs/device-protocol.md) and BLE (docs/ble-protocol.md).

#include <Arduino.h>

#include "guardian_types.h"

namespace guardian::payloads {

String registerJson(const char* deviceId, const char* secret, const char* firmware, const char* vehicleId);
String heartbeatJson(const DeviceStatus& s, const char* timestamp);
String crashJson(const CrashEvent& e);

// BLE characteristic values
String bleDeviceStatus(const DeviceStatus& s);
String bleBattery(const DeviceStatus& s);
String bleGps(const DeviceStatus& s);
String bleCellular(const DeviceStatus& s);
String bleVehicle(const DeviceStatus& s);
String bleCrashStatus(CrashState state, const CrashResult* r, uint32_t verifyRemainingMs);
String bleEvent(const CrashEvent& e, bool delivered);

// Current UTC time as ISO-8601 (falls back to 1970-based uptime if the clock is unset).
void isoTimestamp(char* buf, size_t len);
bool clockValid();

}  // namespace guardian::payloads
