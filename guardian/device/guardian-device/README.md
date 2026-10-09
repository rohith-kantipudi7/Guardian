# Guardian Device Firmware (ESP32 / PlatformIO)

> Prototype for hackathon demonstration. **Not** an automotive-certified crash detector.
> Read-only vehicle access. Never controls brakes, steering, airbags or CAN.

## Build & flash

```powershell
pip install platformio                     # or use the PlatformIO VS Code extension
copy include\config\secrets.example.h include\config\secrets.h   # edit Wi-Fi/backend/secret
pio run -e esp32dev -t upload              # all hardware mocked; Wi-Fi stands in for LTE
pio device monitor                         # 115200 baud
pio run -e esp32dev-hw -t upload           # MPU6050 + NMEA GNSS + SIM7600 + ELM327
pio test -e native                         # crash engine + event queue tests on the host
```

Register the device with the backend once (same secret as `GUARDIAN_DEVICE_SECRET`) — the
firmware also calls `POST /api/devices/register` on first connection.

## Architecture

```text
core 1  loop():  IMU (100 Hz) -> CrashDetector -> EventQueue (LittleFS)      never blocks
core 0  task:    EventQueue -> CellularProvider -> backend; heartbeat; time sync
BLE:             status + crash notifications to the phone if connected     optional
```

| Interface | Mock | Real |
|---|---|---|
| `ImuSensor` | `MockImu` (scenario player) | `Mpu6050Imu` |
| `GnssProvider` | `MockGnss` (Bengaluru) | `NmeaGnss` (TinyGPSPlus) |
| `CellularProvider` | `MockCellularProvider` (Wi-Fi) | `SimcomCellular` (TinyGSM) |
| `VehicleTelemetryProvider` | `MockVehicleTelemetry` | `ObdVehicleTelemetry` (ELM327, Mode 01 only) |
| `BatteryProvider` | `MockBattery` | `AdcBattery` |
| `BleProvider` | — | `NimbleBle` |
| `QueueStorage` | in-memory (tests) | `LittleFsQueueStorage` |

Pure C++ (no Arduino) and unit-tested natively: `src/crash/`, `src/storage/event_queue.*`,
`src/sim/`, `src/communication/response.h`.

## Serial demo commands

`normal`, `hard_brake`, `pothole`, `minor_impact`, `severe_crash`, `rollover` inject a
synthetic scenario into the crash engine (works with real or mock IMU; demo mode only).
`cancel` = I'M SAFE during verification. `cell off` / `cell on` = simulated network outage.
`status` prints device state.

Same commands are available over BLE (`COMMAND` characteristic) — see `docs/ble-protocol.md`.

## Configuration

- Crash thresholds: `include/config/crash_config.h` (single source; mirrors the simulator).
- Pins, timings, feature flags: `include/config/device_config.h`.
- Identity, backend, Wi-Fi, APN: `include/config/secrets.h` (git-ignored).
- `GUARDIAN_DEMO_MODE=0` disables simulation/outage commands for field tests.
- `GUARDIAN_BLE_REQUIRE_ENCRYPTION=1` requires an encrypted, bonded BLE link.
