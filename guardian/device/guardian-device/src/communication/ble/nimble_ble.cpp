#include "communication/ble/nimble_ble.h"

#include <ArduinoJson.h>

#include <cstring>

#include "ble_uuids.h"
#include "communication/payloads.h"
#include "config/device_config.h"

namespace guardian {

namespace {
#if GUARDIAN_BLE_REQUIRE_ENCRYPTION
constexpr uint32_t READ = NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::READ_ENC;
constexpr uint32_t WRITE = NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_ENC;
#else
constexpr uint32_t READ = NIMBLE_PROPERTY::READ;
constexpr uint32_t WRITE = NIMBLE_PROPERTY::WRITE;
#endif
constexpr uint32_t NOTIFY = NIMBLE_PROPERTY::NOTIFY;

BleCommandType parseCommand(const char* cmd) {
    if (!std::strcmp(cmd, "PING")) return BleCommandType::PING;
    if (!std::strcmp(cmd, "CANCEL")) return BleCommandType::CANCEL;
    if (!std::strcmp(cmd, "GET_HISTORY")) return BleCommandType::GET_HISTORY;
    if (!std::strcmp(cmd, "SIMULATE")) return BleCommandType::SIMULATE;
    if (!std::strcmp(cmd, "CELLULAR_OFF")) return BleCommandType::CELLULAR_OFF;
    if (!std::strcmp(cmd, "CELLULAR_ON")) return BleCommandType::CELLULAR_ON;
    return BleCommandType::NONE;
}
}  // namespace

NimBLECharacteristic* NimbleBle::make(NimBLEService* svc, const char* uuid, uint32_t props) {
    return svc->createCharacteristic(uuid, props);
}

void NimbleBle::set(NimBLECharacteristic* c, const String& v, bool notify) {
    if (!c) return;
    c->setValue(reinterpret_cast<const uint8_t*>(v.c_str()), v.length());
    if (notify) c->notify();
}

void NimbleBle::start() {
    commands_ = xQueueCreate(8, sizeof(BleCommand));
    String name = String(ble::DEVICE_NAME_PREFIX) + deviceId_;
    NimBLEDevice::init(name.c_str());
    NimBLEDevice::setMTU(247);
#if GUARDIAN_BLE_REQUIRE_ENCRYPTION
    // LE Secure Connections + bonding; "Just Works" because the device has no display/keypad.
    NimBLEDevice::setSecurityAuth(true, false, true);
    NimBLEDevice::setSecurityIOCap(BLE_HS_IO_NO_INPUT_OUTPUT);
#endif
    NimBLEServer* server = NimBLEDevice::createServer();
    server->setCallbacks(this, false);
    NimBLEService* svc = server->createService(ble::SERVICE_UUID);
    status_ = make(svc, ble::DEVICE_STATUS_UUID, READ | NOTIFY);
    battery_ = make(svc, ble::BATTERY_UUID, READ | NOTIFY);
    gps_ = make(svc, ble::GPS_STATUS_UUID, READ | NOTIFY);
    cellular_ = make(svc, ble::CELLULAR_STATUS_UUID, READ | NOTIFY);
    vehicle_ = make(svc, ble::VEHICLE_STATUS_UUID, READ | NOTIFY);
    crash_ = make(svc, ble::CRASH_STATUS_UUID, READ | NOTIFY);
    event_ = make(svc, ble::EVENT_UUID, READ | NOTIFY);
    command_ = make(svc, ble::COMMAND_UUID, WRITE);
    command_->setCallbacks(this);
    blackbox_ = make(svc, ble::BLACKBOX_UUID, WRITE);
    blackbox_->setCallbacks(this);
    svc->start();

    NimBLEAdvertising* adv = NimBLEDevice::getAdvertising();
    adv->addServiceUUID(ble::SERVICE_UUID);
    adv->setScanResponse(true);
    adv->start();
    Serial.printf("[ble] advertising as %s\n", name.c_str());
}

void NimbleBle::onConnect(NimBLEServer*) {
    connected_ = true;
    Serial.println("[ble] phone connected");
}

void NimbleBle::onDisconnect(NimBLEServer*) {
    connected_ = false;
    Serial.println("[ble] phone disconnected - emergency path unaffected");
    NimBLEDevice::startAdvertising();
}

void NimbleBle::onWrite(NimBLECharacteristic* c) {
    if (c == blackbox_) {
        // Phone streams the post-accident black box in chunks; 0x04 (EOT) ends the bundle.
        std::string v = c->getValue();
        for (char ch : v) {
            if (ch == '\x04') {
                blackbox_json_ = String(blackboxBuf_.c_str());
                blackboxReady_ = true;
                Serial.printf("[ble] black box received (%u bytes): %s\n",
                              static_cast<unsigned>(blackboxBuf_.size()), blackboxBuf_.c_str());
                blackboxBuf_.clear();
            } else {
                blackboxBuf_.push_back(ch);
            }
        }
        return;
    }
    std::string v = c->getValue();
    JsonDocument doc;
    if (deserializeJson(doc, v) != DeserializationError::Ok) return;
    BleCommand cmd;
    cmd.type = parseCommand(doc["cmd"] | "");
    std::strncpy(cmd.arg, doc["arg"] | "", sizeof(cmd.arg) - 1);
    if (cmd.type != BleCommandType::NONE && commands_) xQueueSend(commands_, &cmd, 0);
}

bool NimbleBle::pollCommand(BleCommand& out) {
    return commands_ && xQueueReceive(commands_, &out, 0) == pdTRUE;
}

bool NimbleBle::consumeBlackBox(std::string& out) {
    if (!blackboxReady_) return false;
    blackboxReady_ = false;
    out = blackbox_json_.c_str();
    return true;
}

void NimbleBle::notify(const DeviceStatus& s) {
    bool n = connected_;
    set(status_, payloads::bleDeviceStatus(s), n);
    set(battery_, payloads::bleBattery(s), n);
    set(gps_, payloads::bleGps(s), n);
    set(cellular_, payloads::bleCellular(s), n);
    set(vehicle_, payloads::bleVehicle(s), n);
}

void NimbleBle::notifyCrashState(CrashState state, const CrashResult* result, uint32_t verifyRemainingMs) {
    set(crash_, payloads::bleCrashStatus(state, result, verifyRemainingMs), connected_);
}

void NimbleBle::notifyEvent(const CrashEvent& event, bool delivered) {
    set(event_, payloads::bleEvent(event, delivered), connected_);
}

}  // namespace guardian
