# Guardian — Copilot Build Specification

## Purpose

Build a working hackathon POC for **Guardian**, an independent vehicle crash-response system.

Guardian has four components:

1. **Guardian Android App** — runs on an iQOO Android phone.
2. **Guardian Vehicle Device** — ESP32-class embedded device installed in a vehicle.
3. **Guardian Backend** — FastAPI service receiving device events.
4. **Guardian Emergency Dashboard** — React web application showing incidents.

The defining capability is:

> **The phone is a companion, not a single point of failure. The physical vehicle device must be able to detect and report a serious crash independently when the phone is disconnected or unavailable.**

This repository must be developed as a POC suitable for hackathon idea submission and demonstration. It is not an automotive-certified safety product.

---

# 1. Product Goal

Demonstrate this end-to-end flow:

```text
                     VEHICLE
                        |
                        v
              +-------------------+
              | Guardian Device   |
              | ESP32              |
              | IMU                |
              | GNSS               |
              | Cellular           |
              | Backup Battery     |
              | OBD-II (optional)  |
              +---------+----------+
                        |
              +---------+----------+
              |                    |
             BLE                Cellular
              |                    |
              v                    v
        +-----------+       +-------------+
        | iQOO App  |       |   Backend   |
        +-----------+       +------+------+
                                   |
                                   v
                         +--------------------+
                         | Emergency         |
                         | Dashboard         |
                         +--------------------+
```

Critical demonstration:

```text
iQOO PHONE OFF / DISCONNECTED
             |
             X
             |
        Guardian Device
             |
          Cellular
             |
          Backend
             |
     Emergency Dashboard
```

---

# 2. Core Engineering Rules

## Phone independence

The emergency communication path must NOT depend on:

```text
Vehicle Device -> Bluetooth -> Phone -> Internet -> Backend
```

The primary emergency path must be:

```text
Vehicle Device -> Cellular -> Backend
```

BLE is for local companion functionality only.

### Phone-primary capture with device black-box fallback

The phone **may** act as the *primary* capture and alert source when it survives a crash (it has
the richer sensors and on-device AI). This does not weaken the rule above: the vehicle device
remains the **black box** that guarantees delivery. The phone streams a copy of its evidence to
the device over BLE, and if the phone fails to deliver the alert, the device sends it over its own
cellular link. Backend dedup by `eventId` keeps it to exactly one incident. See
[docs/blackbox.md](guardian/docs/blackbox.md).

## Hardware abstraction

Implement interfaces for:

- IMU
- GNSS
- Cellular
- BLE
- Vehicle telemetry
- Battery

Every hardware interface must have a real implementation where hardware exists and a mock/simulator implementation for local development.

The entire software system must run in simulator mode without physical hardware.

## Safety boundary

This is a prototype.

Do not:

- control brakes
- control steering
- modify airbags
- inject CAN commands
- claim automotive certification
- claim medically/police validated dispatch

OBD-II/CAN integration is read-only.

---

# 3. Repository Structure

Create:

```text
guardian/
├── README.md
├── docker-compose.yml
├── .gitignore
├── .env.example
├── mobile/
│   └── guardian-android/
├── device/
│   └── guardian-device/
├── backend/
│   └── guardian-api/
├── dashboard/
│   └── guardian-dashboard/
├── simulator/
│   └── vehicle-simulator/
├── docs/
│   ├── architecture.md
│   ├── api.md
│   ├── ble-protocol.md
│   ├── device-protocol.md
│   ├── hardware.md
│   ├── crash-detection.md
│   ├── demo.md
│   ├── testing.md
│   └── security.md
├── scripts/
│   ├── dev-start.*
│   └── seed-demo-data.*
└── .github/
    └── copilot-instructions.md
```

Keep each component independently runnable.

---

# 4. Technology Stack

## Android

- Kotlin
- Android Studio
- Jetpack Compose
- Kotlin Coroutines
- StateFlow/SharedFlow
- Android BLE APIs
- Room
- Retrofit or Ktor Client
- Android location APIs

Architecture:

