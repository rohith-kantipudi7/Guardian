# BLE Protocol (Device ↔ Phone, companion only)

The device is a GATT server advertising as `Guardian-<deviceId>` with the Guardian service.
**BLE never carries the emergency to the backend**; it only mirrors state to the phone and
accepts user commands.

UUID base: `4755xxxx-4152-4449-414e-000000000000` (`4755 4152 4449 414e` = ASCII "GUARDIAN").
Defined in `device/guardian-device/include/ble_uuids.h` and Android `GuardianBleUuids.kt`.

| Characteristic | UUID | Props | Payload (UTF-8 JSON) |
|---|---|---|---|
| Service | `47550000-…` | | |
| DEVICE_STATUS | `47550001-…` | read, notify | `{"deviceId","vehicleId","firmware","battery","gps","cellular","vehicleConnected","crashState","pendingEvents"}` |
| BATTERY | `47550002-…` | read, notify | `{"battery":91}` |
| GPS_STATUS | `47550003-…` | read, notify | `{"status":"AVAILABLE","lat":12.97,"lon":77.59,"accuracy":5.0}` |
| CELLULAR_STATUS | `47550004-…` | read, notify | `{"status":"CONNECTED","pendingEvents":0}` |
| VEHICLE_STATUS | `47550005-…` | read, notify | `{"connected":true,"speed":72.0,"rpm":null}` (`null` = not available) |
| CRASH_STATUS | `47550006-…` | read, notify | `{"state":"VERIFYING","severity":"MODERATE","confidence":0.63,"reasons":"impact,rapid_rotation","verifyRemainingMs":9000}` |
| EVENT | `47550007-…` | read, notify | `{"eventId","timestamp","severity","confidence","lat","lon","phoneConnected","delivered"}` |
| COMMAND | `47550008-…` | write | `{"cmd":"…","arg":"…"}` |
| BLACKBOX | `47550009-…` | write | Phone-primary post-accident bundle: chunked UTF-8 JSON, `0x04` (EOT) terminates |

Status characteristics are notified every 1 s; CRASH_STATUS on every state transition;
EVENT when an event is created (`delivered:false`) and again when the backend ACKs it
(`delivered:true`).

## Commands

| `cmd` | `arg` | Effect |
|---|---|---|
| `PING` | | Immediate status notify |
| `CANCEL` | | I'M SAFE — cancels a crash in `VERIFYING` |
| `GET_HISTORY` | | Replays recent events on EVENT (phone recovery after reconnect) |
| `SIMULATE` | `normal`/`hard_brake`/`pothole`/`minor_impact`/`severe_crash`/`rollover` | Demo only: injects a scenario into the crash engine |
| `CELLULAR_OFF` / `CELLULAR_ON` | | Demo only: simulated network outage |

Demo-only commands are compiled out with `GUARDIAN_DEMO_MODE=0`.

## Connection flow (Android)

Scan (filter: service UUID) → connect (LE) → request MTU 247 → discover → enable notifications
(one GATT op at a time) → read initial values → `GET_HISTORY`. On link loss the app reconnects
with exponential backoff (2 s → 30 s) unless the user forced a disconnect.

## Security

POC default: open characteristics for easy demo. Set `GUARDIAN_BLE_REQUIRE_ENCRYPTION=1` to
require an encrypted, bonded link (LE Secure Connections, Just Works). See [security.md](security.md).
