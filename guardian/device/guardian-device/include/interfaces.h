#pragma once
// Hardware abstraction interfaces. Every interface has a mock and (where hardware exists)
// a real implementation, selected at build time in main.cpp.

#include <string>

#include "guardian_types.h"

namespace guardian {

class ImuSensor {
public:
    virtual ~ImuSensor() = default;
    virtual bool begin() = 0;
    virtual SensorSample read() = 0;
};

class GnssProvider {
public:
    virtual ~GnssProvider() = default;
    virtual bool begin() = 0;
    virtual void update() = 0;  // non-blocking; call every loop
    virtual Location getLocation() = 0;
    virtual bool hasFix() = 0;
    virtual float getAccuracy() = 0;
    // UTC time from satellites, if available (used to set the system clock).
    virtual bool getUtcEpoch(uint32_t& epoch) { (void)epoch; return false; }
};

class VehicleTelemetryProvider {
public:
    virtual ~VehicleTelemetryProvider() = default;
    virtual bool begin() = 0;
    virtual void update() = 0;  // non-blocking; call every loop
    virtual VehicleTelemetry read() = 0;
};

class BatteryProvider {
public:
    virtual ~BatteryProvider() = default;
    virtual uint8_t percent() = 0;
};

// Local audible alarm (active buzzer). Backup alert that draws bystanders to the vehicle, and
// the device's own way to sound off when the phone has handed over its black box and gone dark.
class AlertSignal {
public:
    virtual ~AlertSignal() = default;
    virtual void begin() = 0;
    // Drives the non-blocking beep pattern from the live crash state; call every loop.
    virtual void update(CrashState state, uint32_t nowMs) = 0;
    // The phone streamed its black box and may be destroyed: latch the full alarm locally.
    virtual void triggerFromPhone(uint32_t nowMs) = 0;
};

// The independent emergency path. Must never depend on the phone.
class CellularProvider {
public:
    virtual ~CellularProvider() = default;
    virtual bool connect() = 0;
    virtual void disconnect() = 0;
    virtual bool isConnected() = 0;
    virtual CellularStatus status() = 0;
    // Sets the system clock from the network (NTP / modem network time).
    virtual bool syncTime() = 0;
    virtual bool registerDevice() = 0;
    virtual SendResult sendHeartbeat(const DeviceStatus& status) = 0;
    virtual SendResult sendCrashEvent(const CrashEvent& event) = 0;
    // Demo control: pretend the network is down without touching hardware.
    virtual void setSimulatedOutage(bool outage) = 0;
    virtual bool simulatedOutage() const = 0;
};

enum class BleCommandType : uint8_t {
    NONE,
    PING,
    CANCEL,        // occupant pressed I'M SAFE
    GET_HISTORY,   // phone reconnected: replay recent events
    SIMULATE,      // demo only: arg = scenario name
    CELLULAR_OFF,  // demo only
    CELLULAR_ON,   // demo only
};

struct BleCommand {
    BleCommandType type = BleCommandType::NONE;
    char arg[24] = {0};
};

// Companion link to the phone. Optional: the device works identically without it.
class BleProvider {
public:
    virtual ~BleProvider() = default;
    virtual void start() = 0;
    virtual bool isPhoneConnected() = 0;
    virtual void notify(const DeviceStatus& status) = 0;
    virtual void notifyCrashState(CrashState state, const CrashResult* result, uint32_t verifyRemainingMs) = 0;
    virtual void notifyEvent(const CrashEvent& event, bool delivered) = 0;
    virtual bool pollCommand(BleCommand& out) = 0;
    // Returns true once when the phone has finished streaming a black box bundle (chunked).
    virtual bool consumeBlackBox(std::string& out) {
        (void)out;
        return false;
    }
};

}  // namespace guardian