```text
UI
 |
ViewModel
 |
Domain
 |
Repository
 |
BLE / REST / Local DB
```

## Device

- ESP32-class MCU
- PlatformIO
- C/C++

Interfaces:

```text
ImuSensor
GnssProvider
CellularProvider
BleProvider
VehicleTelemetryProvider
BatteryProvider
CrashDetector
EventStore
```

## Backend

- Python
- FastAPI
- Pydantic
- SQLAlchemy
- SQLite for local POC
- PostgreSQL-compatible schema
- WebSocket
- pytest

## Dashboard

- React
- TypeScript
- Vite
- MUI
- React Query
- WebSocket
- Map provider abstraction

## Simulator

Python simulator for:

- normal driving
- hard braking
- minor impact
- severe crash
- phone connected/disconnected
- cellular outage/recovery

---

# 5. Core Domain Model

## User

```text
User
- id
- name
- phone
- createdAt
```

## EmergencyContact

```text
EmergencyContact
- id
- userId
- name
- phone
- relationship
```

## Vehicle

```text
Vehicle
- id
- userId
- registrationNumber
- make
- model
- year
- deviceId
```

## Device

```text
Device
- id
- serialNumber
- firmwareVersion
- batteryLevel
- gpsStatus
- cellularStatus
- bleStatus
- vehicleConnected
- lastHeartbeat
- createdAt
```

## CrashEvent

```text
CrashEvent
- id
- deviceId
- vehicleId
- timestamp
- latitude
- longitude
- speed
- accelerationPeak
- rotationPeak
- confidence
- severity
- phoneConnected
- cellularConnected
- alertSource        (PHONE | DEVICE — who delivered the alert)
- aiAssessment       (on-device AI summary, optional)
- status
- createdAt
```

Severity:

```text
NORMAL
MINOR
MODERATE
CRITICAL
```

Status:

```text
DETECTED
VERIFYING
CANCELLED
EMERGENCY_ACTIVE
ACKNOWLEDGED
RESOLVED
```

## BlackBoxEvidence

Black-box record captured for a crash (phone-primary, with a device-held copy).

```text
BlackBoxEvidence
- id
- crashId
- kind           (AUDIO | IMAGE | SENSOR_LOG | TRANSCRIPT | AI_ASSESSMENT)
- source         (PHONE | DEVICE)
- label
- contentText    (inline text: transcript / sensor-log excerpt)
- mediaUrl
- mediaMime
- capturedAt
- storedBy        (black-box device id holding the copy)
- createdAt
```

AlertSource / EvidenceSource:

```text
PHONE
DEVICE
```

---

# 6. Backend API

Implement:

```text
GET    /health

POST   /api/users
GET    /api/users/{userId}

POST   /api/vehicles
GET    /api/vehicles/{vehicleId}

POST   /api/devices/register
GET    /api/devices/{deviceId}
POST   /api/devices/{deviceId}/heartbeat

POST   /api/crashes
GET    /api/crashes
GET    /api/crashes/{eventId}

POST   /api/crashes/{eventId}/acknowledge
POST   /api/crashes/{eventId}/cancel
POST   /api/crashes/{eventId}/resolve

POST   /api/crashes/{eventId}/evidence
GET    /api/crashes/{eventId}/evidence
POST   /api/devices/{deviceId}/blackbox

WS     /ws/emergency
```

Crash request:

```json
{
  "eventId": "EVT-001",
  "deviceId": "GD-001",
  "vehicleId": "VH-001",
  "timestamp": "2026-10-05T10:30:00Z",
  "latitude": 12.9716,
  "longitude": 77.5946,
  "speed": 72.0,
  "accelerationPeak": 8.4,
  "rotationPeak": 4.2,
  "confidence": 0.96,
  "severity": "CRITICAL",
  "phoneConnected": false,
  "cellularConnected": true
}
```

Response:

```json
{
  "accepted": true,
  "eventId": "EVT-001",
  "status": "EMERGENCY_ACTIVE"
}
```

---

# 7. Device Heartbeat

The physical device periodically sends:

