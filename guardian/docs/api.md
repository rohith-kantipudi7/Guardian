# Backend API

Base URL: `http://<host>:8000`. JSON uses **camelCase**. Timestamps are ISO-8601 UTC
(`2026-10-05T10:30:00.000Z`). OpenAPI UI at `/docs`.

Errors: `{"detail": "..."}` with `401` (bad device credentials), `404`, `409` (conflict /
invalid state transition), `422` (validation).

## Health

`GET /health` → `{"status":"ok","version":"0.1.0","demoMode":true,"websocketClients":1}`

## Users

`POST /api/users`

```json
{"id":"USR-001","name":"Demo Driver","phone":"+910000000000",
 "emergencyContacts":[{"name":"Asha","phone":"+918888888888","relationship":"Spouse"}]}
```

`201` → user with contacts. `409` if the id exists. `GET /api/users/{userId}`.

## Vehicles

`POST /api/vehicles` (upsert; `201` created / `200` updated)

```json
{"id":"VH-001","userId":"USR-001","registrationNumber":"KA01GD0001","make":"Demo","model":"Car","year":2024,"deviceId":"GD-001"}
```

`GET /api/vehicles`, `GET /api/vehicles/{vehicleId}`.

## Devices

`POST /api/devices/register` (`201` new / `200` re-registration with the same secret / `401` different secret)

```json
{"deviceId":"GD-001","deviceSecret":"<secret>","firmwareVersion":"0.1.0","vehicleId":"VH-001","serialNumber":"..."}
```

The secret is stored as a salted HMAC-SHA256 hash and never returned.

`GET /api/devices`, `GET /api/devices/{deviceId}` → includes computed `online`
(heartbeat newer than `DEVICE_OFFLINE_AFTER_SECONDS`).

`POST /api/devices/{deviceId}/heartbeat` — header `X-Device-Secret` required.

```json
{"deviceId":"GD-001","timestamp":"2026-10-05T10:30:00Z","batteryLevel":91,"gpsStatus":"AVAILABLE",
 "cellularStatus":"CONNECTED","bleStatus":"CONNECTED","phoneConnected":true,"vehicleConnected":true,
 "latitude":12.9716,"longitude":77.5946,"firmwareVersion":"0.1.0"}
```

All fields except `deviceId`/`timestamp` are optional. Enums: `gpsStatus` AVAILABLE|NO_FIX|UNAVAILABLE,
`cellularStatus` CONNECTED|CONNECTING|DISCONNECTED|UNAVAILABLE, `bleStatus` CONNECTED|ADVERTISING|DISCONNECTED.

## Crashes

`POST /api/crashes` — header `X-Device-Secret` required.

```json
{"eventId":"EVT-001","deviceId":"GD-001","vehicleId":"VH-001","timestamp":"2026-10-05T10:30:00Z",
 "latitude":12.9716,"longitude":77.5946,"speed":72.0,"accelerationPeak":8.4,"rotationPeak":4.2,
 "confidence":0.96,"severity":"CRITICAL","phoneConnected":false,"cellularConnected":true}
```

| Field | Rule |
|---|---|
| `eventId` | `^[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}$`, globally unique, generated on the device |
| `latitude` / `longitude` | [-90, 90] / [-180, 180], optional (no GNSS fix) |
| `speed` | km/h, optional (no OBD/GNSS speed) |
| `accelerationPeak` | g, `rotationPeak` rad/s |
| `confidence` | [0, 1] |
| `severity` | MINOR / MODERATE / CRITICAL (NORMAL → 422) |
| `phoneConnected` | `false` is a normal, fully accepted case |
| `vehicleId` | optional; defaults to the device's vehicle |
| `alertSource` | `PHONE` or `DEVICE` (default `DEVICE`); who delivered the alert |
| `aiAssessment` | optional on-device AI situation summary |

Responses:

| Case | Code | Body |
|---|---|---|
| New event | `201` | `{"accepted":true,"duplicate":false,"eventId":"EVT-001","status":"EMERGENCY_ACTIVE"}` |
| Duplicate `eventId` (retry) | `200` | `{"accepted":false,"duplicate":true,"eventId":"EVT-001","status":"<current>"}` |

Devices treat **both 201 and 200 as ACK**. Duplicates are never stored or broadcast twice.

Initial status: MODERATE/CRITICAL → `EMERGENCY_ACTIVE` (simulated police/hospital/contact
workflow recorded in `actions`); MINOR → `DETECTED`.

`GET /api/crashes?status=&deviceId=&vehicleId=&active=true|false&limit=100` (newest first)

`GET /api/crashes/{eventId}` → full event + `actions[]`.

### Transitions

