# Guardian — Mobile‑Primary + IoT Black Box: Build Plan

> Authoritative plan for the current direction. We implement these **one by one**.
> Legend: `[x]` done · `[~]` partial · `[ ]` not started

## Corrected architecture (read first)

- **Mobile phone = PRIMARY.** It detects the crash with its own sensors, **auto‑detects the
  emergency intent**, turns on the **dual camera** (cabin + road), records the **in‑cabin
  conversation**, and runs **on‑device AI** — everything on the phone, no network needed.
- **IoT device (ESP32) = BACKUP black box.** If the phone is destroyed/lost, the IoT device
  takes over. **All captured data (AI assessment + audio + images + sensor log + GPS + time) is
  transferred from the phone to the IoT device** so it survives the phone.
- **There is NO "cloud device."** Cloud AI is an *optional* fallback only (on‑device is primary).
  The backend exists only to coordinate the responder notification — it is **not** a device and
  must not appear as one in the app.
- **No hardcoded device link / IP.** The device address comes from the real device, never a constant.

```text
CRASH
  │
  ├─ Phone (PRIMARY): intent detect → dual cam + voice + sensors → on‑device AI → black box bundle
  │        │
  │        └─ transfer bundle ───────────────▶ IoT device (BACKUP black box) stores it
  │
  └─ Alert delivered by the phone if it can; else by the IoT device (survives phone loss)
```

---

## 1. Mobile‑primary capture pipeline
- [x] Phone crash detection using the phone's accelerometer + gyroscope (primary trigger)
- [x] **Auto** on a detected emergency — the whole capture runs with no button
- [x] **Dual camera capture** — front/cabin + rear/road stills (CameraX, no preview)
- [x] Record in‑cabin **voice/conversation** automatically on detection
- [x] On‑device AI assessment over the crash signals
- [~] Extend the AI to actually read the audio + images (currently reasons over signals + transcript)

## 2. IoT device = backup + full data transfer
- [~] Define the complete black‑box bundle (AI assessment + audio + images + sensor log + GPS + time)
- [~] Transfer the bundle **phone → IoT device** over the real link (chunked)
- [ ] IoT **stores** the bundle and can deliver the alert if the phone is gone
- [ ] Verify end‑to‑end once the real IoT device is connected

## 3. Proper README
- [ ] Rewrite the README to match this architecture (mobile‑primary, IoT backup, how to run, demo)

## 4. Remove the "cloud" from the app + fix the emergency‑path view
- [~] Dropped the "cloud / Wi‑Fi" wording; the source is now just **"Guardian device"**
- [x] Fixed the "emergency path" view: **Phone → Device → Responders** (no "cloud" node)
- [ ] Point the device client at the **real IoT** (direct) once the IoT IP is provided

## 5. Remove hardcoded device link
- [ ] Remove any hardcoded device/backend IP in the app; make it configurable/discovered
- [ ] The device link must come from the real device, not a constant

## 6. Automatic intent → record conversation + images
- [x] Fires automatically on a probable crash (no user tap)
- [x] Auto‑records conversation + auto‑captures cabin + road photos
- [x] Attached to the black‑box bundle (transfer to IoT wired; runs when the device is connected)

## 7. Settings cleanup (device details appear only when the IoT is connected)
- [x] Hid the **"Offline mock"** source
- [x] Hid the **"Real Guardian device (Bluetooth)"** source
- [x] Vehicle details now appear **only when the device is connected**

---

## Needed from you
- [ ] The **IoT device IP** and how it connects (phone → IoT directly, or IoT → backend)
- [ ] **Rotate the Azure keys** that were shared; cloud AI is optional fallback only (keys go in a git‑ignored `.env`)

## Already in place (starting point)
- Phone sensor crash detection; on‑device AI assessment; audio capture; black‑box bundle;
  emerald theme + Guardian shield logo.