```json
{
  "deviceId": "GD-001",
  "timestamp": "2026-10-05T10:30:00Z",
  "batteryLevel": 91,
  "gpsStatus": "AVAILABLE",
  "cellularStatus": "CONNECTED",
  "vehicleConnected": true
}
```

Backend stores latest device state.

---

# 8. WebSocket Emergency Events

When a crash is created, broadcast:

```json
{
  "type": "CRASH_DETECTED",
  "event": {
    "eventId": "EVT-001",
    "vehicleId": "VH-001",
    "severity": "CRITICAL",
    "confidence": 0.96,
    "latitude": 12.9716,
    "longitude": 77.5946,
    "phoneConnected": false
  }
}
```

Dashboard must update without refresh.

---

# 9. Android App

Create:

## Dashboard

Display:

```text
GUARDIAN

Vehicle: My Vehicle
Device: GD-001

Device Status: ONLINE
BLE: CONNECTED
GPS: AVAILABLE
Cellular: CONNECTED
Battery: 91%

Crash Protection
ACTIVE
```

## Emergency

```text
CRASH DETECTED

Severity: CRITICAL
Confidence: 96%
Location: Available

Emergency response: ACTIVATING...

[ I'M SAFE ]
```

The button calls:

```text
POST /api/crashes/{eventId}/cancel
```

## Device

Display:

- Device ID
- Firmware
- BLE
- GPS
- Cellular
- Battery
- Last heartbeat
- Vehicle connection

## History

Display:

- Date
- Time
- Severity
- Location
- Status

## Settings

Include:

- Vehicle information
- Emergency contacts
- Device pairing
- Demo mode
- Notifications

---

# 10. Android BLE

Define one Guardian BLE service with characteristics:

```text
DEVICE_STATUS
BATTERY
GPS_STATUS
CELLULAR_STATUS
VEHICLE_STATUS
CRASH_STATUS
EVENT
COMMAND
```

Support:

```text
Scan
Connect
Disconnect
Read
Write
Subscribe
```

BLE disconnect must not stop emergency communication.

Example state:

```text
Phone: DISCONNECTED
Device: ONLINE
Cellular: CONNECTED
Emergency path: ACTIVE
```

---

# 11. ESP32 Firmware

Structure:

```text
device/guardian-device/
├── platformio.ini
├── include/
├── src/
│   ├── main.cpp
│   ├── config/
│   ├── sensors/
│   │   ├── imu/
│   │   └── gnss/
│   ├── crash/
│   ├── communication/
│   │   ├── ble/
│   │   └── cellular/
│   ├── vehicle/
│   ├── storage/
│   └── power/
└── test/
```

Create interfaces:

```cpp
class ImuSensor {
public:
    virtual SensorSample read() = 0;
};

class GnssProvider {
public:
    virtual Location getLocation() = 0;
};

class CellularProvider {
public:
    virtual bool isConnected() = 0;
    virtual bool sendCrashEvent(const CrashEvent& event) = 0;
};

class VehicleTelemetryProvider {
public:
    virtual VehicleTelemetry read() = 0;
};

class BleProvider {
public:
    virtual void start() = 0;
    virtual void notify(const DeviceStatus& status) = 0;
};
```

Use mocks when hardware is unavailable.

---

# 12. Sensor Processing

IMU sample:

```json
{
  "timestamp": 123456,
  "ax": 0.2,
  "ay": -1.1,
  "az": 9.8,
  "gx": 0.1,
  "gy": 0.4,
  "gz": 0.2
}
```

Calculate:

```text
accelerationMagnitude
rotationMagnitude
```

Use multiple axes and a short signal window rather than a single raw reading.

---

# 13. Crash Detection

First implementation must be deterministic and configurable.

Pipeline:

```text
IMU
 |
Filtering
 |
Feature extraction
 |
+-- acceleration peak
+-- deceleration
+-- rotation peak
+-- repeated impact
+-- vehicle speed
 |
Crash score
 |
Severity
```

Config:

