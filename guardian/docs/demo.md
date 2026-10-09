# Demo Script

Opening line: *"What happens when your phone is the first thing destroyed in an accident?"*

## Setup (5 minutes before)

| Window | Command |
|---|---|
| Backend | `cd backend/guardian-api; .\.venv\Scripts\python -m uvicorn app.main:app --host 0.0.0.0 --port 8000` |
| Dashboard (projector) | `cd dashboard/guardian-dashboard; npm run dev` → http://localhost:5173 |
| Device | ESP32 serial monitor **or** `cd simulator/vehicle-simulator` |
| Device heartbeat (simulator only) | `python simulator.py run` — keeps the device ONLINE on the dashboard (ESP32 does this itself every 10 s) |
| Phone | iQOO app, Settings → Response center URL = `http://<laptop-IP>:8000` |

Reset demo data: delete `backend/guardian-api/guardian.db`, run `python simulator.py reset; python simulator.py seed`.
Allow port 8000 through the laptop firewall so the phone/ESP32 can reach it.

## Showcase mode (no hardware, no live testing)

1. Start backend and dashboard as above.
2. `cd simulator/vehicle-simulator; python simulator.py reset; python simulator.py serve` (device control panel on :8100, sends heartbeats).
3. Open http://localhost:5173/showcase, click **Reset demo**.
4. Click **Run step 1 … 10**. Narrate the text shown for each step:
   1. Normal drive → nothing happens. 2. Severe crash → CRITICAL, phone shows CRASH DETECTED; police, hospital and family are alerted automatically.
   3. Phone gone. 4. Crash again → still reported and alerted, tagged *NO PHONE*.
   5. Dead zone crash → *Queued on device*. 6. Coverage returns → delivered and alerted automatically.
   7. Pothole + hard brake → rejected (nobody alerted). 8. Phone reconnects → device syncs the open emergency to the phone.
   9. Responders read the alert → ambulance, police and family open their links; each shows as *Seen* automatically.
   10. Driver is safe → I'M SAFE closes the incident; every responder page shows *Stand down*.
5. Click **Open as hospital** (or police / family) in the Responders panel to show exactly what that person sees on their phone: map + Navigate, vehicle, driver and family numbers (tap to call), crash data, who else is responding.
6. Tap **I'M SAFE** on the phone mockup → the incident is cancelled and every responder's page shows *Stand down*.
7. Close with the **How it works** page (who gets what).

Optional: run the Android app on the emulator alongside; it receives the same incidents through the live feed.

## Scripted flow (spec §23)

| # | Action | Expected |
|---|---|---|
| 1-4 | Backend, dashboard, app, device running | Dashboard `LIVE`, device card ONLINE |
| 5 | App → Scan & connect (or mock device) | App dashboard: BLE CONNECTED, Crash Protection ACTIVE |
| 6 | Show normal status (`python simulator.py normal`) | No incident |
| 7 | Trigger severe crash: app Developer → SIMULATE SEVERE CRASH, serial `severe_crash`, or `python simulator.py severe_crash` | |
| 8 | Device creates event | Serial/log: `EMERGENCY CRITICAL confidence=0.92` |
| 9 | Phone shows alert | Emergency screen: CRASH DETECTED, CRITICAL, 92% |
| 10 | Dashboard shows incident | Red CRITICAL card, map marker, `Phone: CONNECTED` |
| 11 | **Disconnect the phone** (Developer → FORCE PHONE DISCONNECTED, turn off Bluetooth, or `python simulator.py phone_disconnect`) | Device: `phone disconnected - emergency path unaffected` |
| 12 | Trigger another severe crash (serial / simulator) | |
| 13 | Device sends directly over cellular | Log: `ACK EVT-…` |
| 14 | Dashboard receives event | New CRITICAL card, alert banner |
| 15 | Event shows `phoneConnected=false` | `Phone: DISCONNECTED` chip + "Reported directly by the vehicle device" |
| 16 | Reconnect phone | History shows both crashes (BLE `GET_HISTORY` + backend sync) |

Closing line: **"The phone is gone. The emergency signal isn't."**

One-command rehearsal against a running backend: `python simulator.py demo --step`.

## Bonus segments

- **Network outage:** `python simulator.py network_offline` → `severe_crash` (queued) →
  `network_restore` → exactly one incident appears.
- **False-positive guard:** `pothole`, `hard_brake`, `minor_impact` → no incident.
- **I'M SAFE:** `rollover` → 10 s verification on the phone → tap I'M SAFE → cancelled.
- **Response center:** open incident → ACKNOWLEDGE → RESOLVE; simulated workflow log.

## Manual checklist

- [ ] Backend `/health` OK
- [ ] Dashboard shows `LIVE`
- [ ] Device ONLINE on dashboard (heartbeats)
- [ ] Phone connected, status visible
- [ ] Crash with phone → phone alert + dashboard incident (`phoneConnected=true`)
- [ ] Phone disconnected → crash → dashboard incident (`phoneConnected=false`)
- [ ] Reconnect → phone history contains both
- [ ] Offline → queued → restore → delivered once
- [ ] Pothole / hard brake → no incident
- [ ] Acknowledge / resolve from dashboard
