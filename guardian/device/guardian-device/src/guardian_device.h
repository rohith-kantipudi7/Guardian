#pragma once
// Orchestrates sensing, crash detection, local storage and both communication paths.
//
//   core 1 (loop):  IMU 100 Hz -> CrashDetector -> EventQueue          (never blocks on network)
//   core 0 (task):  EventQueue -> CellularProvider -> backend, heartbeats, time sync
//   BLE:            status/crash notifications to the phone, if connected (optional)

#include <freertos/FreeRTOS.h>
#include <freertos/semphr.h>
#include <freertos/task.h>

#include <atomic>

#include "crash/crash_detector.h"
#include "interfaces.h"
#include "sim/scenario_player.h"
#include "storage/event_queue.h"

namespace guardian {

class GuardianDevice {
public:
    GuardianDevice(ImuSensor& imu, GnssProvider& gnss, VehicleTelemetryProvider& vehicle, BatteryProvider& battery,
                   CellularProvider& cellular, BleProvider& ble, EventQueue& queue, AlertSignal& alert);

    void begin();
    void loop();

    // Demo / test controls
    bool simulate(const char* scenario);
    bool cancelVerification();
    void setCellularOutage(bool outage);
    void printStatus();

private:
    static void networkTaskEntry(void* self);
    void networkTask();
    static SendResult sendThunk(const CrashEvent& e, void* ctx);
    static void deliveredThunk(const CrashEvent& e, void* ctx);
    static void stateThunk(CrashState s, void* ctx);

    void sample(uint32_t now);
    void raiseEmergency();
    void handleCommands();
    void replayHistory();
    DeviceStatus snapshot();
    void maybeSyncClockFromGnss();

    ImuSensor& imu_;
    GnssProvider& gnss_;
    VehicleTelemetryProvider& vehicle_;
    BatteryProvider& battery_;
    CellularProvider& cellular_;
    BleProvider& ble_;
    EventQueue& queue_;
    AlertSignal& alert_;
    CrashDetector detector_;
    ScenarioPlayer sim_;
    bool simActive_ = false;

    SemaphoreHandle_t queueMutex_ = nullptr;
    SemaphoreHandle_t statusMutex_ = nullptr;
    DeviceStatus cachedStatus_;  // built on core 1, read by the network task
    TaskHandle_t netTask_ = nullptr;
    std::atomic<bool> ackReceived_{false};
    std::atomic<bool> stateChanged_{false};
    std::atomic<uint8_t> pendingEvents_{0};
    std::atomic<uint8_t> cellularStatus_{static_cast<uint8_t>(CellularStatus::UNAVAILABLE)};

    uint32_t lastSampleMs_ = 0;
    uint32_t lastBleMs_ = 0;
    uint32_t lastTick_ = 0;
    uint32_t eventCounter_ = 0;
    bool clockSet_ = false;
};

}  // namespace guardian
