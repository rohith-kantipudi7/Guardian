#pragma once
// Device identity, pins and timing. Secrets are in secrets.h (git-ignored).

#if __has_include("config/secrets.h")
#include "config/secrets.h"
#else
#warning "include/config/secrets.h missing - copy secrets.example.h; using insecure dev defaults"
#include "config/secrets.example.h"
#endif

#ifndef GUARDIAN_IMU_MPU6050
#define GUARDIAN_IMU_MPU6050 0
#endif
#ifndef GUARDIAN_GNSS_NMEA
#define GUARDIAN_GNSS_NMEA 0
#endif
#ifndef GUARDIAN_CELLULAR_SIMCOM
#define GUARDIAN_CELLULAR_SIMCOM 0
#endif
#ifndef GUARDIAN_VEHICLE_OBD
#define GUARDIAN_VEHICLE_OBD 0
#endif
#ifndef GUARDIAN_BATTERY_ADC
#define GUARDIAN_BATTERY_ADC 0
#endif
// Active buzzer on a GPIO for the local audible alarm. Off by default (no hardware).
#ifndef GUARDIAN_BUZZER
#define GUARDIAN_BUZZER 0
#endif
// Require an encrypted (bonded) BLE link for characteristic access.
#ifndef GUARDIAN_BLE_REQUIRE_ENCRYPTION
#define GUARDIAN_BLE_REQUIRE_ENCRYPTION 0
#endif
// Enables SIMULATE / CELLULAR_OFF commands over BLE and serial. Disable for field tests.
#ifndef GUARDIAN_DEMO_MODE
#define GUARDIAN_DEMO_MODE 1
#endif

namespace guardian::config {

constexpr const char* FIRMWARE_VERSION = "0.1.0";

// Timing
constexpr uint32_t SAMPLE_PERIOD_MS = 10;         // 100 Hz IMU
constexpr uint32_t BLE_STATUS_PERIOD_MS = 1000;
constexpr uint32_t HEARTBEAT_PERIOD_MS = 10000;
constexpr uint32_t QUEUE_FLUSH_PERIOD_MS = 2000;

// I2C (MPU6050)
constexpr int PIN_I2C_SDA = 21;
constexpr int PIN_I2C_SCL = 22;
// UART2: GNSS
constexpr int PIN_GNSS_RX = 16;
constexpr int PIN_GNSS_TX = 17;
constexpr uint32_t GNSS_BAUD = 9600;
// UART1: LTE modem
constexpr int PIN_MODEM_RX = 26;
constexpr int PIN_MODEM_TX = 27;
constexpr int PIN_MODEM_PWRKEY = 4;
constexpr uint32_t MODEM_BAUD = 115200;
// OBD-II ELM327. ESP32 has 3 UARTs (USB console, modem, GNSS), so OBD uses EspSoftwareSerial.
constexpr int PIN_OBD_RX = 32;
constexpr int PIN_OBD_TX = 33;
constexpr uint32_t OBD_BAUD = 38400;
// Battery sense through a 1:1 divider
constexpr int PIN_BATTERY_ADC = 35;
// Active buzzer (HIGH = sound). Drives the local emergency alarm.
constexpr int PIN_BUZZER = 25;

}  // namespace guardian::config
