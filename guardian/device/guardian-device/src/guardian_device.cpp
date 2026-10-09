#include "guardian_device.h"

#include <Arduino.h>
#include <ArduinoJson.h>
#include <LittleFS.h>
#include <sys/time.h>

#include <cstdio>
#include <cstring>

#include "communication/payloads.h"
#include "config/device_config.h"

namespace guardian {

namespace {
constexpr const char* BLACKBOX_PATH = "/blackbox.json";

Severity parseSeverity(const char* s) {
    if (std::strcmp(s, "CRITICAL") == 0) return Severity::CRITICAL;
    if (std::strcmp(s, "MODERATE") == 0) return Severity::MODERATE;
    if (std::strcmp(s, "MINOR") == 0) return Severity::MINOR;
    return Severity::NORMAL;
}
}  // namespace

GuardianDevice::GuardianDevice(ImuSensor& imu, GnssProvider& gnss, VehicleTelemetryProvider& vehicle,
                               BatteryProvider& battery, CellularProvider& cellular, BleProvider& ble,
                               EventQueue& queue, AlertSignal& alert)
    : imu_(imu), gnss_(gnss), vehicle_(vehicle), battery_(battery), cellular_(cellular), ble_(ble), queue_(queue),
      alert_(alert) {}

void GuardianDevice::begin() {
    queueMutex_ = xSemaphoreCreateMutex();
    statusMutex_ = xSemaphoreCreateMutex();
    if (!imu_.begin()) Serial.println("[imu] init FAILED - check wiring");
    gnss_.begin();
    vehicle_.begin();
    queue_.begin();
    pendingEvents_ = static_cast<uint8_t>(queue_.pendingCount());
    detector_.setListener(stateThunk, this);
    cachedStatus_ = snapshot();
    ble_.start();
    alert_.begin();
    // Network work runs on core 0 so HTTP/modem latency never stalls 100 Hz sensing on core 1.
    xTaskCreatePinnedToCore(networkTaskEntry, "guardian-net", 12288, this, 1, &netTask_, 0);
    Serial.printf("[guardian] %s firmware %s ready (%u queued events)\n", GUARDIAN_DEVICE_ID,
                  config::FIRMWARE_VERSION, pendingEvents_.load());
}

void GuardianDevice::loop() {
    uint32_t now = millis();
    gnss_.update();
    vehicle_.update();
    maybeSyncClockFromGnss();

    if (now - lastSampleMs_ >= config::SAMPLE_PERIOD_MS) {
        lastSampleMs_ = now;
        sample(now);
    }
    if (now - lastTick_ >= 100) {
        lastTick_ = now;
        if (detector_.tick(now)) raiseEmergency();
    }
    if (ackReceived_.exchange(false)) detector_.onAck();
    if (stateChanged_.exchange(false)) {
        uint32_t remaining = 0;
        if (detector_.state() == CrashState::VERIFYING && detector_.verifyDeadlineMs() > now)
            remaining = detector_.verifyDeadlineMs() - now;
        ble_.notifyCrashState(detector_.state(), detector_.hasResult() ? &detector_.lastResult() : nullptr, remaining);
    }
    if (now - lastBleMs_ >= config::BLE_STATUS_PERIOD_MS) {
        lastBleMs_ = now;
        DeviceStatus s = snapshot();
        xSemaphoreTake(statusMutex_, portMAX_DELAY);
        cachedStatus_ = s;
        xSemaphoreGive(statusMutex_);
        ble_.notify(s);
    }
    // Backup alarm: the phone handed off its black box (it may be destroyed) -> sound off locally.
    std::string blackbox;
    if (ble_.consumeBlackBox(blackbox)) {
        alert_.triggerFromPhone(now);
        ingestBlackBox(blackbox);
    }
    alert_.update(detector_.state(), now);
    handleCommands();
}

void GuardianDevice::sample(uint32_t now) {
    SensorSample s;
    bool speedValid;
    float speed;
    if (simActive_) {
        sim_.next(now, s, speed);
        speedValid = true;
        if (sim_.finished()) {
            simActive_ = false;
            Serial.printf("[sim] scenario '%s' finished\n", sim_.name());
        }
    } else {
        s = imu_.read();
        s.timestampMs = now;
        VehicleTelemetry v = vehicle_.read();
        speedValid = v.speedValid;
        speed = v.speedKmh;
    }
    if (detector_.process(s, speedValid, speed)) raiseEmergency();
}

void GuardianDevice::raiseEmergency() {
    const CrashResult& r = detector_.lastResult();
    Location loc = gnss_.getLocation();

    CrashEvent e;
    char ts[32];
    payloads::isoTimestamp(ts, sizeof(ts));
    std::snprintf(e.eventId, sizeof(e.eventId), "EVT-%s-%lu-%04lx", GUARDIAN_DEVICE_ID,
                  static_cast<unsigned long>(payloads::clockValid() ? time(nullptr) : millis()),
                  static_cast<unsigned long>((esp_random() ^ ++eventCounter_) & 0xFFFF));
    std::strncpy(e.deviceId, GUARDIAN_DEVICE_ID, sizeof(e.deviceId) - 1);
    std::strncpy(e.vehicleId, GUARDIAN_VEHICLE_ID, sizeof(e.vehicleId) - 1);
    std::strncpy(e.timestamp, ts, sizeof(e.timestamp) - 1);
    e.locationValid = loc.valid;
    e.latitude = loc.latitude;
    e.longitude = loc.longitude;
    e.speedValid = r.speedValid;
    e.speedKmh = r.speedBeforeKmh;
    e.accelerationPeakG = r.accelerationPeakG;
    e.rotationPeakRadS = r.rotationPeakRadS;
    e.confidence = r.confidence;
    e.severity = r.severity;
    e.phoneConnected = ble_.isPhoneConnected();
    e.cellularConnected = cellular_.isConnected();

    char reasons[160];
    reasonsToString(r.reasons, reasons, sizeof(reasons));
    Serial.printf("[crash] EMERGENCY %s confidence=%.2f reasons=%s phone=%s\n", toString(r.severity), r.confidence,
                  reasons, e.phoneConnected ? "CONNECTED" : "DISCONNECTED");

    // Persist first: the event must survive a power cut or modem failure.
    xSemaphoreTake(queueMutex_, portMAX_DELAY);
    queue_.enqueue(e, millis());
    pendingEvents_ = static_cast<uint8_t>(queue_.pendingCount());
    xSemaphoreGive(queueMutex_);

    detector_.onTransmitStarted();
    ble_.notifyEvent(e, false);  // companion only; skipped silently when no phone
    xTaskNotifyGive(netTask_);   // transmit now
}

// Phone (primary) streamed its black box before it could be destroyed. Persist the bundle so it
// survives, and enqueue a backup crash event so the device delivers the alert on its own cellular
// link even if the phone never reaches the backend. Idempotent: the backend dedupes by eventId.
void GuardianDevice::ingestBlackBox(const std::string& json) {
    Serial.printf("[blackbox] received from phone (%u bytes)\n", static_cast<unsigned>(json.size()));

    // 1. Persist raw bundle to flash (write-then-rename so a power cut never leaves it half-written).
    File f = LittleFS.open("/blackbox.tmp", "w");
    if (f) {
        f.write(reinterpret_cast<const uint8_t*>(json.data()), json.size());
        f.close();
        if (LittleFS.rename("/blackbox.tmp", BLACKBOX_PATH)) Serial.println("[blackbox] persisted to flash");
    } else {
        Serial.println("[blackbox] WARN could not open flash to persist bundle");
    }

    // 2. Build a deliverable crash event from the bundle.
    JsonDocument doc;
    if (deserializeJson(doc, json) != DeserializationError::Ok) {
        Serial.println("[blackbox] parse failed; raw bundle stored, no backup event queued");
        return;
    }
    CrashEvent e;
    const char* eid = doc["eventId"] | "";
    if (eid[0] != '\0') {
        std::strncpy(e.eventId, eid, sizeof(e.eventId) - 1);
    } else {
        // Phone never got a backend id (was offline): mint one so the device can still deliver.
        std::snprintf(e.eventId, sizeof(e.eventId), "EVT-%s-%lu-%04lx", GUARDIAN_DEVICE_ID,
                      static_cast<unsigned long>(payloads::clockValid() ? time(nullptr) : millis()),
                      static_cast<unsigned long>((esp_random() ^ ++eventCounter_) & 0xFFFF));
    }
    std::strncpy(e.deviceId, GUARDIAN_DEVICE_ID, sizeof(e.deviceId) - 1);
    std::strncpy(e.vehicleId, GUARDIAN_VEHICLE_ID, sizeof(e.vehicleId) - 1);
    char ts[32];
    payloads::isoTimestamp(ts, sizeof(ts));
    std::strncpy(e.timestamp, ts, sizeof(e.timestamp) - 1);
    Location loc = gnss_.getLocation();
    e.locationValid = loc.valid;
    e.latitude = loc.latitude;
    e.longitude = loc.longitude;
    e.confidence = doc["confidence"] | 0.0f;
    e.severity = parseSeverity(doc["severity"] | "CRITICAL");
    e.phoneConnected = ble_.isPhoneConnected();
    e.cellularConnected = cellular_.isConnected();

    xSemaphoreTake(queueMutex_, portMAX_DELAY);
    queue_.enqueue(e, millis());
    pendingEvents_ = static_cast<uint8_t>(queue_.pendingCount());
    xSemaphoreGive(queueMutex_);
    ble_.notifyEvent(e, false);
    xTaskNotifyGive(netTask_);
    Serial.printf("[blackbox] backup crash event %s queued for cellular delivery\n", e.eventId);
}

void GuardianDevice::networkTaskEntry(void* self) { static_cast<GuardianDevice*>(self)->networkTask(); }

SendResult GuardianDevice::sendThunk(const CrashEvent& e, void* ctx) {
    return static_cast<GuardianDevice*>(ctx)->cellular_.sendCrashEvent(e);
}

void GuardianDevice::deliveredThunk(const CrashEvent& e, void* ctx) {
    auto* self = static_cast<GuardianDevice*>(ctx);
    Serial.printf("[cellular] ACK %s\n", e.eventId);
    self->ackReceived_ = true;
    self->ble_.notifyEvent(e, true);
}

void GuardianDevice::stateThunk(CrashState s, void* ctx) {
    Serial.printf("[crash] -> %s\n", toString(s));
    static_cast<GuardianDevice*>(ctx)->stateChanged_ = true;
}

void GuardianDevice::networkTask() {
    uint32_t lastHeartbeat = 0;
    uint32_t lastTimeSync = 0;
    bool registered = false;
    for (;;) {
        // Wake on a new crash event, or periodically for retries/heartbeat.
        bool urgent = ulTaskNotifyTake(pdTRUE, pdMS_TO_TICKS(config::QUEUE_FLUSH_PERIOD_MS)) > 0;
        uint32_t now = millis();

        if (!cellular_.simulatedOutage() && !cellular_.isConnected()) cellular_.connect();
        cellularStatus_ = static_cast<uint8_t>(cellular_.status());
        if (!cellular_.isConnected()) continue;

        if (!payloads::clockValid() && now - lastTimeSync > 30000) {
            lastTimeSync = now;
            cellular_.syncTime();
        }
        if (!registered) registered = cellular_.registerDevice();

        xSemaphoreTake(queueMutex_, portMAX_DELAY);
        queue_.flush(sendThunk, this, now, urgent, deliveredThunk, this);
        pendingEvents_ = static_cast<uint8_t>(queue_.pendingCount());
        xSemaphoreGive(queueMutex_);

        if (now - lastHeartbeat >= config::HEARTBEAT_PERIOD_MS || lastHeartbeat == 0) {
            lastHeartbeat = now;
            xSemaphoreTake(statusMutex_, portMAX_DELAY);
            DeviceStatus s = cachedStatus_;
            xSemaphoreGive(statusMutex_);
            s.cellular = cellular_.status();
            s.pendingEvents = pendingEvents_;
            cellular_.sendHeartbeat(s);
        }
    }
}

void GuardianDevice::maybeSyncClockFromGnss() {
    if (clockSet_ || payloads::clockValid()) return;
    uint32_t epoch;
    if (gnss_.getUtcEpoch(epoch)) {
        struct timeval tv = {static_cast<time_t>(epoch), 0};
        settimeofday(&tv, nullptr);
        clockSet_ = true;
        Serial.println("[gnss] system clock set from satellites");
    }
}

DeviceStatus GuardianDevice::snapshot() {
    DeviceStatus s;
    s.deviceId = GUARDIAN_DEVICE_ID;
    s.vehicleId = GUARDIAN_VEHICLE_ID;
    s.firmwareVersion = config::FIRMWARE_VERSION;
    s.batteryPercent = battery_.percent();
    s.location = gnss_.getLocation();
    s.gps = gnss_.hasFix() ? GpsStatus::AVAILABLE : GpsStatus::NO_FIX;
    s.cellular = static_cast<CellularStatus>(cellularStatus_.load());
    s.phoneConnected = ble_.isPhoneConnected();
    s.vehicle = vehicle_.read();
    s.vehicleConnected = s.vehicle.connected;
    s.crashState = detector_.state();
    s.pendingEvents = pendingEvents_;
    return s;
}

bool GuardianDevice::simulate(const char* scenario) {
#if GUARDIAN_DEMO_MODE
    if (!sim_.load(scenario, millis())) return false;
    simActive_ = true;
    Serial.printf("[sim] injecting scenario '%s' (demo mode)\n", scenario);
    return true;
#else
    (void)scenario;
    return false;
#endif
}

bool GuardianDevice::cancelVerification() {
    bool ok = detector_.cancel();
    Serial.println(ok ? "[crash] occupant reported safe - cancelled" : "[crash] nothing to cancel");
    return ok;
}

void GuardianDevice::setCellularOutage(bool outage) {
#if GUARDIAN_DEMO_MODE
    cellular_.setSimulatedOutage(outage);
    Serial.printf("[cellular] simulated outage %s\n", outage ? "ON" : "OFF");
    if (!outage) xTaskNotifyGive(netTask_);
#else
    (void)outage;
#endif
}

void GuardianDevice::replayHistory() {
    const QueueItem* items[EVENT_QUEUE_CAPACITY];
    static CrashEvent copies[EVENT_QUEUE_CAPACITY];
    bool delivered[EVENT_QUEUE_CAPACITY];
    xSemaphoreTake(queueMutex_, portMAX_DELAY);
    size_t n = queue_.recent(items, EVENT_QUEUE_CAPACITY);
    for (size_t i = 0; i < n; ++i) {
        copies[i] = items[i]->event;
        delivered[i] = items[i]->status == QueueStatus::SENT;
    }
    xSemaphoreGive(queueMutex_);
    for (size_t i = 0; i < n; ++i) {
        ble_.notifyEvent(copies[i], delivered[i]);
        delay(20);  // let each notification go out
    }
}

void GuardianDevice::handleCommands() {
    BleCommand cmd;
    while (ble_.pollCommand(cmd)) {
        switch (cmd.type) {
            case BleCommandType::PING: ble_.notify(snapshot()); break;
            case BleCommandType::CANCEL: cancelVerification(); break;
            case BleCommandType::GET_HISTORY: replayHistory(); break;
            case BleCommandType::SIMULATE: simulate(cmd.arg); break;
            case BleCommandType::CELLULAR_OFF: setCellularOutage(true); break;
            case BleCommandType::CELLULAR_ON: setCellularOutage(false); break;
            case BleCommandType::NONE: break;
        }
    }
}

void GuardianDevice::printStatus() {
    DeviceStatus s = snapshot();
    Serial.printf("device=%s fw=%s battery=%u%% gps=%s cellular=%s phone=%s vehicle=%s speed=%s crash=%s pending=%u\n",
                  s.deviceId, s.firmwareVersion, s.batteryPercent, toString(s.gps), toString(s.cellular),
                  s.phoneConnected ? "CONNECTED" : "DISCONNECTED", s.vehicleConnected ? "CONNECTED" : "NO",
                  s.vehicle.speedValid ? String(s.vehicle.speedKmh, 1).c_str() : "n/a", toString(s.crashState),
                  s.pendingEvents);
}

}  // namespace guardian
