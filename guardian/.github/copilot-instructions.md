# Copilot instructions — Guardian POC

Guardian is a hackathon POC: a vehicle-installed device that detects serious crashes and reports
them **without depending on the driver's phone**. Full spec: `../GUARDIAN_COPILOT_BUILD_SPEC.md`
(outside repo) and `docs/`. Work plan: `TODO.md`.

## Non-negotiable rules

1. The emergency path is `Device -> Cellular -> Backend`. Never route emergencies through the phone.
   BLE is companion-only; losing it must never block emergency transmission.
2. Never implement vehicle control. OBD-II/CAN is read-only (AT + Mode 01 only).
3. Never claim automotive certification or real police/hospital dispatch. Emergency workflows are SIMULATED.
4. Never hard-code or commit secrets (`.env`, `include/config/secrets.h` are git-ignored).
5. Crash thresholds live in exactly one place per component:
   `device/guardian-device/include/config/crash_config.h` and
   `simulator/vehicle-simulator/crash_engine.py::CrashConfig` — keep them identical.
6. Do not silently change API / BLE contracts (`docs/api.md`, `docs/ble-protocol.md`,
   `dashboard/.../src/types.ts`, Android `GuardianBleUuids.kt`/`BlePayloadParser.kt`).
7. Every hardware interface needs a mock with the same interface. Preserve simulator mode.

## Working style

- Implement one milestone at a time (see `TODO.md`); keep the project runnable after each.
- Prefer working POC code over speculative infrastructure; avoid unnecessary dependencies.
- Write tests for important logic; run them before finishing:
  - backend: `cd backend/guardian-api; python -m pytest -q`
  - simulator + e2e: `cd simulator/vehicle-simulator; ..\..\backend\guardian-api\.venv\Scripts\python -m pytest -q`
  - dashboard: `cd dashboard/guardian-dashboard; npm run build`
  - firmware core: `cd device/guardian-device; pio test -e native`
  - android: `./gradlew testDebugUnitTest`
- Update docs when architecture or contracts change. Report files changed and remaining issues.
