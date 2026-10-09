#!/usr/bin/env python3
"""Guardian vehicle/device simulator CLI.

Acts as a Guardian device talking to the backend over its own (simulated) cellular link.
State (phone link, network mode, event queue) persists between invocations, so you can
toggle conditions from one terminal and trigger crashes from another.

Examples:
    python simulator.py seed
    python simulator.py severe_crash
    python simulator.py phone_disconnect
    python simulator.py severe_crash          # still reaches the dashboard
    python simulator.py network_offline
    python simulator.py severe_crash          # queued locally
    python simulator.py network_restore       # delivered exactly once
    python simulator.py demo                  # full scripted judge demo
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
from dataclasses import asdict
from pathlib import Path

from crash_engine import CrashConfig
from device import DeviceConfig, DeviceState, VirtualDevice
from event_store import EventStore
from network import HttpTransport, NetworkError, NetworkMode, SimulatedNetwork
from scenarios import SCENARIOS

HERE = Path(__file__).resolve().parent
STATE_FILE = HERE / ".sim_state.json"
QUEUE_FILE = HERE / ".sim_queue.json"
CRASH_CONFIG_FILE = HERE / "crash_config.json"


def load_dotenv() -> None:
    for path in (HERE / ".env", HERE.parent.parent / ".env"):
        if path.exists():
            for line in path.read_text().splitlines():
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, v = line.split("=", 1)
                    os.environ.setdefault(k.strip(), v.strip())


def load_state() -> tuple[DeviceState, NetworkMode]:
    if STATE_FILE.exists():
        data = json.loads(STATE_FILE.read_text())
        mode = NetworkMode(data.pop("network_mode", "ONLINE"))
        return DeviceState(**data), mode
    return DeviceState(), NetworkMode.ONLINE


def save_state(state: DeviceState, mode: NetworkMode) -> None:
    STATE_FILE.write_text(json.dumps({**asdict(state), "network_mode": mode.value}, indent=2))


def build_device() -> VirtualDevice:
    state, mode = load_state()
    cfg = DeviceConfig(
        device_id=os.environ.get("DEVICE_ID", "GD-001"),
        device_secret=os.environ.get("DEVICE_SECRET", "dev-secret-change-me"),
        vehicle_id=os.environ.get("VEHICLE_ID", "VH-001"),
    )
    # 127.0.0.1 avoids a ~2 s IPv6 "localhost" fallback per request on Windows.
    network = SimulatedNetwork(HttpTransport(os.environ.get("BACKEND_URL", "http://127.0.0.1:8000")), mode)
    return VirtualDevice(cfg, state, network, EventStore(QUEUE_FILE), CrashConfig.load(CRASH_CONFIG_FILE))


def banner(text: str) -> None:
    print(f"\n=== {text} ===")


def show_status(dev: VirtualDevice) -> None:
    pending = dev.store.pending()
    print(f"device      {dev.cfg.device_id}  vehicle {dev.cfg.vehicle_id}")
    print(f"phone       {'CONNECTED' if dev.state.phone_connected else 'DISCONNECTED'}")
    print(f"cellular    {dev.network.mode.value}")
    print(f"queue       {len(pending)} pending / {len(dev.store.items)} total")
    for item in pending:
        print(f"  - {item.event_id} attempts={item.attempts} last_error={item.last_error}")


def cmd_seed(dev: VirtualDevice) -> None:
    banner("Seeding demo user, vehicle and device")
    try:
        code, _ = dev.network.request(
            "POST",
            "/api/users",
            {
                "id": "USR-001",
                "name": "Demo Driver",
                "phone": "+910000000000",
                "emergencyContacts": [{"name": "Demo Contact", "phone": "+910000000001", "relationship": "Family"}],
            },
            {},
        )
        print(f"  user USR-001: {'created' if code == 201 else 'exists' if code == 409 else code}")
        code, _ = dev.network.request(
            "POST",
            "/api/vehicles",
            {
                "id": dev.cfg.vehicle_id,
                "userId": "USR-001",
                "registrationNumber": "KA01GD0001",
                "make": "Demo",
                "model": "Test Vehicle",
                "year": 2024,
                "deviceId": dev.cfg.device_id,
            },
            {},
        )
        print(f"  vehicle {dev.cfg.vehicle_id}: {code}")
    except NetworkError as e:
        sys.exit(f"Backend unreachable: {e}")
    print(f"  device {dev.cfg.device_id}: {'registered' if dev.register() else 'FAILED'}")
    dev.heartbeat()


def cmd_scenario(dev: VirtualDevice, name: str, cancel: bool, no_speed: bool) -> None:
    banner(f"Scenario: {name}  (phone {'CONNECTED' if dev.state.phone_connected else 'DISCONNECTED'}, "
           f"cellular {dev.network.mode.value})")
    if dev.network.connected:
        dev.register()
        dev.heartbeat()
    out = dev.run_scenario(name, cancel_verification=cancel, with_speed=not no_speed)
    print(f"  states: {' -> '.join(out.transitions) or 'NORMAL'}")
    if out.emergency:
        status = "DELIVERED to backend" if out.delivered else "QUEUED on device"
        print(f"  event {out.event['eventId']}: {status}")  # type: ignore[index]


def cmd_phone_crash(dev: VirtualDevice, phone_can_send: bool) -> None:
    label = "phone PRIMARY (phone sends)" if phone_can_send else "phone DESTROYED (device black box sends)"
    banner(f"Crash with on-device AI + black box - {label}")
    if dev.network.connected:
        dev.register()
        dev.heartbeat()
    dev.crash_with_phone("severe_crash", phone_can_send=phone_can_send)


def set_phone(dev: VirtualDevice, connected: bool) -> None:
    dev.state.phone_connected = connected
    banner(f"Phone {'CONNECTED' if connected else 'DISCONNECTED'} (BLE companion only)")
    if dev.network.connected:
        dev.heartbeat()


def set_network(dev: VirtualDevice, mode: NetworkMode) -> None:
    dev.network.mode = mode
    banner(f"Cellular {mode.value}")
    if mode != NetworkMode.OFFLINE:
        dev.heartbeat()
        delivered = dev.flush(force=True)
        if delivered:
            print(f"  flushed {len(delivered)} queued event(s)")


def cmd_run(dev: VirtualDevice, interval: float) -> None:
    banner(f"Device loop: heartbeat every {interval:.0f}s (Ctrl+C to stop)")
    dev.register()
    try:
        while True:
            state, mode = load_state()
            dev.state, dev.network.mode = state, mode
            dev.store = EventStore(QUEUE_FILE)
            ok = dev.heartbeat()
            dev.flush()
            print(f"  {time.strftime('%H:%M:%S')} heartbeat {'ok' if ok else 'failed'} "
                  f"phone={'on' if state.phone_connected else 'off'} cellular={mode.value} "
                  f"pending={len(dev.store.pending())}")
            time.sleep(interval)
    except KeyboardInterrupt:
        pass


def cmd_demo(dev: VirtualDevice, pause: float, step: bool) -> None:
    def wait(msg: str) -> None:
        print(f"\n>>> {msg}")
        if step:
            input("    [Enter] to continue")
        else:
            time.sleep(pause)

    dev.store.clear()
    dev.network.mode = NetworkMode.ONLINE
    dev.state.phone_connected = True
    cmd_seed(dev)
    wait("1-6. Backend, dashboard and device online; phone connected over BLE. Normal driving.")
    cmd_scenario(dev, "normal", False, False)
    wait("7-10. Controlled SEVERE CRASH with phone connected.")
    cmd_scenario(dev, "severe_crash", False, False)
    wait("11. Disconnect the phone (phone destroyed / off).")
    set_phone(dev, False)
    wait("12-15. Another SEVERE CRASH - device reports directly over cellular.")
    cmd_scenario(dev, "severe_crash", False, False)
    wait("16-18. Phone PRIMARY: on-device AI understands the crash, captures a black box, phone sends the alert.")
    set_phone(dev, True)
    cmd_phone_crash(dev, phone_can_send=True)
    wait("19-21. Phone DESTROYED mid-crash: the device black box keeps the evidence and sends the alert.")
    cmd_phone_crash(dev, phone_can_send=False)
    wait("Bonus: cellular outage -> queue -> restore -> exactly-once delivery.")
    set_network(dev, NetworkMode.OFFLINE)
    cmd_scenario(dev, "severe_crash", False, False)
    set_network(dev, NetworkMode.ONLINE)
    wait("Bonus: false-positive guard (pothole + hard brake) -> no emergency.")
    cmd_scenario(dev, "pothole", False, False)
    cmd_scenario(dev, "hard_brake", False, False)
    wait("Recovery: reconnect the phone; app syncs history over BLE + backend.")
    set_phone(dev, True)
    banner("Demo complete - \"The phone is gone. The emergency signal isn't.\"")


def main(argv: list[str] | None = None) -> None:
    load_dotenv()
    p = argparse.ArgumentParser(description="Guardian vehicle/device simulator")
    commands = [*SCENARIOS, "seed", "phone_disconnect", "phone_connect", "phone_crash", "phone_dead_crash",
                "network_offline", "network_delayed", "network_restore", "heartbeat", "flush", "status", "run",
                "demo", "reset", "serve"]
    p.add_argument("command", choices=commands)
    p.add_argument("--cancel", action="store_true", help="press I'M SAFE during verification")
    p.add_argument("--no-speed", action="store_true", help="simulate missing vehicle speed (no OBD/GNSS speed)")
    p.add_argument("--interval", type=float, default=5.0, help="heartbeat interval for 'run'")
    p.add_argument("--pause", type=float, default=3.0, help="pause between demo steps")
    p.add_argument("--step", action="store_true", help="wait for Enter between demo steps")
    p.add_argument("--port", type=int, default=8100, help="control panel port for 'serve'")
    p.add_argument("--host", default="127.0.0.1", help="bind address for 'serve' (0.0.0.0 = reachable from a real phone)")
    args = p.parse_args(argv)

    dev = build_device()
    c = args.command
    if c in SCENARIOS:
        cmd_scenario(dev, c, args.cancel, args.no_speed)
    elif c == "seed":
        cmd_seed(dev)
    elif c == "phone_disconnect":
        set_phone(dev, False)
    elif c == "phone_connect":
        set_phone(dev, True)
    elif c == "phone_crash":
        cmd_phone_crash(dev, phone_can_send=True)
    elif c == "phone_dead_crash":
        cmd_phone_crash(dev, phone_can_send=False)
    elif c == "network_offline":
        set_network(dev, NetworkMode.OFFLINE)
    elif c == "network_delayed":
        set_network(dev, NetworkMode.DELAYED)
    elif c == "network_restore":
        set_network(dev, NetworkMode.ONLINE)
    elif c == "heartbeat":
        print("heartbeat", "ok" if dev.heartbeat() else "failed")
    elif c == "flush":
        print(f"delivered {len(dev.flush(force=True))}")
    elif c == "status":
        show_status(dev)
    elif c == "run":
        cmd_run(dev, args.interval)
    elif c == "demo":
        cmd_demo(dev, args.pause, args.step)
    elif c == "serve":
        from control_server import serve

        serve(dev, save_state, host=args.host, port=args.port)
    elif c == "reset":
        dev.store.clear()
        dev.state, dev.network.mode = DeviceState(), NetworkMode.ONLINE
        print("simulator state reset")

    if c not in ("run", "serve"):
        save_state(dev.state, dev.network.mode)


if __name__ == "__main__":
    main()
