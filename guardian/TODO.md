# Guardian POC — End-to-End TODO

Source of truth: [`../GUARDIAN_COPILOT_BUILD_SPEC.md`](../GUARDIAN_COPILOT_BUILD_SPEC.md).

Legend: `[x]` done & verified · `[~]` implemented, needs hardware/toolchain verification · `[ ]` not started

Core rule for every item: **the phone is never on the emergency path** (`Device -> Cellular -> Backend`). BLE is companion-only.

---

## Milestone workflow (applies to every milestone)

1. Inspect existing code.
2. Implement only that milestone.
3. Run tests / build.
4. Fix failures.
5. Update docs.
6. Report files changed and remaining issues.
7. Keep the project runnable. Do not move on if the current milestone is broken.

---

## M1 — Repository structure + documentation

- [x] Root layout: `mobile/`, `device/`, `backend/`, `dashboard/`, `simulator/`, `docs/`, `scripts/`, `.github/`
- [x] `README.md` with architecture, quick start, per-component commands
- [x] `.gitignore` (Python, Node, Android, PlatformIO, secrets, local DBs)
- [x] `.env.example` (BACKEND_URL, DATABASE_URL, WS_URL, MAP_PROVIDER, DEVICE_ID, VEHICLE_ID, DEMO_MODE, DEVICE_SECRET)
- [x] `docker-compose.yml` (backend + dashboard)
- [x] `.github/copilot-instructions.md` (coding rules §31, safety boundary §2)
- [x] `docs/architecture.md` — components, data flows, emergency path vs companion path
- [x] `docs/api.md` — REST + WebSocket contract, error codes, dedup semantics
- [x] `docs/ble-protocol.md` — service/characteristic UUIDs, payloads, commands
- [x] `docs/device-protocol.md` — device → backend heartbeat/crash, auth headers, retry/ACK
- [x] `docs/hardware.md` — BOM, wiring, pin map, power, installation safety (§33, §34)
- [x] `docs/crash-detection.md` — features, scoring, thresholds, state machine (§12–14)
- [x] `docs/demo.md` — scripted judge demo (§23, §37) + manual checklist
- [x] `docs/testing.md` — test matrix (§28)
- [x] `docs/security.md` — POC security controls (§26)
- [x] `scripts/dev-start.ps1` / `dev-start.sh`, `scripts/seed-demo-data.ps1` / `.sh`

## M2 — FastAPI backend (`backend/guardian-api`)

### Setup
- [x] `requirements.txt` (fastapi, uvicorn, sqlalchemy, pydantic-settings, websockets, httpx, pytest)
- [x] `app/config.py` — settings from env (`DATABASE_URL`, `DEVICE_OFFLINE_AFTER_SECONDS`, `REQUIRE_DEVICE_AUTH`, `CORS_ORIGINS`, `LOCATION_RETENTION_DAYS`)
- [x] `app/database/` — engine, session, `Base`, `init_db()`; SQLite local, PostgreSQL-compatible types only
- [x] `Dockerfile`

### Models (SQLAlchemy) — spec §5
- [x] `User`, `EmergencyContact`
- [x] `Vehicle`
- [x] `Device` (+ `secret_hash`, status fields, `last_heartbeat`)
- [x] `CrashEvent` (unique `event_id` → dedup)
- [x] `EmergencyAction` (simulated police / hospital / contact workflow, §36)

### Schemas (Pydantic, camelCase JSON)
- [x] Enums: `Severity` (NORMAL/MINOR/MODERATE/CRITICAL), `CrashStatus` (DETECTED/VERIFYING/CANCELLED/EMERGENCY_ACTIVE/ACKNOWLEDGED/RESOLVED), `GpsStatus`, `CellularStatus`, `BleStatus`
- [x] Validation: lat ∈ [-90,90], lon ∈ [-180,180], confidence ∈ [0,1], battery ∈ [0,100], non-negative peaks/speed
- [x] UTC timestamps serialised with `Z`

### Repositories + services
- [x] `repositories/` — users, vehicles, devices, crashes
- [x] `services/device_service.py` — register (hash secret), authenticate, heartbeat, online computation
- [x] `services/crash_service.py` — create (dedup), initial status by severity, state transitions (acknowledge/cancel/resolve), invalid transition → 409
- [x] `services/emergency_service.py` — simulated workflows, never real dispatch
- [x] `realtime/broadcaster.py` — WebSocket connection manager