```yaml
crash_detection:
  impact_threshold: configurable
  severe_deceleration_threshold: configurable
  rotation_threshold: configurable
  critical_confidence: 0.85
  verification_window_ms: configurable
```

Do not scatter thresholds throughout the code.

Do not claim the algorithm is automotive-certified.

---

# 14. Crash State Machine

Implement:

```text
NORMAL
  |
  v
SUSPECTED_IMPACT
  |
  v
ANALYZING
  |
  +----------------+
  |                |
  v                v
FALSE_POSITIVE   PROBABLE_CRASH
                     |
                     v
                 VERIFYING
                     |
              +------+------+
              |             |
              v             v
          CANCELLED    EMERGENCY_ACTIVE
                            |
                            v
                     TRANSMIT_EVENT
                            |
                            v
                      WAIT_FOR_ACK
```

Prevent duplicate events from the same impact.

---

# 15. Local Event Store

If cellular is unavailable:

```text
Crash
 |
Create event
 |
Persist
 |
Retry
 |
Network available
 |
Send
 |
Server ACK
 |
Mark transmitted
```

Every event needs a unique ID.

Ensure exactly-once behavior at the backend through event ID deduplication.

---

# 16. Cellular

Create:

```text
CellularProvider
```

Methods:

```text
connect()
disconnect()
isConnected()
sendHeartbeat()
sendCrashEvent()
```

Use HTTPS where supported.

If cellular hardware is unavailable, provide:

```text
MockCellularProvider
```

with the same interface.

Do not block software development on hardware availability.

---

# 17. GNSS

Create:

```text
GnssProvider
```

Methods:

```text
getLocation()
hasFix()
getAccuracy()
```

Store:

```text
latitude
longitude
accuracy
timestamp
```

Use simulator coordinates indoors when required.

---

# 18. Vehicle Integration

Create:

```text
VehicleTelemetryProvider
```

Implement:

```text
MockVehicleTelemetryProvider
ObdVehicleTelemetryProvider
```

Initially use mock telemetry.

Later connect the real OBD-II adapter.

OBD-II is optional for the core crash event.

---

# 19. OBD-II

Use read-only access.

Potential data:

- vehicle speed
- available engine/vehicle telemetry
- diagnostic information where appropriate

Do not send control commands to the vehicle.

Do not assume every vehicle exposes identical signals.

If speed is unavailable:

```text
vehicleSpeed = null
```

Crash detection must still operate.

---

# 20. Vehicle Simulator

Create a Python simulator supporting:

```bash
python simulator.py normal
python simulator.py hard_brake
python simulator.py minor_impact
python simulator.py severe_crash
python simulator.py phone_disconnect
python simulator.py network_offline
python simulator.py network_restore
```

The simulator should generate synthetic but internally consistent sensor and vehicle data.

---

# 21. Dashboard

Use React + TypeScript + Vite + MUI.

Pages:

```text
/
 /incidents
 /incidents/:id
 /devices
 /vehicles
```

Main view:

```text
GUARDIAN EMERGENCY RESPONSE CENTER

Active incidents: 1

CRITICAL INCIDENT

Vehicle: VH-001
Device: GD-001

Severity: CRITICAL
Confidence: 96%

Phone: DISCONNECTED
Device: ONLINE
Cellular: CONNECTED
GPS: AVAILABLE

Location:
12.9716, 77.5946

Response:
EMERGENCY ACTIVE
```

Display incident on a map.

Use a map abstraction.

---

# 22. Dashboard Incident Detail

Display:

- Incident ID
- Vehicle
- Device
- Timestamp
- Location
- Speed
- Acceleration peak
- Rotation peak
- Confidence
- Severity
- Phone connection
- Cellular connection
- Response status

Buttons:

```text
ACKNOWLEDGE
RESOLVE
```

---

# 23. Demo Mode

The complete demonstration must work without physically crashing a vehicle.

Sequence:

```text
1. Start backend.
2. Start dashboard.
3. Start Android app.
4. Start device simulator.
5. Connect phone to simulated device.
6. Show normal status.
7. Trigger severe crash.
8. Device creates event.
9. Phone displays alert.
10. Dashboard displays incident.
11. Disconnect phone.
12. Trigger another severe crash.
13. Device sends event directly.
14. Dashboard receives event.
15. Event shows phoneConnected=false.
```

