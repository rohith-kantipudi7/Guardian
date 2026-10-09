# Guardian — Independent Vehicle Emergency Intelligence (POC)

> **"The phone is gone. The emergency signal isn't."**

Guardian is a vehicle-installed device that detects serious crashes and reports them to an
emergency response center **over its own cellular link — even when the driver's phone is
destroyed, off, or disconnected**. An iQOO Android app is the companion; it is never on the
emergency path.

> Hackathon prototype. Not automotive-certified. Emergency dispatch is **simulated**.
> Vehicle access is read-only.

```text
                 Guardian Device (ESP32)
        IMU · GNSS · LTE · backup battery · OBD-II (read-only)
                          │
            ┌─────────────┴─────────────┐
           BLE (companion)        Cellular (emergency path)
            │                           │
        iQOO App                 Guardian Backend (FastAPI)
                                        │ WebSocket
                              Emergency Dashboard (React)
```

## Repository

| Path | Component | Status |
|---|---|---|
| [backend/guardian-api](backend/guardian-api) | FastAPI + SQLAlchemy + WebSocket | runs, 26 tests |
| [dashboard/guardian-dashboard](dashboard/guardian-dashboard) | React + TS + Vite + MUI + Leaflet | builds |
| [simulator/vehicle-simulator](simulator/vehicle-simulator) | Virtual device + reference crash engine | runs, 23 tests incl. e2e |
| [device/guardian-device](device/guardian-device) | ESP32 firmware (PlatformIO) | core logic host-tested (14 tests); needs `pio run` |
| [mobile/guardian-android](mobile/guardian-android) | Kotlin + Compose companion app | needs Android Studio build |
| [docs](docs) | Architecture, protocols, hardware, demo, testing, security | |
| [TODO.md](TODO.md) | End-to-end milestone plan & status | |

## Quick start (no hardware)

```powershell
# 1. Backend
cd backend/guardian-api
python -m venv .venv; .\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

# 2. Dashboard (new terminal)
cd dashboard/guardian-dashboard
npm install
npm run dev                      # http://localhost:5173

# 3. Simulator = virtual Guardian device (new terminal)
cd simulator/vehicle-simulator
python simulator.py seed
python simulator.py severe_crash         # incident appears on the dashboard
python simulator.py phone_disconnect
python simulator.py severe_crash         # still appears: phoneConnected=false
python simulator.py demo --step          # full scripted judge demo

# 4. Showcase mode (no hardware, recommended for presentations)
python simulator.py reset
python simulator.py serve               # virtual device control panel on :8100
# open http://localhost:5173/showcase and click "Run step" 1..8
```

The **Showcase** page shows the three actors side by side: the virtual device (with the impact graph from the real crash engine), the driver's phone (same screens as the Android app, including I'M SAFE) and the responders. There is **no operator**: on every emergency the backend automatically alerts police (112), the nearest hospital / ambulance (108) and each family contact with an SMS containing full details and a personal link (`/alert/<token>`, no login). Responders just open the link (recorded as *seen*, no buttons); the driver closes the incident with I'M SAFE. **Live monitor** is a read-only view for the demo. Messages are simulated.

Or: `scripts/dev-start.ps1` (Windows) / `scripts/dev-start.sh`, then `scripts/seed-demo-data.*`.
Docker: `docker compose up --build` (backend :8000, dashboard :5173).

Android: see [mobile/guardian-android/README.md](mobile/guardian-android/README.md).
ESP32: see [device/guardian-device/README.md](device/guardian-device/README.md).

## Tests

```powershell
cd backend/guardian-api;          python -m pytest -q
cd simulator/vehicle-simulator;   ..\..\backend\guardian-api\.venv\Scripts\python -m pytest -q
cd dashboard/guardian-dashboard;  npm run build
cd device/guardian-device;        pio test -e native
cd mobile/guardian-android;       ./gradlew testDebugUnitTest
```

## Docs

[architecture](docs/architecture.md) · [api](docs/api.md) · [ble-protocol](docs/ble-protocol.md) ·
[device-protocol](docs/device-protocol.md) · [crash-detection](docs/crash-detection.md) ·
[blackbox](docs/blackbox.md) ·
[hardware](docs/hardware.md) · [demo](docs/demo.md) · [testing](docs/testing.md) · [security](docs/security.md) ·
[market & device build plan](docs/market-and-device-build.md)