### API — spec §6
- [x] `GET /health`
- [x] `POST /api/users`, `GET /api/users/{userId}` (with emergency contacts)
- [x] `POST /api/vehicles`, `GET /api/vehicles`, `GET /api/vehicles/{vehicleId}`
- [x] `POST /api/devices/register`, `GET /api/devices`, `GET /api/devices/{deviceId}`, `POST /api/devices/{deviceId}/heartbeat`
- [x] `POST /api/crashes` (201 new / 200 duplicate, `phoneConnected=false` always accepted)
- [x] `GET /api/crashes` (filters: status, deviceId, vehicleId, active, limit)
- [x] `GET /api/crashes/{eventId}`
- [x] `POST /api/crashes/{eventId}/acknowledge|cancel|resolve`
- [x] `WS /ws/emergency` — `CRASH_DETECTED`, `CRASH_UPDATED`, `DEVICE_HEARTBEAT`, `DEVICE_REGISTERED`
- [x] Device auth: `X-Device-Secret` header on heartbeat + crash create

### Tests (pytest) — spec §28
- [x] health
- [x] user + vehicle registration
- [x] device registration + re-registration with wrong secret rejected
- [x] heartbeat updates state; unauthenticated heartbeat rejected
- [x] crash validation (bad lat, confidence > 1, bad severity)
- [x] crash created with `phoneConnected=false` → `EMERGENCY_ACTIVE`
- [x] duplicate event rejected (not stored twice, not re-broadcast)
- [x] acknowledge / cancel / resolve + invalid transitions
- [x] WebSocket broadcast received on crash create

## M3 — React dashboard (`dashboard/guardian-dashboard`)

- [x] Vite + React + TypeScript + MUI + React Query + React Router
- [x] `services/api.ts` — typed REST client
- [x] `services/ws.ts` — auto-reconnecting WebSocket, invalidates React Query caches
- [x] `types.ts` — mirrors backend contract
- [x] Map abstraction `components/map/` — `MapView` interface, Leaflet/OSM provider, coordinates-only fallback (`VITE_MAP_PROVIDER=none`)
- [x] Pages: `/` Dashboard, `/incidents`, `/incidents/:id`, `/devices`, `/vehicles`
- [x] Components: `IncidentCard`, `SeverityBadge`, `DeviceStatus`, `ConnectionChip`, `VehicleMap`, `StatusBadge`
- [x] Highlight the key state: **Phone DISCONNECTED + Device ONLINE + Cellular CONNECTED → incident still displayed normally**
- [x] Incident detail with ACKNOWLEDGE / RESOLVE buttons
- [x] Live updates without refresh; connection indicator; audible/visual alert on new CRITICAL
- [x] `npm run build` passes (type-check + bundle)
- [x] `Dockerfile` (static build served by nginx)

## M4 — Vehicle / device simulator (`simulator/vehicle-simulator`)

- [x] Pure-stdlib Python (no extra runtime deps)
- [x] `scenarios.py` — synthetic, physically consistent IMU (100 Hz) + speed streams: normal, hard_brake, pothole, minor_impact, severe_crash, rollover
- [x] `crash_engine.py` — reference implementation of the firmware crash engine (same config + state machine)
- [x] `network.py` — ONLINE / OFFLINE / DELAYED network simulator
- [x] `event_store.py` — persistent queue, unique IDs, retry, mark transmitted on ACK
- [x] `device.py` — virtual Guardian device (register, heartbeat, detect, queue, transmit)
- [x] CLI: `normal | hard_brake | minor_impact | severe_crash | phone_disconnect | phone_connect | network_offline | network_restore | network_delayed | heartbeat | flush | status | run | demo | reset`
- [x] State persisted between invocations (`.sim_state.json`)
- [x] Tests: normal, hard brake, pothole, minor, severe, repeated impacts, rotation, missing vehicle data, duplicate suppression, offline queue → exactly-once delivery

## M5 — Android app with mock device (`mobile/guardian-android`)