---

# 24. Android Developer Demo Controls

Create a developer-only screen:

```text
SIMULATE NORMAL
SIMULATE MINOR IMPACT
SIMULATE SEVERE CRASH

FORCE PHONE DISCONNECTED

FORCE CELLULAR OFF

RESTORE CELLULAR
```

Clearly mark these as test/demo controls.

---

# 25. End-to-End Scenarios

## Normal

```text
Device -> heartbeat -> backend
Phone -> BLE -> device
Dashboard -> healthy
```

## Crash with phone

```text
Impact
 |
Device
 |
BLE
 |
Phone alert

Device
 |
Cellular
 |
Backend
 |
Dashboard
```

Expected:

```text
CRITICAL INCIDENT
phoneConnected=true
```

## Crash without phone

```text
Phone OFF

Impact
 |
Device
 |
Cellular
 |
Backend
 |
Dashboard
```

Expected:

```text
CRITICAL INCIDENT
phoneConnected=false
deviceConnected=true
```

This is the primary acceptance test.

## Network outage

```text
Crash
 |
Cellular unavailable
 |
Local queue
 |
Network restored
 |
Transmit
 |
Backend ACK
```

Expected: event eventually reaches backend exactly once.

## False positive

```text
Minor event
 |
Crash engine
 |
Low confidence
 |
No emergency
```

Expected: no emergency incident.

---

# 26. Security

Implement basic POC security:

- Device ID + device secret
- No secrets committed to Git
- TLS for deployed communication
- Request validation
- Event ID deduplication
- Authenticated device requests
- Secure BLE pairing where supported
- Minimal location retention

Use `.env` for local secrets.

---

# 27. Configuration

Create `.env.example`:

```text
BACKEND_URL=http://localhost:8000
DATABASE_URL=sqlite:///./guardian.db
WS_URL=ws://localhost:8000/ws/emergency
MAP_PROVIDER=
DEVICE_ID=GD-001
VEHICLE_ID=VH-001
DEMO_MODE=true
```

---

# 28. Testing

## Backend

Test:

- device registration
- vehicle registration
- heartbeat
- crash validation
- duplicate event rejection
- acknowledgement
- cancellation
- WebSocket broadcast

## Crash engine

Test:

- normal driving
- hard braking
- pothole-like event
- minor impact
- severe impact
- repeated impacts
- rotation event
- missing vehicle data

## Android

Test:

- BLE connection
- BLE disconnect
- event reception
- emergency UI
- cancellation
- history

## Device

Test:

- sensor interface
- mock GPS
- mock cellular
- event queue
- retry
- duplicate prevention
- BLE status

---

# 29. Acceptance Criteria

## Backend

- [ ] Starts locally
- [ ] Health endpoint works
- [ ] Device registration works
- [ ] Vehicle registration works
- [ ] Heartbeat works
- [ ] Crash events persist
- [ ] Duplicate events rejected
- [ ] WebSocket events work

## Dashboard

- [ ] Starts locally
- [ ] Active incidents display
- [ ] Location displays
- [ ] Severity displays
- [ ] Confidence displays
- [ ] Phone state displays
- [ ] Device state displays
- [ ] New crashes appear without refresh

## Android

- [ ] Builds
- [ ] Dashboard works
- [ ] Device screen works
- [ ] Emergency screen works
- [ ] History works
- [ ] BLE mock works
- [ ] Real BLE can replace mock

## Device

- [ ] Firmware builds
- [ ] IMU interface works
- [ ] GNSS interface works
- [ ] Crash detector works
- [ ] BLE works
- [ ] Cellular works or has mock
- [ ] Local event queue works

## End-to-end

- [ ] Crash can be simulated
- [ ] Device creates event
- [ ] Backend receives event
- [ ] Dashboard displays event
- [ ] Phone displays event
- [ ] Phone can be disconnected
- [ ] Device still sends event
- [ ] Dashboard still receives event
- [ ] Network outage queue works

