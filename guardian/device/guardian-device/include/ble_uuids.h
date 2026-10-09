#pragma once
// Guardian BLE GATT layout. "4755 4152 4449 414e" is ASCII "GUARDIAN".
// Must match docs/ble-protocol.md and the Android app (GuardianBleUuids.kt).

namespace guardian::ble {

constexpr const char* DEVICE_NAME_PREFIX = "Guardian-";

constexpr const char* SERVICE_UUID = "47550000-4152-4449-414e-000000000000";
constexpr const char* DEVICE_STATUS_UUID = "47550001-4152-4449-414e-000000000000";
constexpr const char* BATTERY_UUID = "47550002-4152-4449-414e-000000000000";
constexpr const char* GPS_STATUS_UUID = "47550003-4152-4449-414e-000000000000";
constexpr const char* CELLULAR_STATUS_UUID = "47550004-4152-4449-414e-000000000000";
constexpr const char* VEHICLE_STATUS_UUID = "47550005-4152-4449-414e-000000000000";
constexpr const char* CRASH_STATUS_UUID = "47550006-4152-4449-414e-000000000000";
constexpr const char* EVENT_UUID = "47550007-4152-4449-414e-000000000000";
constexpr const char* COMMAND_UUID = "47550008-4152-4449-414e-000000000000";

}  // namespace guardian::ble