- [x] Gradle (Kotlin DSL) project, Compose, minSdk 26, targetSdk 35; Gradle wrapper (`gradlew`) generated
- [x] Layers: `ui` → `viewmodel` → `domain` → `data` (BLE / REST / Room)
- [x] `GuardianDeviceClient` interface + `MockGuardianDeviceClient` + `BleGuardianDeviceClient`
- [x] Retrofit `GuardianApi` (crash list, cancel) — history sync from backend verified on emulator
- [x] Room `CrashEventEntity` for local history (merges BLE + backend)
- [x] Screens: Dashboard, Emergency (I'M SAFE → `POST /api/crashes/{id}/cancel`), Device, History, Settings, Developer Demo Controls (§24) — verified on emulator
- [x] Settings: vehicle info, emergency contacts, device pairing, demo mode toggle, notifications
- [x] Recovery: on reconnect pull missed events (BLE `EVENT` + backend history)
- [x] `assembleDebug` builds (10.3 MB APK); runs on Android emulator (mock device)
- [ ] Install on the iQOO phone (`adb install app/build/outputs/apk/debug/app-debug.apk`)
- [x] Unit tests: `BlePayloadParserTest` (7), `MockGuardianDeviceClientTest` (5) — 12/12 pass

## M6 — ESP32 firmware interfaces (`device/guardian-device`)

- [x] `platformio.ini` — `esp32dev` env + `native` test env
- [x] `include/` interfaces: `ImuSensor`, `GnssProvider`, `CellularProvider`, `BleProvider`, `VehicleTelemetryProvider`, `BatteryProvider`, `EventStore`, `CrashDetector`
- [x] `config/crash_config.h` — all thresholds in one place
- [x] Mocks: `MockImu` (scenario-driven), `MockGnss`, `MockCellular` (Wi-Fi HTTP fallback), `MockVehicleTelemetry`, `MockBattery`
- [x] `GuardianDevice` orchestrator: sample loop → crash engine → event store → cellular, BLE notify (independent)
- [x] Serial commands for demo: `normal`, `minor`, `severe`, `cell off/on`, `status`
- [x] Native unit tests for crash detector + event queue (14 tests pass on host via clang; `pio test -e native` equivalent)
- [x] `pio run -e esp32dev` SUCCESS (RAM 20.6%, flash 62.6%) and `-e esp32dev-hw` SUCCESS (RAM 14.9%, flash 37.2%)
- [ ] Flash to a physical ESP32 (`pio run -e esp32dev -t upload`)

## M7 — BLE integration (Android ↔ ESP32)

- [~] ESP32 NimBLE GATT server with Guardian service + 8 characteristics
- [~] Android scan by service UUID, connect, MTU request, subscribe notifications, write COMMAND
- [~] Connection loss → auto-reconnect with backoff; UI shows `Phone: DISCONNECTED` on device side
- [ ] Verify on hardware: status updates, crash notify, `GET_HISTORY` replay after reconnect

## M8 — Real IMU + GNSS

- [~] `Mpu6050Imu` (I2C, ±16 g, ±2000 °/s, 100 Hz)
- [~] `NmeaGnss` (UART, TinyGPSPlus) with fix/accuracy (HDOP-based)
- [ ] Bench test: tap/shake test, GNSS outdoor fix, threshold tuning log

## M9 — Independent cellular path

- [~] `SimcomCellular` (SIM7600/A7670 via TinyGSM) — connect, heartbeat, crash POST, ACK parse
- [~] Event queue + exponential backoff retry; ACK on 201 or 200-duplicate
- [ ] Verify with SIM: phone OFF → crash reaches dashboard
- [ ] HTTPS (modem SSL) for deployed backend

## M10 — Read-only OBD-II

- [~] `ObdVehicleTelemetryProvider` (ELM327 UART/BLE, PID 0x0D speed, 0x0C RPM) — read-only, `speed = null` when unavailable
- [~] Crash engine works with and without vehicle speed
- [ ] Verify on a test vehicle (parked / controlled)

## M11 — Complete end-to-end demo

- [x] `simulator.py demo` scripted full flow (§23 steps 1–15) against running backend
- [x] Backend e2e test: phone-connected crash, phone-disconnected crash, offline queue exactly-once (`simulator/vehicle-simulator/tests/test_e2e_backend.py`)
- [x] `docs/demo.md` manual checklist
- [ ] Rehearse on hardware: ESP32 + iQOO + dashboard on projector

## M12 — Phone-primary capture + device black-box fallback

Phone is primary (richer sensors + on-device AI); the vehicle device is the **black box** that
preserves evidence and delivers the alert when the phone is gone. Backend dedup by `eventId`
keeps it to one incident. See [docs/blackbox.md](docs/blackbox.md).

### Backend (done)
- [x] `BlackBoxEvidence` model; `CrashEvent.alertSource` (PHONE|DEVICE) + `aiAssessment`
- [x] `POST`/`GET /api/crashes/{eventId}/evidence`; `POST /api/devices/{deviceId}/blackbox` (`X-Device-Secret`)
- [x] `POST /api/crashes` accepts `alertSource` + `aiAssessment`; dedup-based phone→device failover
- [x] Backend suite green (38 tests)

### Simulator (done)
- [x] `device.py`: on-device-AI assessment, evidence capture, BLE stream, `crash_with_phone(phone_can_send)`
- [x] CLI `phone_crash` (phone delivers) / `phone_dead_crash` (device black box delivers); woven into `demo`

### Dashboard (done)
- [x] `types.ts` evidence + `alertSource` + `aiAssessment`; black-box panel + AI callout + alert-source chip
- [x] `npm run build` passes

### Android (pending)
- [ ] Real on-device AI (Gemma/LLaMA), capture audio/image/sensor log
- [ ] Stream evidence to the device over BLE; phone-primary alert with device fallback

### ESP32 (pending)
- [ ] Receive/store black box over BLE; send the fallback alert when the phone didn't

## M13 — Phone-destroyed path on the device itself (next, with the IoT build)

When the phone is gone the occupant cannot see or cancel anything on the app, so the device must cover it:

- [ ] Buzzer + red/green LED: beeps during the 10 s verification window, steady red once help is alerted, green when a responder is on the way (cloud → device ACK carries status)
- [ ] Physical **I'M SAFE / cancel** button on the device (same as app CANCEL; long-press = manual SOS)
- [ ] Direct **SMS fallback** from the device's own SIM (`AT+CMGS` via TinyGSM) to 112 / family if the cloud cannot be reached after N retries
- [ ] Optional voice call (`ATD112;`) + speaker/mic for two-way talk (eCall-style)
- [ ] High-g accelerometer (ADXL375, ±200 g) alongside MPU6050 (±16 g saturates in hard crashes)

## M14 — Guardian cloud for production

- [ ] Real SMS / WhatsApp / voice gateway (replace simulated delivery)
- [ ] Nearest police station + hospital from GPS (instead of fixed 112 / 108)
- [ ] Alert-link expiry, HTTPS, deployed backend (Azure)
- [ ] Escalation: re-alert / call if nobody opens the alert within 60 s

---

## Acceptance criteria tracker (spec §29)

| Area | Criterion | Status |
|---|---|---|
| Backend | Starts locally / health / registration / heartbeat / persistence / dedup / WebSocket | M2 |
| Dashboard | Starts / incidents / location / severity / confidence / phone + device state / live | M3 |
| Android | Builds / screens / BLE mock / real BLE replaceable | M5, M7 (needs Android Studio) |
| Device | Firmware builds / IMU / GNSS / crash detector / BLE / cellular or mock / queue | M6–M9 (needs PlatformIO) |
| E2E | Simulated crash → backend → dashboard; phone disconnected still delivered; outage queue | M4, M11 |

## Out of scope (spec §24 of plan)

Real police/hospital APIs, certification, custom PCB, ML training, CAN control, insurance, fleet mgmt, iOS, complex auth.

---

## Verification log (2026-10-05)

| Check | Result |
|---|---|
| Backend `pytest` | 26 passed |
| Simulator unit + e2e `pytest` | 23 passed |
| Dashboard `npm run build` | OK; rendered live incidents incl. `phoneConnected=false` |
| Live HTTP demo (`simulator.py demo` → uvicorn) | phone-on crash, phone-off crash, outage → exactly-once, false positives — all OK |
| Firmware pure-C++ core (host clang) | 14/14 passed |
| ESP32 Arduino build (`pio run`) | esp32dev + esp32dev-hw both SUCCESS |
| Android unit tests + APK | 12/12 passed; APK built, runs on emulator: dashboard, history sync (3 backend incidents), SIMULATE SEVERE CRASH → emergency screen + notification, I'M SAFE |
| Live stack run | backend + dashboard + simulator heartbeat loop; phone-off crash shown live; outage → delivered once; ACKNOWLEDGE from dashboard UI |

## Verification log (2026-10-09)

| Check | Result |
|---|---|
| Backend `pytest` (after black-box feature) | 38 passed |
| Simulator `phone_crash` | `alertSource=PHONE`, 4 black-box records uploaded, phone delivered the alert |
| Simulator `phone_dead_crash` | phone offline → `alertSource=DEVICE`, device black box delivered alert + 4 records |
| Dashboard `npm run build` | OK; black-box panel (AI assessment, transcript, cabin photo, sensor log) + alert-source chip |
| Live API check | `GET /api/crashes` shows `alertSource` + `evidence[]`; `GET /api/crashes/{id}/evidence` returns records |

## Next actions for you (in order) — everything left needs physical hardware

1. iQOO: enable USB debugging → `adb install -r mobile/guardian-android/app/build/outputs/apk/debug/app-debug.apk` → Settings → Response center URL = `http://<laptop-LAN-IP>:8000`.
2. ESP32: edit `include/config/secrets.h` (Wi-Fi, backend IP, secret) → `pio run -e esp32dev -t upload` → `pio device monitor` → `severe_crash`.
3. Phone: disable mock device → Scan & connect to `Guardian-GD-001` (M7).
4. Wire MPU6050 + GNSS (M8), then SIM7600 (M9), then ELM327 (M10) following `docs/hardware.md`.
5. Rehearse `docs/demo.md` end-to-end (M11).
