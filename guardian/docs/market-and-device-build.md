# Guardian — Market Check, Differentiation & IoT Device Build Plan

> Status: research notes for the hackathon submission (Oct 2026). Items marked **(verify)** are
> from general industry knowledge and should be confirmed before you quote them to judges.

---

## 1. Does this product already exist?

**Yes — the core idea (a vehicle that calls for help by itself after a crash) exists.**
Guardian is not a new category; it must win on *who it serves* and *how it is delivered*.

### 1.1 Built into new cars (OEM)

| Product | What it does | Phone needed? | Limitation for us to exploit |
|---|---|---|---|
| **EU eCall** | Mandatory in all new cars type-approved in the EU since **1 April 2018**. Auto-dials 112 after a serious crash, sends GPS + airbag/impact data; manual SOS button. Standardised under **UNECE R144**; ITU-T Y.4467/Y.4468 define data set & protocol (incl. aftermarket devices). [source](https://en.wikipedia.org/wiki/ECall) | No — own modem | Only new cars, only where mandated. Not retrofittable to the existing fleet. |
| **ERA-GLONASS** (Russia) | eCall-compatible national system. [source](https://en.wikipedia.org/wiki/ECall) | No | Region-specific. |
| **GM OnStar** Automatic Crash Response | Embedded telematics unit; airbag/sensor-triggered crash notification with location, rollover, impact direction, delta-V to a 24×7 centre. Subscription (~US$30/mo for Safety & Security). [source](https://en.wikipedia.org/wiki/OnStar) | No | GM vehicles, US-centric, paid subscription. |
| Ford SYNC Emergency Assistance, BMW/Mercedes/Toyota SOS | OEM emergency call. | Ford SYNC: **uses paired phone** (verify) | Brand-locked; some depend on the phone. |
| India connected cars — Hyundai Bluelink, Tata iRA, MG i-Smart, etc. | Embedded eSIM with SOS / emergency features on newer variants. **(verify per model)** | Mostly no | Only newer, higher variants; small share of India's on-road fleet. |

### 1.2 Phone-based (software only)

| Product | What it does | Phone needed? |
|---|---|---|
| **Apple Crash Detection** | iPhone 14+/Apple Watch; detects severe crashes, 10 s alert + 30 s countdown, then calls emergency services and messages contacts with location; satellite fallback in some countries. [source](https://support.apple.com/en-us/104959) | **Yes — the phone *is* the device** |
| Google Pixel Car Crash Detection, Life360, OnStar Guardian app | Same concept on Android / cross-platform. OnStar Guardian uses phone accelerometer + GPS. [source](https://en.wikipedia.org/wiki/OnStar) | **Yes** |

### 1.3 Aftermarket / retrofit devices (closest competitors)

| Product | What it does | Gap |
|---|---|---|
| **OnStar Link** (OBD-II plug-in) | GPS + 4G LTE IoT device in the OBD port; provides most OnStar services. **Voice with an advisor requires a Bluetooth-paired phone.** US/Canada only. [source](https://en.wikipedia.org/wiki/OnStar) | Closest analogue. Not sold in India; voice path depends on phone. |
| OnStar FMV (2011–2014, discontinued) | Replacement rear-view mirror with crash sensors + cellular. [source](https://en.wikipedia.org/wiki/OnStar) | Discontinued — shows retrofit is hard to sell at US$299/yr. |
| OBD GPS trackers (Bouncie, Vyncs, JioMotive, etc.) | Location, trip, sometimes "accident/impact alert" to an app. **(verify each)** | Alerts go to an app/owner, not an emergency workflow; crash logic usually basic. |
| India **AIS-140** VLTD (vehicle location tracking device) | Mandated for public-service / commercial vehicles: GPS tracking + panic buttons connected to state command centres. **(verify current notification)** | Commercial vehicles only; *panic button* (manual), not automatic crash detection. |
| Helmet / bike sensors (Tocsen, ANGi, etc.) | Crash sensor on helmet, alerts via phone. **(verify)** | Phone-dependent. |

### 1.4 Honest conclusion

- **Not novel:** automatic crash notification, phone-independent cellular, OBD retrofit all exist.
- **Gap that is real:** in markets like India, the *existing* fleet (older cars, base variants,
  taxis, two-wheelers) has **no embedded eCall**, and the available alternatives are either
  **phone-based** (fails when the phone is destroyed/flat/thrown) or **trackers** that notify an
  app rather than run an emergency workflow.

---

## 2. What Guardian adds (positioning)

| # | Differentiator | Versus |
|---|---|---|
| 1 | **Retrofit eCall-class device for the existing fleet** — no OEM integration, works on any vehicle (OBD optional). | eCall / OnStar (new cars only) |
| 2 | **Phone-independent by design** — own LTE + GNSS + backup battery; phone is a companion only. *Demo: phone off, dashboard still gets the crash.* | Apple / Pixel / Life360 / OnStar Guardian / OnStar Link voice |
| 3 | **Local-first reliability** — event persisted to flash before sending, retry with backoff, **exactly-once** at the backend, survives network dead zones and power loss. | Typical trackers (best-effort SMS / push) |
| 4 | **Explainable severity + confidence** (impact, rotation, speed drop, repeated impact) with a **verification window** (I'M SAFE) to cut false alarms. | Basic "impact alert" trackers |
| 5 | **Response-centre dashboard**, not just an owner app — incident, location map, device health, phone state, acknowledge/resolve workflow. | Consumer tracker apps |
| 6 | **Works without OBD** (speed unavailable → still detects) — usable on two-wheelers, autos, old vehicles. | OBD-only devices |
| 7 | **Open, standards-aligned path** — data model can map to the eCall Minimum Set of Data (ITU-T Y.4467) and India ERSS-112 integration later (needs authorisation). | Proprietary closed trackers |
| 8 | **Price target** — aftermarket BOM aim < ₹4,000 at volume with a low-cost Cat-1 bis IoT SIM plan. **(estimate)** | OnStar subscription pricing |

**Gaps vs. eCall that we must close before calling it "eCall-class":**

| Gap | Plan |
|---|---|
| No **voice call** to the responder (we send data only) | Add mic + speaker on the LTE module (SIM7600/A7672 support voice) → auto-call 112/response centre after data push. |
| No **manual SOS button** | Add a tactile SOS button (GPIO) → same event path with `trigger=MANUAL`. |
| IMU range: MPU6050 saturates at **±16 g**; real crashes exceed this | Add a **high-g accelerometer** (H3LIS331DL ±400 g or ADXL375 ±200 g) for impact peak; keep 6-axis IMU for rotation. |
| No airbag signal | Optional: detect airbag deployment via OBD/CAN where exposed (read-only). |
| No certification | See §5 certification checklist. |

**Pitch line:** *"eCall saves lives in new European cars. Guardian brings the same
phone-independent emergency signal to the hundreds of millions of vehicles already on the road."*

---

## 3. Guardian IoT device — architecture

```text
            12 V vehicle supply ──► fuse ─► TVS/reverse-protection ─► 12→5 V buck ─┐
                                                                                    ├─► power-path ─► 3.3 V LDO ─► ESP32 + sensors
            Backup Li-ion 18650 ◄── charger (TP4056 / BQ24074) ◄────────────────────┘                ─► 4.0 V rail ─► LTE modem (2 A peaks)

ESP32 (BLE + Wi-Fi, 2 cores)
 ├─ I2C ── 6-axis IMU (rotation)          MPU6050 → LSM6DSO / BMI270
 ├─ SPI ── high-g accelerometer (impact)  H3LIS331DL / ADXL375
 ├─ UART2 ─ GNSS (NMEA)                   NEO-M8N / ATGM336H + active antenna
 ├─ UART1 ─ LTE Cat-1/Cat-4 modem         SIM7600EI / A7672S + LTE & GNSS antennas + nano/eSIM
 ├─ CAN / UART ─ OBD-II (read-only)       MCP2515 + TJA1050 (listen-only) or ELM327-UART
 ├─ ADC ── battery & ignition sense
 ├─ GPIO ─ SOS button, buzzer, status LED
 └─ SPI ── microSD / flash (event log)    optional; LittleFS used today
```

Firmware already supports this split (`device/guardian-device`, flags in `platformio.ini`).

---

## 4. Bill of materials

Prices are **indicative Indian retail (Oct 2026), single quantity** — confirm with vendors
(Robu.in, Robocraze, Evelta, Electronicscomp, Mouser/DigiKey India).

### 4.1 Prototype v1 (breadboard / perfboard) — matches current firmware

| # | Component | Example part | Qty | Approx. ₹ | Purpose |
|---|---|---|---|---|---|
| 1 | MCU board | ESP32-DevKitC (WROOM-32E) | 1 | 450–700 | Crash engine, BLE, orchestration |
| 2 | 6-axis IMU | MPU6050 (GY-521) | 1 | 150–300 | Accel + gyro (±16 g, ±2000 °/s) |
| 3 | GNSS module | u-blox NEO-6M / NEO-M8N (or ATGM336H) + active antenna | 1 | 600–2,000 | Location + UTC time |
| 4 | LTE modem | SIMCom **SIM7600EI** (India bands) breakout, *or* A7672S Cat-1 bis | 1 | 1,800–6,000 | Independent emergency path |
| 5 | LTE antenna | 4G LTE stick/FPC antenna (IPEX/SMA) | 1 | 150–400 | |
| 6 | SIM | IoT/M2M data SIM (Jio/Airtel IoT) or regular data SIM for POC | 1 | 0–300 + plan | |
| 7 | Backup battery | 18650 Li-ion 2,600 mAh (protected) + holder | 1 | 250–450 | Survive vehicle power loss |
| 8 | Charger | TP4056 module **with protection** (DW01) | 1 | 40–80 | Charge backup cell |
| 9 | 12 V → 5 V | LM2596 or MP1584 buck (3 A) | 1 | 80–200 | Vehicle power input |
| 10 | Modem rail | 1,000 µF low-ESR cap + 100 nF near modem | 2 | 30 | Handle 2 A TX bursts |
| 11 | Protection | 12 V inline fuse (2 A) + holder, SMBJ24A TVS, reverse-polarity diode/P-MOSFET | 1 set | 100–200 | Load dump / wiring faults |
| 12 | Battery sense | 100 kΩ + 100 kΩ resistors | 2 | 5 | ADC divider |
| 13 | OBD-II (later) | ELM327 **UART** module *or* MCP2515 + TJA1050 CAN board + OBD-II male cable | 1 | 250–900 | Read-only speed/RPM |
| 14 | Car power | 12 V accessory-socket plug with leads | 1 | 100–200 | POC power source |
| 15 | User I/O | Tactile SOS button, active buzzer, RGB LED | 1 set | 50–100 | Manual SOS, local alert |
| 16 | Build | Breadboard / perfboard, jumper wires, headers, JST connectors | — | 200–400 | |
| 17 | Enclosure | ABS project box ~120×80×40 mm + mounting tape/screws | 1 | 150–300 | Rigid mounting |
| | **Prototype total** | | | **≈ ₹4,500 – 12,000** | depends mainly on LTE module choice |

### 4.2 Recommended upgrades for Prototype v2 (closer to product)

| Component | Part | Approx. ₹ | Why |
|---|---|---|---|
| High-g accelerometer | **H3LIS331DL** (±100/200/400 g) or **ADXL375** (±200 g) breakout | 600–1,500 | MPU6050 clips at 16 g; crash pulses can be 20–60 g+ |
| Better 6-axis IMU | LSM6DSO / BMI270 | 400–900 | Lower noise, built-in FIFO, automotive-grade variants exist |
| LTE with voice + GNSS | SIM7600EI-H / A7672S (voice + GNSS on some variants) | 1,800–6,000 | Voice call to responder; can drop the separate GNSS |
| Audio | Electret mic + 8 Ω 1 W speaker + PAM8302 amp | 150–300 | eCall-style voice channel |
| Power path | TI BQ24074 / BQ25895 board | 300–700 | Proper load sharing vehicle ↔ battery |
| Supercap (optional) | 5.5 V 1–5 F | 100–300 | Rides through modem bursts during brown-out |
| Storage | microSD breakout | 80–150 | Crash data recorder (pre/post-crash window) |
| Ignition sense | Opto-coupler (PC817) on ACC line | 20 | Wake/sleep with vehicle |

### 4.3 Tools

Soldering station, multimeter, USB-UART adapter (CP2102), bench power supply (0–15 V, 3 A),
hot-glue/epoxy, cable ties. Optional: logic analyzer (₹600–1,200), oscilloscope.

---

## 5. Step-by-step build plan

### Phase A — Bench bring-up (≈ 1–2 weeks)

Each step has a firmware flag in `device/guardian-device/platformio.ini` and a pass check.

| Step | Do | Pass check |
|---|---|---|
| A1 | Flash ESP32 with all mocks: `pio run -e esp32dev -t upload`; set Wi-Fi/backend in `include/config/secrets.h`. | Serial banner; `severe_crash` → incident on dashboard. |
| A2 | Wire **MPU6050** (SDA 21, SCL 22, 3V3, GND); `GUARDIAN_IMU_MPU6050=1`. | `status` OK; table tap → `SUSPECTED_IMPACT → FALSE_POSITIVE`; no trigger when shaking gently. |
| A3 | BLE pairing with the Android app (Settings → disable mock → Scan & connect). | App shows live battery/GPS/cellular; disconnect → device keeps reporting. |
| A4 | Wire **GNSS** (RX 16, TX 17); `GUARDIAN_GNSS_NMEA=1`; test outdoors. | `gps=AVAILABLE`, coordinates on dashboard; clock set from satellites. |
| A5 | Wire **LTE modem** on its **own 4 V / 2 A supply** (RX 26, TX 27, PWRKEY 4), insert SIM, set APN; `GUARDIAN_CELLULAR_SIMCOM=1`. | Heartbeats arrive with Wi-Fi off; **phone OFF → crash reaches dashboard**. |
| A6 | Add **battery + charger + buck**; power from bench supply at 12 V. | Pull 12 V → device stays online on battery; event still sent. |
| A7 | `cell off` → `severe_crash` → `cell on`; power-cycle with a queued event. | Delivered **exactly once**, also after reboot. |
| A8 | (v2) Add **high-g accelerometer**; feed its peak into `accelerationPeakG`. | Drop test from 1 m onto foam shows peaks > 16 g without clipping. |
| A9 | (v2) Add **SOS button**, buzzer, LED. | Long-press SOS → `MANUAL` incident; buzzer during verification window. |

### Phase B — Vehicle integration (≈ 1 week)

| Step | Do | Safety rule |
|---|---|---|
| B1 | Mount enclosure **rigidly** to the floor/console (not dashboard top); +x to vehicle front. Record orientation. | Do not obstruct airbags, pedals, wiring harnesses. |
| B2 | Power from fused accessory socket (or ACC fuse tap with add-a-fuse). | Never tap airbag/ABS circuits. |
| B3 | Plug OBD adapter; `GUARDIAN_VEHICLE_OBD=1`. | **Read-only** (AT + Mode 01 only, enforced in firmware). |
| B4 | Drive normal routes, speed breakers, potholes, hard braking; log `status`. | Zero `PROBABLE_CRASH` in normal driving. Tune only `crash_config.h`. |
| B5 | Demonstrations use `SIMULATE` (serial/BLE) — **never stage a real crash**. | |

### Phase C — Data & validation (ongoing)

1. Log raw IMU windows (pre/post 2 s) to microSD on every trigger.
2. Collect labelled data: normal, speed breaker, pothole, door slam, hard brake, controlled drop tests.
3. Track false-positive rate per 1,000 km and missed-detection rate on drop/sled tests.
4. Re-tune thresholds; later train the ML severity classifier on the same features (spec §35).

### Phase D — Productisation (after the hackathon)

| Step | Output |
|---|---|
| D1 | **Custom PCB** in KiCad: ESP32-S3 module, LSM6DSO + H3LIS331DL, A7672S/SIM7600, power path, automotive input protection (ISO 7637/ISO 16750 pulses), eSIM (MFF2) footprint. |
| D2 | **Enclosure**: IP54+, flame-retardant (UL94 V-0), rigid mount, external antennas or embedded FPC. |
| D3 | **Firmware**: OTA updates, secure boot + flash encryption (ESP32), per-device certificates (mTLS) instead of shared secret, watchdog, brown-out handling. |
| D4 | **Backend**: managed cloud, HTTPS, operator auth, audit log, 24×7 response-centre workflow, ERSS-112 integration (needs government authorisation). |
| D5 | **Certification (India, verify scope with a test lab like ARAI/ICAT):** WPC ETA (radio), TEC MTCTE (telecom), BIS where applicable, automotive EMC (AIS-004), and **AIS-140** if sold for commercial/public-service vehicles; UNECE R10/R144 for export markets. |
| D6 | **Pilot**: 20–50 vehicles (fleet/taxi partner), measure false alarms, uptime, delivery latency. |
| D7 | **Business model**: device + yearly service (data SIM + response centre); B2B for fleets/insurers first, then consumers. |

---

## 6. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Market already has eCall/OnStar | Position as **retrofit for existing fleet** in markets without mandate; partner with insurers/fleet operators. |
| False positives (speed breakers, potholes) | Robust k-th peak, verification window, field-tuned thresholds, ML phase 2. |
| Sensor saturation in severe crashes | High-g accelerometer (v2). |
| No coverage at crash site | Persisted queue + retry; future: SMS fallback, satellite (NTN) on newer modems. |
| Power loss after crash | Backup battery + supercap; send event before anything else. |
| Device damaged in crash | Rigid low mount in the cabin, small form factor, store-then-send within ~1 s. |
| Recurring SIM cost | Cat-1 bis IoT plans, data-only (bytes per event), heartbeat interval tuning. |
| Liability / regulation | Clear "assistive, not certified" positioning in POC; certification roadmap before sale. |
| Privacy | Minimal data, location retention limit (implemented), user consent, no audio recording. |

---

## 7. Sources

- eCall overview, 2018 EU mandate, UNECE R144, ITU-T Y.4467/Y.4468, ERA-GLONASS — <https://en.wikipedia.org/wiki/ECall>
- OnStar ACR, OnStar Link OBD-II LTE device, OnStar FMV, pricing — <https://en.wikipedia.org/wiki/OnStar>
- Apple Crash Detection behaviour and requirements — <https://support.apple.com/en-us/104959>
- Items marked **(verify)**: confirm with manufacturer pages / MoRTH notifications before submission.
