# Architecture

## Components

| Component | Tech | Role |
|---|---|---|
| Guardian Device | ESP32, IMU, GNSS, LTE, battery, OBD-II (read-only) | Detects crashes, stores events, reports over **its own** cellular link |
| Guardian App | Android, Kotlin, Compose | Companion: status, alerts, I'M SAFE, history, demo controls |
| Guardian Backend | FastAPI, SQLAlchemy, SQLite/PostgreSQL | Receives device events, dedups, runs simulated response workflow, broadcasts |
| Emergency Dashboard | React, MUI, React Query, Leaflet | Live incident view for the response center |
| Vehicle Simulator | Python stdlib | Software twin of the device for development and demo |

## Two independent paths

```mermaid
flowchart LR
  subgraph Vehicle
    IMU --> CE[Crash engine]
    GNSS --> CE
    OBD[OBD-II read-only] -.optional.-> CE
    CE --> Q[(Event queue<br/>flash)]
  end
  Q -- "EMERGENCY PATH<br/>LTE / HTTPS" --> API[Backend]
  CE -- "companion only<br/>BLE" --> APP[iQOO app]
  API -- WebSocket --> DASH[Dashboard]
  APP -. "history sync / I'M SAFE" .-> API
```

- **Emergency path:** `Device -> Cellular -> Backend -> Dashboard`. Never depends on the phone.
- **Companion path:** `Device <-> BLE <-> Phone`. Losing it changes nothing on the emergency path;
  events are flagged `phoneConnected=false`.

## Crash event lifecycle

```mermaid
sequenceDiagram
  participant D as Device
  participant P as Phone (optional)
  participant B as Backend
  participant R as Dashboard
  D->>D: IMU window -> confidence/severity
  D->>D: persist event (unique eventId)
  D-->>P: BLE CRASH_STATUS / EVENT (if connected)
  loop until ACK (exp. backoff)
    D->>B: POST /api/crashes (X-Device-Secret)
  end
  B->>B: dedup by eventId, status=EMERGENCY_ACTIVE, simulated workflow
  B-->>D: 201 accepted | 200 duplicate (both = ACK)
  B->>R: WS CRASH_DETECTED
  P->>B: POST /api/crashes/{id}/cancel (I'M SAFE, optional)
  B->>R: WS CRASH_UPDATED
```

## Device firmware structure

```text
core 1 loop():  IMU 100 Hz -> CrashDetector -> EventQueue      (never blocks on I/O)
core 0 task:    EventQueue -> CellularProvider -> backend; heartbeat; time sync
```

Every hardware interface (`ImuSensor`, `GnssProvider`, `CellularProvider`, `BleProvider`,
`VehicleTelemetryProvider`, `BatteryProvider`, `QueueStorage`) has a mock. The crash engine,
queue and scenario player are pure C++ and identical in behaviour to the Python simulator.

## Backend layers

`api/` (routers) → `services/` (device, crash state transitions + dedup, emergency workflow)
→ `repositories/` → `models/` (SQLAlchemy). `realtime/` fans events out to WebSocket clients.

## Android layers

`ui` → `GuardianViewModel` → `GuardianRepository` → (`SwitchableDeviceClient` [Mock | BLE],
`GuardianApi` [Retrofit], Room).

## Key design decisions

| Decision | Why |
|---|---|
| Device owns the emergency path | The phone is the most likely casualty of a crash |
| Persist before transmit; dedup on backend by `eventId` | Exactly-once delivery across outages and lost ACKs |
| Deterministic, configurable crash engine first | Explainable, testable; ML later (spec §35) |
| Robust k-th-largest peak | Rejects single-sample spikes (potholes, knocks) |
| Python reference engine mirrors C++ | Same behaviour testable without hardware |
| Simulated emergency workflow | No real dispatch without authorization (spec §36) |
| Phone-primary capture, device black-box fallback | Phone has richer sensors + on-device AI; the device guarantees delivery and keeps the evidence if the phone dies ([blackbox.md](blackbox.md)) |
