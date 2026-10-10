# Guardian — Mobile‑Primary + IoT Black Box: Build Plan

> Authoritative plan for the current direction. We implement these **one by one**.
> Legend: `[x]` done · `[~]` partial · `[ ]` not started

## Corrected architecture (read first)

- **Mobile phone = PRIMARY.** It detects the crash with its own sensors, **auto‑detects the
  emergency intent**, turns on the **dual camera** (cabin + road), records the **in‑cabin
  conversation**, and runs AI — the capture happens on the phone with no network needed.
- **Cloud AI = PRIMARY reasoner (for now).** The phone sends crash signals to the backend's
  hosted LLM (Azure OpenAI) as the primary brain. The **on‑device model is the fallback** and
  becomes primary once the local LLM is installed. No coverage is lost offline.
- **IoT device (ESP32) = BACKUP black box + local alarm.** If the phone is destroyed/lost, the IoT
  device takes over: it **receives the black‑box bundle** from the phone and **sounds its buzzer**.
- **There is NO "cloud device."** The backend coordinates responder notification only — it is
  **not** a device and no longer appears as one in the app.
- **Honest device status.** Nothing shows as "connected" (no fake battery) until a **real IoT
  device** actually heartbeats. Default source is **"No device connected."**

```text
CRASH
  │
  ├─ Phone (PRIMARY): intent detect → dual cam + voice + sensors
  │        │                      → Cloud AI (fallback: on‑device) → black box bundle + LOUD SIREN
  │        └─ transfer bundle ───────────────▶ IoT device (BACKUP black box) stores it + BUZZES
  │
  └─ Alert delivered by the phone if it can; else by the IoT device (survives phone loss)
```

---

## 1. Mobile‑primary capture pipeline
- [x] Phone crash detection using the phone's accelerometer + gyroscope (primary trigger)
- [x] **Auto** on a detected emergency — the whole capture runs with no button
- [x] **Dual camera capture** — front/cabin + rear/road stills (CameraX, no preview)
- [x] Record in‑cabin **voice/conversation** automatically on detection
- [x] **Before + after audio** — a rolling pre‑buffer captures the seconds *before* impact plus a
      longer clip *after*; the AI now states the real captured audio/photos (no more hardcoded
      "no audio")
- [x] **Live demo**: "Capture test" button runs the real dual‑camera + mic now (no crash needed)
- [x] Show the captured **cabin + road photos** and **play before/after audio** in‑app (emergency + demo)
- [~] Extend the AI to actually read the audio + image **bytes** (currently reasons over signals + evidence)

## 2. IoT device = backup + full data transfer
- [~] Define the complete black‑box bundle (AI assessment + audio + images + sensor log + GPS + time)
- [x] Transfer the bundle **phone → IoT device** — now over **Wi‑Fi** (`POST /blackbox`); BLE path exists too
- [~] IoT **buzzer** — firmware ready; blocked on the hardware component
- [x] IoT receives + exposes the bundle at `GET /blackbox` (RAM; flash persistence optional)
- [x] Verified end‑to‑end with the real ESP32 over Wi‑Fi (device IP `192.168.137.52`)

## 3. Proper README
- [x] Rewrite the README for the mobile‑primary architecture (phone primary, cloud AI, IoT backup,
      run + capture demo)

## 4. Remove the "cloud" from the app + fix the emergency‑path view
- [x] Dropped the "cloud / Wi‑Fi" wording; the source is now just **"Guardian device"**
- [x] Fixed the "emergency path" view: **Phone → Device → Responders** (no "cloud" node)
- [x] Point the device client at the **real IoT** over Wi‑Fi (device web server at `192.168.137.52`)

## 5. Remove hardcoded device link
- [x] Removed the fake connected device + hardcoded 92% battery; status is now honest (disconnected
      until a real device heartbeats)
- [x] Backend/device address is a **configurable** default (Settings), not a baked‑in constant
- [ ] Verify once the real IoT is on the network

## 6. Automatic intent → record conversation + images + WARN on abnormality
- [x] Fires automatically on a probable crash (no user tap)
- [x] Auto‑records conversation + auto‑captures cabin + road photos
- [x] **Abnormal‑driving detection** (hard braking, sharp swerve, sudden impact) with a **warning**
      (snackbar + notification), separate from a full crash
- [x] "History" replaced by an **Abnormal activity** feed (anomalies + crashes), not a plain log

## 7. Settings + status cleanup
- [x] Hid the **"Offline mock"** and **"Bluetooth"** sources; picker shows No device / Guardian
      device / Simulator
