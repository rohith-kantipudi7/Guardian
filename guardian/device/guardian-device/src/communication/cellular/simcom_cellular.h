#pragma once
// Independent LTE path through a SIMCOM modem (SIM7600 / A7670) using TinyGSM.
// Build with -DTINY_GSM_MODEM_SIM7600 (see platformio.ini env:esp32dev-hw).

#include <Arduino.h>
#include <TinyGsmClient.h>
#include <sys/time.h>
#include <time.h>

#include "communication/cellular/http_cellular_base.h"

namespace guardian {

class SimcomCellular : public HttpCellularBase {
public:
    SimcomCellular(HardwareSerial& serial, int rx, int tx, int pwrKey, uint32_t baud)
        : serial_(serial), rx_(rx), tx_(tx), pwrKey_(pwrKey), baud_(baud), modem_(serial), client_(modem_) {}

    bool connect() override {
        if (connected()) return true;
        if (!started_) {
            pinMode(pwrKey_, OUTPUT);
            digitalWrite(pwrKey_, HIGH);
            delay(100);
            digitalWrite(pwrKey_, LOW);  // pulse PWRKEY to power on
            delay(1000);
            digitalWrite(pwrKey_, HIGH);
            serial_.begin(baud_, SERIAL_8N1, rx_, tx_);
            delay(3000);
            if (!modem_.init()) return false;
            started_ = true;
        }
        if (!modem_.waitForNetwork(30000L)) return false;
        return modem_.gprsConnect(GUARDIAN_APN, GUARDIAN_APN_USER, GUARDIAN_APN_PASS);
    }

    void disconnect() override { modem_.gprsDisconnect(); }
    bool isConnected() override { return !outage_ && connected(); }

    CellularStatus status() override {
        if (outage_) return CellularStatus::DISCONNECTED;
        if (!started_) return CellularStatus::UNAVAILABLE;
        return connected() ? CellularStatus::CONNECTED : CellularStatus::CONNECTING;
    }

    bool syncTime() override {
        int year, month, day, hour, minute, second;
        float tz;
        if (!started_ || !modem_.getNetworkTime(&year, &month, &day, &hour, &minute, &second, &tz)) return false;
        if (year < 2024) return false;
        struct tm t = {};
        t.tm_year = year - 1900;
        t.tm_mon = month - 1;
        t.tm_mday = day;
        t.tm_hour = hour;
        t.tm_min = minute;
        t.tm_sec = second;
        // Modem reports local time + tz offset (hours); convert to UTC.
        time_t epoch = mktime(&t) - static_cast<time_t>(tz * 3600);
        struct timeval tv = {epoch, 0};
        settimeofday(&tv, nullptr);
        return true;
    }

protected:
    Client& client() override { return client_; }
    bool linkUp() override { return connected() || connect(); }

private:
    bool connected() { return started_ && modem_.isGprsConnected(); }

    HardwareSerial& serial_;
    int rx_, tx_, pwrKey_;
    uint32_t baud_;
    bool started_ = false;
    TinyGsm modem_;
#if GUARDIAN_BACKEND_TLS
    TinyGsmClientSecure client_;
#else
    TinyGsmClient client_;
#endif
};

}  // namespace guardian
