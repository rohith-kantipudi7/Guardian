# Hardware (POC)

> No custom PCB for the submission. Modules on a breadboard / perfboard.

## Bill of materials

| Part | Example | Notes |
|---|---|---|
| MCU | ESP32-DevKitC (WROOM-32) | BLE + Wi-Fi, 2 cores |
| IMU | MPU6050 / MPU6500 breakout | ±16 g, ±2000 °/s; ≥ 8 g range required |
| GNSS | u-blox NEO-6M/M8N or ATGM336H | NMEA 9600 baud, active antenna recommended |
| LTE modem | SIM7600 / A7670 module + antenna + nano SIM (data plan) | Needs 2 A peaks: own supply/caps |
| Backup battery | 1S Li-ion 18650 + TP4056/charger + 5 V boost | Device must survive vehicle power loss |
| Vehicle power | 12 V → 5 V buck (fused) | From accessory socket for the POC |
| OBD-II (later) | ELM327 **UART** adapter | Read-only |
| Misc | 100 kΩ/100 kΩ divider, 1000 µF cap near modem | |

## Wiring (matches `include/config/device_config.h`)

| Signal | ESP32 pin | Module pin |
|---|---|---|
| I2C SDA | GPIO 21 | MPU SDA |
| I2C SCL | GPIO 22 | MPU SCL |
| IMU power | 3V3 / GND | VCC / GND |
| GNSS RX (UART2) | GPIO 16 | GNSS TX |
| GNSS TX (UART2) | GPIO 17 | GNSS RX |
| Modem RX (UART1) | GPIO 26 | Modem TX |
| Modem TX (UART1) | GPIO 27 | Modem RX |
| Modem PWRKEY | GPIO 4 | PWRKEY (via transistor if 1.8 V logic) |
| OBD RX (SoftwareSerial) | GPIO 32 | ELM327 TX |
| OBD TX (SoftwareSerial) | GPIO 33 | ELM327 RX |
| Battery sense | GPIO 35 (ADC1) | Battery + through 1:1 divider |

Check modem logic levels (many SIM7600 boards are 3.3 V tolerant; bare modules are 1.8 V).

## Bring-up order (spec §33)

1. ESP32 alone: `pio run -e esp32dev -t upload`, serial `status`, `severe_crash`.
2. + IMU: set `GUARDIAN_IMU_MPU6050=1`; tap test should show `SUSPECTED_IMPACT` → `FALSE_POSITIVE`.
3. + BLE: phone app → Settings → disable mock → Scan & connect.
4. + GNSS: `GUARDIAN_GNSS_NMEA=1`; outdoor fix → `gps=AVAILABLE`.
5. + LTE: `GUARDIAN_CELLULAR_SIMCOM=1`; APN in `secrets.h`; phone OFF → crash reaches dashboard.
6. + OBD-II: `GUARDIAN_VEHICLE_OBD=1`; speed appears in VEHICLE_STATUS.

Use `esp32dev-hw` once all modules are present.

## Installation in the test vehicle (spec §34)

- Mount the enclosure rigidly to the chassis/console (not the dashboard top), +x towards the front.
- Power from a fused accessory socket; backup battery keeps the device alive after power loss.
- Do **not** interfere with airbags, brakes, steering, or any safety system.
- OBD-II adapter is read-only; firmware only sends AT and Mode 01 requests.
- Use the serial/BLE `SIMULATE` commands for demonstrations — never stage a real crash.
