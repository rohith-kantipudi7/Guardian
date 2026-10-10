# Guardian Android App (iQOO companion)

Kotlin + Jetpack Compose + Coroutines/Flow + BLE + Room + Retrofit. Runs on any Android 8.0+
device; demonstrated on an iQOO phone. **The app is a companion — the vehicle device reports
emergencies over its own cellular link whether or not this phone is present.**

## Build & run

Requirements: Android Studio (Ladybug or newer, bundled JDK 17), Android SDK 35.

1. Android Studio → *Open* → `mobile/guardian-android` (it syncs Gradle 8.9 from
   `gradle/wrapper/gradle-wrapper.properties`; run `gradle wrapper` once if you want CLI `gradlew`).
2. Run on the iQOO phone (USB debugging) or an emulator.
3. Settings → *Servers*: emulator `http://10.0.2.2:8000` (cloud) and `http://10.0.2.2:8100` (virtual device);
   real phone: the laptop's Wi-Fi IP, and start the virtual device with `python simulator.py serve --host 0.0.0.0`.
4. Unit tests: `./gradlew testDebugUnitTest` (or right-click `app/src/test` → Run).

### On-device transcription (whisper.cpp)

The in-cabin audio black box is transcribed on-device with whisper.cpp. Its source and the ggml model
are **not committed** (size); fetch them once before building:

```powershell
# from mobile/guardian-android
git clone --depth 1 https://github.com/ggerganov/whisper.cpp
# tiny English model (~75 MB) into the app assets
curl.exe -L -o app/src/main/assets/models/ggml-tiny.en.bin `
  https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin
```

The native lib builds via `app/src/main/cpp/CMakeLists.txt` (NDK 26, CMake 3.22, arm64-v8a). If the
model asset is missing, transcription degrades gracefully to empty (the rest of the app is unaffected).

## How the app connects

```text
             Bluetooth LE (companion)            own LTE (emergency path)          SMS + link
  Phone app  <------------------------>  Vehicle device  ----------------->  Guardian cloud  ----------->  Police / Ambulance / Family
      ^                                                                            |
      +-------------------- live feed (WebSocket /ws/emergency) -------------------+
```

- **Phone ⇄ device**: BLE with the ESP32 (status, crash alerts, I'M SAFE, history). Without hardware, the
  *virtual vehicle device* (`python simulator.py serve`, HTTP on :8100) stands in for BLE: it is the same
  device the web Showcase drives, so "Phone gone" there drops the link here and a crash pops up on the phone.
- **Device → cloud**: the device's own cellular link. The phone is never on this path.
- **Cloud → phone**: WebSocket live feed. The emergency screen shows who was alerted and their live status
  (Alerted → Seen → On the way → Help delivered). I'M SAFE cancels on the device and in the cloud.

## Device modes

| Mode | Use |
|---|---|
| Virtual vehicle device (default) | No hardware. Connects to `simulator.py serve`; crashes reach the real backend and responders. |
| Real Guardian device | ESP32 over BLE (advertises `Guardian-GD-001`). Settings → *Real Guardian device* → *Connect*. |
| Offline mock | Built into the app, nothing leaves the phone (UI development only). |

## Screens

Home (protection status + connection chain + last incident) · Emergency (live responder status, I'M SAFE) ·
Device (link, live device status, demo test controls) · History · Settings (device source, servers, vehicle,
contacts).

## Architecture

```text
ui/ (Compose screens)  ->  GuardianViewModel  ->  GuardianRepository
                                                   ├── SwitchableDeviceClient
                                                   │     ├── VirtualDeviceClient (simulator over HTTP)
                                                   │     ├── BleGuardianDeviceClient (GATT op queue, auto-reconnect)
                                                   │     └── MockGuardianDeviceClient
                                                   ├── GuardianApi (Retrofit) + BackendEventStream (WebSocket)
                                                   └── Room (crash_events)
```

Recovery: on every (re)connect the app sends `GET_HISTORY` to the device and syncs
`GET /api/crashes?deviceId=` from the backend, so crashes reported while the phone was off
appear in History.
