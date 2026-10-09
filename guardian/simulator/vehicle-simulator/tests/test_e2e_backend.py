"""End-to-end: virtual device -> backend (in-process) -> WebSocket dashboard feed.

Runs only when the backend dependencies are importable, e.g.:
    ..\\..\\backend\\guardian-api\\.venv\\Scripts\\python -m pytest
"""

import os
import sys
from pathlib import Path

import pytest

BACKEND = Path(__file__).resolve().parents[3] / "backend" / "guardian-api"
pytest.importorskip("fastapi")
os.environ["DATABASE_URL"] = "sqlite://"
os.environ["REQUIRE_DEVICE_AUTH"] = "true"
sys.path.insert(0, str(BACKEND))

from fastapi.testclient import TestClient  # noqa: E402

from app import models as _models  # noqa: E402, F401
from app.database import Base, engine  # noqa: E402
from app.main import app  # noqa: E402
from device import DeviceConfig, DeviceState, VirtualDevice  # noqa: E402
from event_store import EventStore  # noqa: E402
from network import NetworkMode, SimulatedNetwork  # noqa: E402
from simulator import cmd_seed  # noqa: E402


class InProcessTransport:
    def __init__(self, client: TestClient):
        self.client = client

    def request(self, method, path, body, headers):
        r = self.client.request(method, path, json=body, headers=headers)
        return r.status_code, r.json()


@pytest.fixture()
def env(tmp_path):
    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)
    with TestClient(app) as client:
        net = SimulatedNetwork(InProcessTransport(client))
        dev = VirtualDevice(DeviceConfig(), DeviceState(), net, EventStore(tmp_path / "q.json"), log=lambda _: None)
        cmd_seed(dev)
        yield client, dev


def crashes(client):
    return client.get("/api/crashes", params={"deviceId": "GD-001"}).json()


def test_full_guardian_demo_flow(env):
    client, dev = env

    with client.websocket_connect("/ws/emergency") as ws:
        assert ws.receive_json()["type"] == "CONNECTED"

        # Normal driving: no incident.
        assert dev.run_scenario("normal").emergency is False
        assert crashes(client) == []

        # Crash with phone connected.
        out = dev.run_scenario("severe_crash")
        assert out.delivered
        msg = ws.receive_json()
        assert msg["type"] == "CRASH_DETECTED"
        assert msg["event"]["phoneConnected"] is True
        assert msg["event"]["severity"] == "CRITICAL"

        # Killer test: phone disconnected, device reports alone.
        dev.state.phone_connected = False
        dev.heartbeat()
        assert ws.receive_json()["type"] == "DEVICE_HEARTBEAT"
        out = dev.run_scenario("severe_crash")
        assert out.delivered
        msg = ws.receive_json()
        assert msg["type"] == "CRASH_DETECTED"
        assert msg["event"]["phoneConnected"] is False
        assert msg["event"]["status"] == "EMERGENCY_ACTIVE"

    device = client.get("/api/devices/GD-001").json()
    assert device["online"] is True and device["phoneConnected"] is False

    # Recovery: phone reconnects and syncs history from the backend.
    dev.state.phone_connected = True
    history = crashes(client)
    assert len(history) == 2
    assert sorted(c["phoneConnected"] for c in history) == [False, True]


def test_network_outage_delivers_exactly_once(env):
    client, dev = env
    dev.network.mode = NetworkMode.OFFLINE
    out = dev.run_scenario("severe_crash")
    assert out.emergency and not out.delivered
    assert crashes(client) == []

    dev.network.mode = NetworkMode.ONLINE
    assert len(dev.flush(force=True)) == 1
    assert dev.flush(force=True) == []
    # Device-side retry of the same event (e.g. lost ACK) must not duplicate.
    code, body = dev.network.request("POST", "/api/crashes", out.event, dev.headers)
    assert code == 200 and body["duplicate"] is True
    assert len(crashes(client)) == 1


def test_false_positives_do_not_reach_backend(env):
    client, dev = env
    for name in ("pothole", "hard_brake", "minor_impact"):
        assert dev.run_scenario(name).emergency is False
    assert crashes(client) == []


def test_verification_cancel_sends_nothing(env):
    client, dev = env
    out = dev.run_scenario("rollover", cancel_verification=True)
    assert out.emergency is False
    assert "CANCELLED" in out.transitions
    assert crashes(client) == []


def test_im_safe_after_transmission_cancels_incident(env):
    client, dev = env
    out = dev.run_scenario("severe_crash")
    r = client.post(f"/api/crashes/{out.event['eventId']}/cancel")
    assert r.json()["status"] == "CANCELLED"
