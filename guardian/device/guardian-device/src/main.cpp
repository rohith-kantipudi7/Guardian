// Guardian vehicle device firmware entry point.
// Hardware is selected at build time (platformio.ini); every module has a mock.

#include <Arduino.h>

#include "communication/ble/nimble_ble.h"
#include "config/device_config.h"
#include "guardian_device.h"
#include "io/buzzer.h"
#include "sensors/mock_sensors.h"
#include "storage/littlefs_storage.h"

#if GUARDIAN_IMU_MPU6050
#include "sensors/imu/mpu6050_imu.h"
#endif
#if GUARDIAN_GNSS_NMEA
#include "sensors/gnss/nmea_gnss.h"
#endif
#if GUARDIAN_CELLULAR_SIMCOM
#include "communication/cellular/simcom_cellular.h"
#else
#include "communication/cellular/mock_cellular.h"
#endif
#if GUARDIAN_VEHICLE_OBD
#include "vehicle/obd_vehicle_telemetry.h"
#endif
#if GUARDIAN_BATTERY_ADC
#include "power/battery.h"
#endif

using namespace guardian;

namespace {

#if GUARDIAN_IMU_MPU6050
Mpu6050Imu imu(config::PIN_I2C_SDA, config::PIN_I2C_SCL);
#else
MockImu imu;
#endif

#if GUARDIAN_GNSS_NMEA
NmeaGnss gnss(Serial2, config::PIN_GNSS_RX, config::PIN_GNSS_TX, config::GNSS_BAUD);
#else
MockGnss gnss;
#endif

#if GUARDIAN_VEHICLE_OBD
ObdVehicleTelemetry vehicle(config::PIN_OBD_RX, config::PIN_OBD_TX, config::OBD_BAUD);
#elif GUARDIAN_IMU_MPU6050
MockVehicleTelemetry vehicle(nullptr);  // real IMU, no OBD: speed unavailable
#else
MockVehicleTelemetry vehicle(&imu);
#endif

#if GUARDIAN_BATTERY_ADC
AdcBattery battery(config::PIN_BATTERY_ADC);
#else
MockBattery battery;
#endif

#if GUARDIAN_CELLULAR_SIMCOM
SimcomCellular cellular(Serial1, config::PIN_MODEM_RX, config::PIN_MODEM_TX, config::PIN_MODEM_PWRKEY,
                        config::MODEM_BAUD);
#else
MockCellularProvider cellular;
#endif

NimbleBle ble(GUARDIAN_DEVICE_ID);
LittleFsQueueStorage storage;
EventQueue queue(&storage);
#if GUARDIAN_BUZZER
GpioBuzzer alert(config::PIN_BUZZER);
#else
MockAlertSignal alert;
#endif
GuardianDevice device(imu, gnss, vehicle, battery, cellular, ble, queue, alert);

String line;

void printHelp() {
    Serial.println(
        "commands: normal | hard_brake | pothole | minor_impact | severe_crash | rollover\n"
        "          cancel | cell off | cell on | status | help");
}

void handleSerial() {
    while (Serial.available()) {
        char c = static_cast<char>(Serial.read());
        if (c != '\n' && c != '\r') {
            if (line.length() < 64) line += c;
            continue;
        }
        line.trim();
        if (line.isEmpty()) continue;
        if (line == "cancel") device.cancelVerification();
        else if (line == "cell off") device.setCellularOutage(true);
        else if (line == "cell on") device.setCellularOutage(false);
        else if (line == "status") device.printStatus();
        else if (line == "help") printHelp();
        else if (!device.simulate(line.c_str())) Serial.printf("unknown command '%s'\n", line.c_str());
        line = "";
    }
}

}  // namespace

void setup() {
    Serial.begin(115200);
    delay(200);
    Serial.println("\n=== GUARDIAN vehicle device (prototype - not automotive certified) ===");
    if (!storage.begin()) Serial.println("[storage] LittleFS mount FAILED - events will not survive reboot");
    device.begin();
    printHelp();
}

void loop() {
    device.loop();
    handleSerial();
}
