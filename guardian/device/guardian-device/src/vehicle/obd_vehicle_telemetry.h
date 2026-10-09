#pragma once
// Read-only OBD-II telemetry through an ELM327-compatible UART adapter.
// SAFETY: only AT configuration and Mode 01 (current data) requests are ever sent.
// No Mode 04 (clear DTCs), no Mode 08 (control), no raw CAN frames.

#include <Arduino.h>
#include <SoftwareSerial.h>

#include <cstdlib>
#include <cstring>

#include "interfaces.h"

namespace guardian {

class ObdVehicleTelemetry : public VehicleTelemetryProvider {
public:
    ObdVehicleTelemetry(int rx, int tx, uint32_t baud) : rx_(rx), tx_(tx), baud_(baud) {}

    bool begin() override {
        serial_.begin(baud_, SWSERIAL_8N1, rx_, tx_);
        step_ = 0;
        return true;
    }

    // Non-blocking request/response loop; never stalls crash sensing.
    void update() override {
        while (serial_.available()) {
            char c = static_cast<char>(serial_.read());
            if (c == '>') {  // ELM327 prompt: response complete
                handleResponse();
                waiting_ = false;
            } else if (len_ < sizeof(buf_) - 1 && c != '\r' && c != '\n' && c != ' ') {
                buf_[len_++] = c;
            }
        }
        uint32_t now = millis();
        if (waiting_ && now - sentAt_ > TIMEOUT_MS) waiting_ = false;
        if (!waiting_ && now - sentAt_ >= POLL_MS) sendNext();
    }

    VehicleTelemetry read() override {
        VehicleTelemetry t = data_;
        t.connected = lastValidMs_ != 0 && millis() - lastValidMs_ < STALE_MS;
        if (!t.connected) t.speedValid = t.rpmValid = false;
        return t;
    }

private:
    static constexpr uint32_t TIMEOUT_MS = 1000;
    static constexpr uint32_t POLL_MS = 200;
    static constexpr uint32_t STALE_MS = 5000;
    static constexpr const char* INIT[] = {"ATZ", "ATE0", "ATL0", "ATS0", "ATSP0"};
    static constexpr size_t INIT_COUNT = 5;

    static bool isAllowed(const char* cmd) {
        return std::strncmp(cmd, "AT", 2) == 0 || std::strncmp(cmd, "01", 2) == 0;
    }

    void send(const char* cmd) {
        if (!isAllowed(cmd)) return;  // read-only guard
        len_ = 0;
        std::strncpy(lastCmd_, cmd, sizeof(lastCmd_) - 1);
        serial_.print(cmd);
        serial_.print('\r');
        sentAt_ = millis();
        waiting_ = true;
    }

    void sendNext() {
        if (step_ < INIT_COUNT) {
            send(INIT[step_++]);
            return;
        }
        send(pollSpeed_ ? "010D" : "010C");  // vehicle speed / engine RPM
        pollSpeed_ = !pollSpeed_;
    }

    static int hexByte(const char* p) {
        char tmp[3] = {p[0], p[1], 0};
        return static_cast<int>(std::strtol(tmp, nullptr, 16));
    }

    void handleResponse() {
        buf_[len_] = 0;
        // Expected: 410D<A> for speed, 410C<A><B> for RPM. "NODATA" / "?" mean unsupported.
        const char* p = std::strstr(buf_, "41");
        if (!p) return;
        if (std::strncmp(p, "410D", 4) == 0 && std::strlen(p) >= 6) {
            data_.speedKmh = static_cast<float>(hexByte(p + 4));
            data_.speedValid = true;
            lastValidMs_ = millis();
        } else if (std::strncmp(p, "410C", 4) == 0 && std::strlen(p) >= 8) {
            data_.rpm = (hexByte(p + 4) * 256 + hexByte(p + 6)) / 4.0f;
            data_.rpmValid = true;
            lastValidMs_ = millis();
        }
    }

    int rx_, tx_;
    uint32_t baud_;
    EspSoftwareSerial::UART serial_;
    char buf_[64] = {0};
    size_t len_ = 0;
    char lastCmd_[8] = {0};
    bool waiting_ = false;
    bool pollSpeed_ = true;
    uint32_t sentAt_ = 0;
    size_t step_ = 0;
    uint32_t lastValidMs_ = 0;
    VehicleTelemetry data_;
};

}  // namespace guardian
