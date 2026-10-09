#pragma once
// NimBLE GATT server for the companion phone app. Optional: the device never waits on it.

#include <Arduino.h>
#include <NimBLEDevice.h>
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>

#include "interfaces.h"

namespace guardian {

class NimbleBle : public BleProvider, NimBLEServerCallbacks, NimBLECharacteristicCallbacks {
public:
    explicit NimbleBle(const char* deviceId) : deviceId_(deviceId) {}

    void start() override;
    bool isPhoneConnected() override { return connected_; }
    void notify(const DeviceStatus& status) override;
    void notifyCrashState(CrashState state, const CrashResult* result, uint32_t verifyRemainingMs) override;
    void notifyEvent(const CrashEvent& event, bool delivered) override;
    bool pollCommand(BleCommand& out) override;

private:
    // NimBLE-Arduino 1.4.x callback signatures
    void onConnect(NimBLEServer* server) override;
    void onDisconnect(NimBLEServer* server) override;
    void onWrite(NimBLECharacteristic* c) override;

    NimBLECharacteristic* make(NimBLEService* svc, const char* uuid, uint32_t props);
    static void set(NimBLECharacteristic* c, const String& v, bool notify);

    const char* deviceId_;
    volatile bool connected_ = false;
    QueueHandle_t commands_ = nullptr;
    NimBLECharacteristic* status_ = nullptr;
    NimBLECharacteristic* battery_ = nullptr;
    NimBLECharacteristic* gps_ = nullptr;
    NimBLECharacteristic* cellular_ = nullptr;
    NimBLECharacteristic* vehicle_ = nullptr;
    NimBLECharacteristic* crash_ = nullptr;
    NimBLECharacteristic* event_ = nullptr;
    NimBLECharacteristic* command_ = nullptr;
};

}  // namespace guardian