---

# 30. Milestone Order

Do not implement everything at once.

## Milestone 1

Repository structure + docs.

## Milestone 2

FastAPI backend + tests.

## Milestone 3

React dashboard + WebSocket.

## Milestone 4

Vehicle/device simulator.

## Milestone 5

Android app using mock device.

## Milestone 6

ESP32 firmware interfaces.

## Milestone 7

BLE integration.

## Milestone 8

Real IMU + GNSS.

## Milestone 9

Independent cellular path.

## Milestone 10

Read-only OBD-II.

## Milestone 11

Complete end-to-end demo.

At every milestone:

1. Inspect existing code.
2. Implement only that milestone.
3. Run tests/build.
4. Fix failures.
5. Update docs.
6. Report files changed and remaining issues.
7. Keep the project runnable.

Do not move forward if the current milestone is broken.

---

# 31. Copilot Coding Rules

1. Prefer working POC code over speculative production infrastructure.
2. Keep hardware-specific code isolated.
3. Use mocks whenever hardware is unavailable.
4. Never make the phone mandatory for emergency transmission.
5. Never hard-code credentials.
6. Never implement vehicle control.
7. Keep OBD/CAN read-only.
8. Write tests for important logic.
9. Update documentation when architecture changes.
10. Do not silently change API contracts.
11. Keep changes small and reviewable.
12. Avoid unnecessary dependencies.
13. Do not delete working functionality to add a new feature.
14. Preserve simulator mode.
15. Keep the project runnable after each milestone.

---

# 32. Development Commands

Root README must document commands similar to:

```bash
# Backend
cd backend/guardian-api
uvicorn app.main:app --reload

# Dashboard
cd dashboard/guardian-dashboard
npm install
npm run dev

# Simulator
cd simulator/vehicle-simulator
python simulator.py severe_crash
```

Android and ESP32 instructions must be documented separately.

---

# 33. Hardware POC

Initial hardware:

```text
ESP32
 |
 +-- IMU
 |
 +-- GNSS
 |
 +-- LTE modem
 |
 +-- Battery
 |
 +-- BLE
 |
 +-- OBD-II interface (later)
```

Development sequence:

```text
ESP32
 |
 +-- IMU
 |
 +-- BLE
 |
 +-- GNSS
 |
 +-- Cellular
 |
 +-- OBD-II
```

Do not wait for all hardware before building the software.

---

# 34. Vehicle Installation POC

For demonstration:

- Mount the device securely.
- Power it from a safe vehicle power source.
- Add backup battery.
- Do not interfere with safety systems.
- Do not modify brake, steering, or airbag systems.
- Use controlled sensor testing instead of a real crash.

---

# 35. AI Roadmap

Do not make cloud AI mandatory for the first crash detector.

Phase 1:

```text
Sensor fusion + deterministic rules
```

Phase 2:

```text
ML severity classifier
```

Phase 3:

```text
Advanced contextual reasoning
```

Potential inputs:

```text
IMU
GPS
Vehicle telemetry
Impact pattern
Speed
Rotation
Repeated impacts
```

Potential output:

```json
{
  "confidence": 0.96,
  "severity": "CRITICAL",
  "reasons": [
    "high_deceleration",
    "rapid_rotation",
    "vehicle_speed_before_event"
  ]
}
```

---

# 36. Emergency Services Boundary

For the POC, create a simulated emergency workflow.

Do not claim that the prototype directly dispatches real police or hospitals.

Use:

```text
Guardian
 |
 v
Emergency Response Center
 |
 +-- Simulated Police Workflow
 +-- Simulated Hospital Workflow
 +-- Emergency Contact Workflow
```

Real production integrations require authorized APIs, agreements, security review, and appropriate regulatory/safety validation.

---

# 37. Final Demo Story

Opening:

> "What happens when your phone is the first thing destroyed in an accident?"

Show:

```text
iQOO Phone
    |
    BLE
    |
Guardian Vehicle Device
    |
    +-- IMU
    +-- GPS
    +-- Cellular
    +-- Vehicle data
```

