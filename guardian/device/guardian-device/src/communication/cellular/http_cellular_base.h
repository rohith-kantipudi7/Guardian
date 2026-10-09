#pragma once
// Shared HTTP(S) logic for every network path. Subclasses only provide a connected Arduino Client.

#include <Arduino.h>
#include <ArduinoHttpClient.h>
#include <Client.h>

#include "communication/payloads.h"
#include "communication/response.h"
#include "config/device_config.h"
#include "interfaces.h"

namespace guardian {

class HttpCellularBase : public CellularProvider {
public:
    bool registerDevice() override {
        String body = payloads::registerJson(GUARDIAN_DEVICE_ID, GUARDIAN_DEVICE_SECRET, config::FIRMWARE_VERSION,
                                             GUARDIAN_VEHICLE_ID);
        return post("/api/devices/register", body) == SendResult::ACKED;
    }

    SendResult sendHeartbeat(const DeviceStatus& status) override {
        char ts[32];
        payloads::isoTimestamp(ts, sizeof(ts));
        String path = String("/api/devices/") + GUARDIAN_DEVICE_ID + "/heartbeat";
        return post(path.c_str(), payloads::heartbeatJson(status, ts));
    }

    SendResult sendCrashEvent(const CrashEvent& event) override { return post("/api/crashes", payloads::crashJson(event)); }

    void setSimulatedOutage(bool outage) override { outage_ = outage; }
    bool simulatedOutage() const override { return outage_; }

protected:
    virtual Client& client() = 0;
    virtual bool linkUp() = 0;

    SendResult post(const char* path, const String& body) {
        if (outage_ || !linkUp()) return SendResult::RETRY;
        HttpClient http(client(), GUARDIAN_BACKEND_HOST, GUARDIAN_BACKEND_PORT);
        http.setHttpResponseTimeout(10000);
        http.beginRequest();
        int err = http.post(path);
        if (err != 0) {
            http.stop();
            return SendResult::RETRY;
        }
        http.sendHeader("Content-Type", "application/json");
        http.sendHeader("Content-Length", body.length());
        http.sendHeader("X-Device-Secret", GUARDIAN_DEVICE_SECRET);
        http.beginBody();
        http.print(body);
        http.endRequest();
        int code = http.responseStatusCode();
        String resp = http.responseBody();
        http.stop();
        if (code != 200 && code != 201) Serial.printf("[net] POST %s -> %d %s\n", path, code, resp.c_str());
        return classifyResponse(code);
    }

    bool outage_ = false;
};

}  // namespace guardian
