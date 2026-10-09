#pragma once
// MockCellularProvider: same interface as the LTE modem, but uses Wi-Fi so the full
// device -> backend path can be developed before the modem arrives.
// It is still phone-independent: the ESP32 joins the network itself.

#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <time.h>

#include "communication/cellular/http_cellular_base.h"

namespace guardian {

class MockCellularProvider : public HttpCellularBase {
public:
    bool connect() override {
        if (WiFi.status() == WL_CONNECTED) return true;
        WiFi.mode(WIFI_STA);
        WiFi.begin(GUARDIAN_WIFI_SSID, GUARDIAN_WIFI_PASSWORD);
        uint32_t start = millis();
        while (WiFi.status() != WL_CONNECTED && millis() - start < 15000) delay(250);
#if GUARDIAN_BACKEND_TLS
        // POC: no CA pinning. Replace with secure_.setCACert(...) before any real deployment.
        secure_.setInsecure();
#endif
        return WiFi.status() == WL_CONNECTED;
    }

    void disconnect() override { WiFi.disconnect(true); }
    bool isConnected() override { return !outage_ && WiFi.status() == WL_CONNECTED; }

    CellularStatus status() override {
        if (outage_) return CellularStatus::DISCONNECTED;
        return WiFi.status() == WL_CONNECTED ? CellularStatus::CONNECTED : CellularStatus::CONNECTING;
    }

    bool syncTime() override {
        if (!isConnected()) return false;
        configTime(0, 0, "pool.ntp.org", "time.google.com");
        uint32_t start = millis();
        while (!payloads::clockValid() && millis() - start < 5000) delay(100);
        return payloads::clockValid();
    }

protected:
#if GUARDIAN_BACKEND_TLS
    Client& client() override { return secure_; }
#else
    Client& client() override { return plain_; }
#endif
    bool linkUp() override { return WiFi.status() == WL_CONNECTED || connect(); }

private:
    WiFiClient plain_;
    WiFiClientSecure secure_;
};

}  // namespace guardian