Trigger a controlled severe-crash simulation.

Show:

```text
CRASH DETECTED
Confidence: 96%
Severity: CRITICAL
```

Show phone alert.

Then disconnect the phone.

Trigger another event.

Show:

```text
PHONE: DISCONNECTED
DEVICE: ONLINE
CELLULAR: CONNECTED
```

Show the emergency dashboard receiving the event.

Final line:

> **"The phone is gone. The emergency signal isn't."**

---

# 38. Product Positioning

Do not describe Guardian as merely:

> "An accident detection app."

Describe it as:

> **An independent vehicle emergency intelligence system that detects serious crashes and communicates the incident even when the driver's smartphone is unavailable.**

Four layers:

```text
1. Vehicle Intelligence
   Detect the crash.

2. Independent Connectivity
   Report it without the phone.

3. Companion Intelligence
   iQOO app provides user interaction.

4. Emergency Coordination
   Cloud + response dashboard handles the event.
```

---

# 39. Final Definition of Done

The POC is ready when a judge can see:

```text
                NORMAL
                  |
                  v
        iQOO + Vehicle Device
                  |
                  v
          Device monitoring
                  |
                  v
          SIMULATED CRASH
                  |
                  v
          Crash classification
                  |
                  v
       Emergency event generated
                  |
          +-------+-------+
          |               |
          v               v
      iQOO App        Cloud Backend
                          |
                          v
                 Emergency Dashboard
                          |
                          v
                  INCIDENT ACTIVE


              SECOND TEST
                  |
                  v
             PHONE OFF
                  |
                  v
          Vehicle Device
                  |
              Cellular
                  |
                  v
             Cloud Backend
                  |
                  v
         Emergency Dashboard
                  |
                  v
       INCIDENT STILL ACTIVE
```

The second test is the core proof of the Guardian concept.

---

# 40. Final Pitch

> **Guardian is a vehicle-installed emergency intelligence device that independently detects serious crashes and communicates the vehicle's location even when the driver's smartphone is destroyed, disconnected, or unavailable — with an iQOO companion app and cloud emergency platform completing the response.**

---

# 41. Phone-Primary Capture + Device Black-Box Fallback

Extends the core concept: the phone is the **primary** capture + alert source when it survives,
and the vehicle device is the **black box** that preserves evidence and guarantees delivery when
the phone is gone. Exactly one incident is created (backend dedup by `eventId`).

## Behaviour

```text
CRASH
  |
  +-- Phone: on-device AI assessment + capture (audio / image / sensor log)
  |     |
  |     +-- BLE stream a copy to the device (black box)
  |     |
  |     +-- if reachable: phone POSTs the alert (alertSource=PHONE) + evidence
  |
  +-- Device (black box): if the phone did NOT deliver,
        send the alert over cellular (alertSource=DEVICE) + upload stored evidence
```

## On-device AI

The phone runs a small on-device LLM (Gemma / LLaMA class) to produce `aiAssessment` from the
crash signals + in-cabin audio: severity, likely mechanism, occupant state, recommended response.
It runs offline and is never on the critical delivery path — the alert still goes out with raw
crash data if AI is unavailable.

## API additions

```text
POST /api/crashes/{eventId}/evidence   # phone attaches one black-box record
GET  /api/crashes/{eventId}/evidence   # list black-box records
POST /api/devices/{deviceId}/blackbox  # device batch-dumps stored evidence (X-Device-Secret)
```

`POST /api/crashes` also accepts `alertSource` (default `DEVICE`) and `aiAssessment`.

## Simulator

```bash
python simulator.py phone_crash        # phone primary: AI + black box, PHONE delivers the alert
python simulator.py phone_dead_crash   # phone destroyed: DEVICE black box delivers alert + evidence
```

## Status

- Backend, simulator, and dashboard: DONE and verified (38 backend tests pass).
- Android (real on-device AI + BLE evidence transfer) and ESP32 (store + fallback): pending.

Full detail: [docs/blackbox.md](guardian/docs/blackbox.md).
