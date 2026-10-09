# Device ↔ Backend Protocol (independent network path)

Transport: HTTP/1.1 JSON over the device's own link — LTE modem in production, Wi-Fi
(`MockCellularProvider`) in dev builds, simulated network in the Python simulator.
Use HTTPS (`GUARDIAN_BACKEND_TLS=1`) for any deployed backend.

## Authentication

Every device request carries `X-Device-Secret: <secret>`. The secret is provisioned at flash
time (`include/config/secrets.h` / `DEVICE_SECRET`) and registered once via
`POST /api/devices/register`. Re-registration with a different secret is rejected (`401`).

## Messages

| When | Request |
|---|---|
| First connection | `POST /api/devices/register` |
| Every 10 s | `POST /api/devices/{deviceId}/heartbeat` |
| Crash (MODERATE/CRITICAL after verification) | `POST /api/crashes` |
| Black-box dump after a crash the phone didn't deliver | `POST /api/devices/{deviceId}/blackbox` |

Payloads: see [api.md](api.md).

## Delivery semantics (exactly-once at the backend)

1. Device creates the event with a unique `eventId`
   (`EVT-<deviceId>-<epoch>-<random>`) and **persists it to flash before sending**.
2. Network task sends pending events; result handling (shared by firmware and simulator):

| Response | Device action |
|---|---|
| `201` | ACK → mark SENT |
| `200` + `duplicate:true` | ACK (backend already has it; earlier ACK was lost) → mark SENT |
| No response / timeout / `5xx` / `429` / `408` | Retry with backoff 1, 2, 4, 8 … 60 s |
| Other `4xx` (`401`, `422`) | Permanent failure → mark FAILED, log |

3. The backend's unique `eventId` constraint guarantees one incident per event, no matter how
   many retries arrive.

Queue capacity: 16 events. Pending events are never evicted; oldest SENT/FAILED are recycled.

## Time

Event timestamps are UTC. The device sets its clock from GNSS, the modem's network time
(`AT+CCLK`), or NTP (Wi-Fi dev build). If no source is available yet, the timestamp is
uptime-based (1970 epoch) — the backend still accepts and orders by arrival.

## Phone independence

`phoneConnected` in the crash payload reports whether a phone was linked over BLE at the time
of the crash. It has **no influence** on whether or how the event is sent.

## Phone-primary + black-box fallback

The phone may be the *primary* capture + alert source when it survives (richer sensors +
on-device AI). It streams a copy of its evidence to the device over BLE. If the phone does **not**
deliver the alert, the device sends it over cellular (`alertSource=DEVICE`) and uploads the stored
evidence via `POST /api/devices/{deviceId}/blackbox`. The backend's unique `eventId` constraint
keeps it to one incident no matter who sends. See [blackbox.md](blackbox.md).