| Endpoint | Allowed from | To |
|---|---|---|
| `POST /api/crashes/{id}/acknowledge` | DETECTED, VERIFYING, EMERGENCY_ACTIVE | ACKNOWLEDGED |
| `POST /api/crashes/{id}/cancel` (I'M SAFE) | DETECTED, VERIFYING, EMERGENCY_ACTIVE, ACKNOWLEDGED | CANCELLED |
| `POST /api/crashes/{id}/resolve` | DETECTED, EMERGENCY_ACTIVE, ACKNOWLEDGED | RESOLVED |

Repeating the same transition is idempotent (`200`); other transitions → `409`.

All three accept an optional body `{"operator": "Asha (ERC)", "note": "..."}`. The operator is stored as
`acknowledgedBy` / `closedBy` (with `acknowledgedAt` / `closedAt`, `resolution`) and on the timeline entry.

### Responder alerts (no login)

On every `EMERGENCY_ACTIVE` crash the backend creates one notification per responder — police
(`POLICE_DESK_NAME`/`POLICE_DESK_PHONE`, default 112), hospital (`HOSPITAL_DESK_*`, default 108) and each emergency
contact — with an SMS-style message (severity, time, Google Maps link, vehicle, driver, crash data, phone-not-reachable
flag) and a link `ALERT_LINK_BASE/<token>`. The token is 144-bit random and opens only that incident. Delivery is
SIMULATED. `CrashOut.notifications[]` lists them with `openedAt`, `response`, `respondedAt`.

- `GET /api/alerts/{token}` → `AlertView` (full incident details, driver + contacts, who else has seen it).
  The first open records an `OPENED` read receipt; the first open of an `EMERGENCY_ACTIVE` incident moves it to
  `ACKNOWLEDGED` (`acknowledgedBy` = that recipient). Responders never have to click anything.
- Incidents are closed by the driver: `POST /api/crashes/{id}/cancel` (I'M SAFE in the app, later the device button).

### Operator actions (optional, not used by the demo UI)

`POST /api/crashes/{id}/actions` → `201`

```json
{"kind": "AMBULANCE_DISPATCHED", "operator": "Asha (ERC)", "message": "optional"}
```

`kind`: `CALLED_DRIVER`, `CALLED_EMERGENCY_CONTACT`, `AMBULANCE_DISPATCHED`, `POLICE_NOTIFIED`, `TOW_REQUESTED`, `NOTE`.
Returns `409` if the incident is already closed. Dispatch actions are always labelled `[SIMULATED]`.

### Black-box evidence (phone-primary + device fallback)

The phone is the primary capture + alert source when it survives; the vehicle device is the black
box that keeps a copy and delivers the alert if the phone is gone. See [blackbox.md](blackbox.md).

`POST /api/crashes/{eventId}/evidence` — attach one black-box record (phone/companion; no auth). `201` → full crash.

```json
{"kind":"TRANSCRIPT","source":"PHONE","label":"Audio transcript","contentText":"...",
 "mediaUrl":null,"mediaMime":null,"capturedAt":"2026-10-09T10:30:00Z","storedBy":null}
```

`kind`: `AUDIO` | `IMAGE` | `SENSOR_LOG` | `TRANSCRIPT` | `AI_ASSESSMENT`; `source`: `PHONE` | `DEVICE`.

`GET /api/crashes/{eventId}/evidence` → `evidence[]` (same records).

`POST /api/devices/{deviceId}/blackbox` — header `X-Device-Secret` required. The device batch-dumps
the evidence it stored for a crash. `201` → full crash.

```json
{"eventId":"EVT-001","evidence":[{"kind":"IMAGE","source":"DEVICE","label":"Cabin photo","mediaUrl":"...","mediaMime":"image/svg+xml"}]}
```

`CrashOut` (`GET /api/crashes/{id}`, WebSocket payloads) now also includes `alertSource`,
`aiAssessment`, and `evidence[]`.

### Stats and demo

- `GET /api/stats` → open/critical counts, average time to acknowledge, devices online, incidents reported without phone.
- `POST /api/demo/reset` → deletes all incidents (only when demo mode is enabled); broadcasts `DEMO_RESET`.

## WebSocket `/ws/emergency`

Server → client messages:

```json
{"type":"CONNECTED"}
{"type":"CRASH_DETECTED","event":{ /* full crash event as in GET /api/crashes/{id} */ }}
{"type":"CRASH_UPDATED","event":{ ... }}
{"type":"DEVICE_HEARTBEAT","device":{ /* as GET /api/devices/{id} */ }}
{"type":"DEVICE_REGISTERED","device":{ ... }}
{"type":"PONG"}
```

Client may send `ping` as keep-alive.
