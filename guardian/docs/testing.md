# Testing

| Suite | Command | Count | Covers |
|---|---|---|---|
| Backend | `cd backend/guardian-api; python -m pytest -q` | 26 | health, users, vehicles, device registration/secret, heartbeat (+auth, validation), crash validation, phone-disconnected acceptance, dedup, minor/normal handling, transitions, list filters, WebSocket broadcast |
| Simulator | `cd simulator/vehicle-simulator; python -m pytest -q` | 18 | crash engine on all scenarios (normal, hard brake, pothole, minor, severe, rollover, missing vehicle data, repeated impacts, cooldown, config), event store (offline queue, lost ACK, restart, backoff, 4xx/5xx) |
| End-to-end | same, with backend venv python | +5 | full demo flow incl. phone disconnected, outage exactly-once, false positives, verification cancel, I'M SAFE |
| Firmware core | `cd device/guardian-device; pio test -e native` | 14 | C++ crash engine on the same scenarios, event queue (offline, persistence, eviction, 4xx/5xx), response classification |
| Dashboard | `cd dashboard/guardian-dashboard; npm run build` | | type-check + bundle |
| Android | `cd mobile/guardian-android; ./gradlew testDebugUnitTest` | 12 | BLE payload parsing/encoding, UUIDs, mock device state machine (severe, minor, verification cancel, outage → delivery, disconnected) |

## Spec §28 coverage

| Area | Item | Where |
|---|---|---|
| Backend | registration / heartbeat / validation / dedup / ack / cancel / WebSocket | `backend/guardian-api/tests` |
| Crash engine | normal, hard braking, pothole, minor, severe, repeated, rotation, missing vehicle data | `simulator/.../tests/test_crash_engine.py`, `device/.../test/test_native` |
| Android | BLE connection/disconnect, event reception, emergency UI, cancellation, history | unit: parser + mock client; manual: [demo.md](demo.md) |
| Device | sensor interface, mock GPS/cellular, queue, retry, duplicate prevention, BLE status | native tests + bench checklist below |

## Hardware bench checklist

- [ ] `pio run -e esp32dev` builds; serial banner and `status` output
- [ ] MPU6050 detected; tapping the board → `SUSPECTED_IMPACT` → `FALSE_POSITIVE`
- [ ] `severe_crash` serial command → event queued → ACK from backend
- [ ] `cell off` → crash → queued; `cell on` → delivered once
- [ ] Power-cycle with a queued event → delivered after reboot (LittleFS)
- [ ] BLE: app shows live status; disconnect → device keeps reporting
- [ ] GNSS outdoor fix; clock set from satellites
- [ ] LTE: SIM registers, heartbeat visible on dashboard with phone off
- [ ] OBD-II: speed visible in app; unplugging adapter → `speed: null`, detection still works
