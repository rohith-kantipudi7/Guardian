"""HTTP control panel for the virtual Guardian device (used by the dashboard Showcase page).

Run:  python simulator.py serve        ->  http://127.0.0.1:8100

The device keeps sending heartbeats and retrying queued events in the background, exactly
like the firmware's network task. Endpoints mirror the CLI commands:

    GET  /state
    POST /seed
    POST /scenario/<name>          body: {"cancel": false, "noSpeed": false}
    POST /phone/connect | /phone/disconnect
    POST /network/online | /network/offline | /network/delayed
    POST /reset
"""

from __future__ import annotations

import json
import threading
import time
from collections import deque
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from device import VirtualDevice
from network import NetworkMode
from scenarios import SCENARIOS

ALLOWED_ORIGINS = {"http://localhost:5173", "http://127.0.0.1:5173", "http://localhost:4173"}
HEARTBEAT_S = 5.0


class DeviceController:
    def __init__(self, device: VirtualDevice, save_state):
        self.device = device
        self.save_state = save_state
        self.lock = threading.Lock()
        self.log: deque[str] = deque(maxlen=200)
        self.last_outcome: dict | None = None
        # Increments on every scenario run so polling clients (the phone app) can spot new outcomes.
        self.seq = 0
        self.last_heartbeat_ok: bool | None = None
        device.log = self._log

    def _log(self, line: str) -> None:
        self.log.append(f"{time.strftime('%H:%M:%S')} {line.strip()}")

    def state(self) -> dict:
        d = self.device
        return {
            "deviceId": d.cfg.device_id,
            "vehicleId": d.cfg.vehicle_id,
            "phoneConnected": d.state.phone_connected,
            "network": d.network.mode.value,
            "battery": d.state.battery,
            "pendingEvents": len(d.store.pending()),
            "lastHeartbeatOk": self.last_heartbeat_ok,
            "lastOutcome": self.last_outcome,
            "seq": self.seq,
            "scenarios": list(SCENARIOS),
            "log": list(self.log)[-40:],
        }

    def heartbeat_loop(self) -> None:
        while True:
            with self.lock:
                self.last_heartbeat_ok = self.device.heartbeat()
                self.device.flush()
            time.sleep(HEARTBEAT_S)

    def run(self, action: str, body: dict) -> dict:
        d = self.device
        with self.lock:
            parts = action.strip("/").split("/")
            if parts == ["seed"]:
                from simulator import cmd_seed  # local import avoids a cycle at module load

                cmd_seed(d)
            elif parts == ["reset"]:
                d.store.clear()
                d.state.phone_connected = True
                d.network.mode = NetworkMode.ONLINE
                self.last_outcome = None
                self.log.clear()
                self._log("device reset")
            elif len(parts) == 2 and parts[0] == "phone" and parts[1] in ("connect", "disconnect"):
                d.state.phone_connected = parts[1] == "connect"
                self._log(f"[BLE] phone {'CONNECTED' if d.state.phone_connected else 'DISCONNECTED'}")
                self.last_heartbeat_ok = d.heartbeat()
            elif len(parts) == 2 and parts[0] == "network" and parts[1] in ("online", "offline", "delayed"):
                d.network.mode = NetworkMode(parts[1].upper())
                self._log(f"[cellular] {d.network.mode.value}")
                if d.network.mode != NetworkMode.OFFLINE:
                    self.last_heartbeat_ok = d.heartbeat()
                    d.flush(force=True)
            elif len(parts) == 2 and parts[0] == "scenario" and parts[1] in SCENARIOS:
                self._log(f"=== scenario {parts[1]} ===")
                if d.network.connected:
                    d.register()
                out = d.run_scenario(
                    parts[1], cancel_verification=bool(body.get("cancel")), with_speed=not body.get("noSpeed")
                )
                r = out.result
                self.seq += 1
                self.last_outcome = {
                    "scenario": out.scenario,
                    "emergency": out.emergency,
                    "delivered": out.delivered,
                    "eventId": out.event["eventId"] if out.event else None,
                    "severity": r.severity.value if r else None,
                    "confidence": r.confidence if r else None,
                    "reasons": r.reasons if r else [],
                    "accelerationPeakG": r.acceleration_peak_g if r else None,
                    "rotationPeakRadS": r.rotation_peak_rad_s if r else None,
                    "transitions": out.transitions,
                    "trace": out.trace,
                    "phoneConnected": d.state.phone_connected,
                    "network": d.network.mode.value,
                }
            else:
                raise KeyError(action)
            self.save_state(d.state, d.network.mode)
            return self.state()


def make_handler(controller: DeviceController):
    class Handler(BaseHTTPRequestHandler):
        def _cors(self) -> None:
            origin = self.headers.get("Origin")
            if origin in ALLOWED_ORIGINS:
                self.send_header("Access-Control-Allow-Origin", origin)
                self.send_header("Vary", "Origin")
            self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            self.send_header("Access-Control-Allow-Headers", "Content-Type")

        def _json(self, code: int, payload: dict) -> None:
            data = json.dumps(payload).encode()
            self.send_response(code)
            self._cors()
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def do_OPTIONS(self) -> None:  # noqa: N802
            self.send_response(204)
            self._cors()
            self.end_headers()

        def do_GET(self) -> None:  # noqa: N802
            if self.path.rstrip("/") in ("", "/state"):
                self._json(200, controller.state())
            else:
                self._json(404, {"detail": "not found"})

        def do_POST(self) -> None:  # noqa: N802
            length = int(self.headers.get("Content-Length") or 0)
            try:
                body = json.loads(self.rfile.read(length) or b"{}") if length else {}
            except json.JSONDecodeError:
                return self._json(400, {"detail": "invalid JSON"})
            try:
                self._json(200, controller.run(self.path, body))
            except KeyError:
                self._json(404, {"detail": f"unknown action {self.path}"})

        def log_message(self, fmt: str, *args) -> None:
            pass

    return Handler


def serve(device: VirtualDevice, save_state, host: str = "127.0.0.1", port: int = 8100) -> None:
    controller = DeviceController(device, save_state)
    device.register()
    threading.Thread(target=controller.heartbeat_loop, daemon=True).start()
    server = ThreadingHTTPServer((host, port), make_handler(controller))
    print(f"Guardian virtual device control panel on http://{host}:{port}  (Ctrl+C to stop)")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