- [x] Vehicle details appear **only when the device is connected**
- [x] Phone is shown as PRIMARY protection; the IoT device is clearly the **backup**

## 8. Alarms
- [x] **Phone siren** — loud alarm‑stream tone + vibration the moment a crash is detected; stops on
      I'M SAFE / dismiss
- [x] **IoT buzzer** — local audible alarm on the ESP32 (backup when the phone is destroyed)

---

## Cloud AI (primary reasoner)
- [x] Backend `POST /api/ai/assess` → Azure OpenAI when configured, else 503 → phone falls back
      to the on‑device model
- [x] Phone `CompositeReasoner` tries Cloud first, then on‑device
- [x] Backend reads the repo‑root git‑ignored `.env` for the Azure config

---

## Real device integration (now live)
- [x] App ↔ **real XIAO ESP32‑C3** over **Wi‑Fi** (not BLE): polls `GET /data`, streams live phone
      signals to `POST /phone`, transfers the black box to `POST /blackbox`
- [x] Dashboard rebuilt **device‑fed** (no backend, no simulator): **Live network** + **Responder
      ops** read `/data` `/phone` `/blackbox` directly; **OpenStreetMap** incident map; SMS‑sent +
      backup‑alert chips; operator **Acknowledge**
- [x] **Real SMS dispatch** from the phone to emergency contacts over cellular (needs a SIM); exact
      messages shown on the emergency screen
- [x] **Backup alert** when the phone is offline (dashboard infers phone‑offline + new collision;
      honours the sketch `backupAlert` flag once flashed)
- [x] **Background mode** — foreground service (camera + mic + dataSync) keeps detection, the rolling
      black box and streaming alive when backgrounded
- [x] **Precise detection** — free‑fall rejection (dropped phone ≠ accident); only MODERATE+ raises
      an accident; small jolts → abnormal‑activity warning
- [x] **Live transfer demo** — toggle streams an evolving black‑box snapshot to the device every 3 s
- [x] Device IP configurable (currently `192.168.137.52`)

## Lined‑up next (roadmap)

### 1. Install on mobile — [x] (done every build)

### 2. IoT buzzer — [ ] blocked on hardware
Waiting on the buzzer component. Firmware driver + Copilot prompt are ready; wire on arrival.

### 3. Connectivity without a SIM — [~] show in UI for now
- No SIM = no real SMS/call can go out. **Showcase in the UI**: display the SMS content (done) and a
  simulated **call** screen ("Calling 112 / 108 / family…") from the phone and/or IoT.
- With a SIM later: real SMS (built) + `ACTION_CALL` auto‑dial.

### 4. Continuous audio processing (transcription) — [ ]
- Add **speech‑to‑text** (Whisper): on‑device `whisper.cpp`/TFLite, or cloud Whisper when online.
- Rolling **10‑minute** audio + photo buffer already refreshes (override old with new).
- On crash: transcribe + analyse the **before + after** audio (both already captured) and feed the
  transcript to the AI.

### 5. Capture images when the phone is disconnected — [ ]
- Buffer the latest cabin/road frames on the phone and hand them to the IoT **before** loss; the
  ESP32 has no camera, so the black box keeps the **last transferred frames**.

### 6. Transfer actual images + audio (not just JSON) — [ ]
- Show the **media** moving phone → IoT. ESP32‑C3 can't store media (no SD), so route media to where
  the dashboard can show it (phone‑served or backend) and **visualise the transfer**.

### 7. Multi‑transport: BLE + Wi‑Fi + SIM — [~]
- **Wi‑Fi**: done. **BLE**: client exists, needs wiring to the sketch's GATT. **SIM**: none to test.
- IP‑range hardcoding is not ideal but **kept** for now (configurable default in Settings).

### 8. In‑depth web UI — [~]
- Richer showcase: per‑incident detail, live‑transfer timeline, media gallery, dispatch log, bigger map.

### Housekeeping
- [x] Removed the two Copilot meta files (`GUARDIAN_COPILOT_BUILD_SPEC.md`, `.github/copilot-instructions.md`)

## Needed from you
- [ ] **SIM card** for real SMS/calls (currently showcased in the UI only)
- [ ] **Buzzer component** for the IoT alarm
- [ ] Decide **Whisper** target: on‑device (offline, heavy) vs cloud (needs internet)
- [ ] **Rotate the Azure keys** in `.env` — they were exposed in chat and must be regenerated

## Already in place (starting point)
- Phone sensor crash detection; cloud + on‑device AI; audio + dual‑camera capture; black‑box bundle;
  BLE transfer; emerald theme + Guardian shield logo.
