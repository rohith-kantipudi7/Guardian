# Guardian Vehicle / Device Simulator

A software twin of the Guardian ESP32 device. It generates synthetic sensor data, runs the
**same crash engine as the firmware**, persists events locally, and reports them to the
backend over a simulated cellular link — with no phone in the path.

Pure Python standard library; Python 3.11+.

## Commands

```powershell
python simulator.py seed               # create demo user USR-001, vehicle VH-001, device GD-001
python simulator.py normal             # 10 s normal driving -> no trigger
python simulator.py hard_brake         # 0.9 g stop -> no trigger
python simulator.py pothole            # single-sample spike -> FALSE_POSITIVE
python simulator.py minor_impact       # MINOR -> logged, no emergency
python simulator.py severe_crash       # CRITICAL -> emergency event to backend
python simulator.py rollover           # MODERATE -> 10 s verification -> emergency
python simulator.py rollover --cancel  # occupant presses I'M SAFE during verification
python simulator.py severe_crash --no-speed   # no OBD/GNSS speed available

python simulator.py phone_disconnect   # BLE companion gone (emergency path unaffected)
python simulator.py phone_connect
python simulator.py network_offline    # crash events are queued locally
python simulator.py network_delayed    # 3 s latency per request
python simulator.py network_restore    # flushes the queue (exactly-once)

python simulator.py heartbeat | flush | status | reset
python simulator.py run --interval 5   # continuous heartbeat + retry loop
python simulator.py demo [--step]      # full scripted judge demo (docs/demo.md)
```

State lives in `.sim_state.json` and `.sim_queue.json` (git-ignored), so commands from
different terminals share the same virtual device.

## Configuration

Environment variables (or `.env` here / at repo root): `BACKEND_URL`, `DEVICE_ID`,
`DEVICE_SECRET`, `VEHICLE_ID`.

Crash thresholds: copy `crash_config.example.json` to `crash_config.json` and edit.
Defaults are identical to `device/guardian-device/include/config/crash_config.h`.

## Files

| File | Role |
|---|---|
| `crash_engine.py` | Reference crash engine + state machine (mirrors firmware) |
| `scenarios.py` | Synthetic 100 Hz IMU + speed + GNSS scenarios |
| `event_store.py` | Persistent queue, backoff retry, ACK semantics |
| `network.py` | HTTP transport + ONLINE/OFFLINE/DELAYED simulator |
| `device.py` | Virtual Guardian device |
| `simulator.py` | CLI |

## Tests

```powershell
# unit tests only (stdlib + pytest)
python -m pytest -q
# + end-to-end against the in-process backend (uses the backend venv)
..\..\backend\guardian-api\.venv\Scripts\python -m pytest -q
```
