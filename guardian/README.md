# Guardian — Mobile‑Primary Vehicle Crash Response (POC)

> **"Your phone is the black box. The IoT device is the backup that survives the crash."**

Guardian turns the driver's **phone into the primary crash responder**: it detects a serious impact
with its own sensors, **automatically** opens the **dual camera** (cabin + road), records the
**in‑cabin voice before and after impact**, runs **AI** on the scene, sounds a **loud siren**, and
alerts responders. A vehicle‑installed **ESP32 IoT device is the backup black box** — the phone
hands it the whole evidence bundle over Bluetooth and the device **buzzes** locally, so the record
survives even if the phone is destroyed.

> Hackathon prototype. Not automotive‑certified. Emergency dispatch is **simulated**.
> Vehicle access is read‑only.

```text
                         CRASH
                           │
   ┌───────────────────────┴───────────────────────────────┐
   │  PHONE  (PRIMARY)                                       │
   │  IMU crash detect → dual cam (cabin+road)              │
   │  + voice (before+after) → AI → LOUD SIREN              │
   │        │                         │                      │
   │        │ Cloud AI (primary)      │ black‑box bundle     │
   │        ▼  fallback: on‑device    ▼ over BLE             │
   │  Guardian Backend (FastAPI) ◀── responders        ESP32 IoT (BACKUP)
   │        │  WebSocket                                 stores bundle + BUZZES
   │        ▼                                                 │
   │  Emergency Dashboard (React)                             │
   └─────────────────────────────────────────────────────────┘
         Alert is delivered by the phone, or by the IoT device if the phone is gone.
```

### How it works
1. **Detect (phone).** The phone's accelerometer + gyroscope run a crash detector continuously. A
   confirmed impact fires the emergency automatically — no button.
2. **Capture (phone).** It opens **both cameras** (cabin + road stills), and because a rolling
   **pre‑buffer** is always recording, the audio clip covers the seconds **before** the impact plus
   a longer clip **after**.
3. **Reason (cloud‑primary AI).** Signals + evidence go to the backend's hosted LLM (Azure OpenAI)
   as the primary brain; if it is unreachable/unconfigured the phone falls back to the **on‑device**
   model — no coverage lost offline.
4. **Alarm.** The phone sounds a loud alarm‑stream **siren + vibration** until I'M SAFE.
5. **Back up (IoT).** The phone streams the black‑box bundle to the **ESP32** over BLE; the device
   **buzzes** and keeps the record so it survives phone loss.
6. **Alert.** Responders (police / ambulance / family) get the location + details; the driver closes
   the incident with **I'M SAFE**. Abnormal driving (hard braking, swerves, impacts) is flagged and
   **warned** in an **Abnormal activity** feed.

## Repository

| Path | Component | Status |
|---|---|---|
| [mobile/guardian-android](mobile/guardian-android) | Kotlin + Compose **primary** app (detect, capture, AI, siren) | builds (JDK 17/19) |
| [backend/guardian-api](backend/guardian-api) | FastAPI + SQLAlchemy + WebSocket + cloud‑AI endpoint | runs, 38 tests |
| [dashboard/guardian-dashboard](dashboard/guardian-dashboard) | React + TS + Vite + MUI + Leaflet | builds |
| [device/guardian-device](device/guardian-device) | ESP32 firmware (PlatformIO): backup black box + buzzer | host‑tested; needs `pio run` |
| [simulator/vehicle-simulator](simulator/vehicle-simulator) | Virtual device + reference crash engine | runs, tests incl. e2e |
| [docs](docs) | Architecture, protocols, hardware, demo, testing, security | |
| [PLAN.md](PLAN.md) | Current mobile‑primary build plan & status | |

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

## The app (primary) — build, run, demo

```powershell
cd mobile/guardian-android
$env:JAVA_HOME = "C:\Program Files\Java\jdk-19"   # JDK 17–19; NOT 25
./gradlew.bat assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- **Point the app at your backend/LAN:** Settings → Servers → **Backend URL** (default
  `http://10.239.116.232:8000`). On a phone off the laptop's network, use `adb reverse tcp:8000
  tcp:8000` and `http://127.0.0.1:8000`.
- **Device source** defaults to **"No device connected"** (honest — no fake status). Pick
  **Guardian device** once your IoT device is heartbeating, or **Simulator** for a demo.
- **Demo the capture live (no crash needed):** Home → **Capture test** opens both cameras + the mic
  and shows the **cabin + road photos** and **before/after audio** you can play back.
- On a real impact the phone raises the full emergency: **siren**, AI assessment, captured evidence,
  and **I'M SAFE** to stand responders down.

## Cloud AI (primary reasoner)

Put Azure OpenAI credentials in the git‑ignored repo‑root `.env` (see `.env.example`), then restart
the backend:

```ini
AZURE_OPENAI_ENDPOINT=https://<resource>.openai.azure.com/
AZURE_OPENAI_API_KEY=<key>
AZURE_OPENAI_DEPLOYMENT=gpt-4o
```

The phone posts crash signals to `POST /api/ai/assess`. If Azure is unset the endpoint returns 503
and the phone uses its **on‑device** reasoner — the app works either way.

Android details: [mobile/guardian-android/README.md](mobile/guardian-android/README.md).
ESP32 details: [device/guardian-device/README.md](device/guardian-device/README.md).

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
