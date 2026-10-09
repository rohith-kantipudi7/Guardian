import pytest

from tests.conftest import AUTH, DEVICE_ID, crash_payload


def test_crash_without_phone_is_accepted_as_emergency(registered):
    """Primary acceptance test: phone disconnected, device reports directly."""
    r = registered.post("/api/crashes", json=crash_payload(phoneConnected=False), headers=AUTH)
    assert r.status_code == 201, r.text
    assert r.json() == {"accepted": True, "duplicate": False, "eventId": "EVT-001", "status": "EMERGENCY_ACTIVE"}

    event = registered.get("/api/crashes/EVT-001").json()
    assert event["phoneConnected"] is False
    assert event["severity"] == "CRITICAL"
    assert event["timestamp"] == "2026-10-05T10:30:00.000Z"
    channels = {a["channel"] for a in event["actions"]}
    assert {"POLICE", "HOSPITAL", "EMERGENCY_CONTACT"} <= channels
    assert all("SIMULATED" in a["message"] or a["status"] == "SKIPPED" for a in event["actions"])

    device = registered.get(f"/api/devices/{DEVICE_ID}").json()
    assert device["online"] is True
    assert device["phoneConnected"] is False


def test_crash_with_phone_connected(registered):
    r = registered.post("/api/crashes", json=crash_payload(eventId="EVT-P1", phoneConnected=True), headers=AUTH)
    assert r.status_code == 201
    assert registered.get("/api/crashes/EVT-P1").json()["phoneConnected"] is True


def test_duplicate_event_is_not_stored_twice(registered):
    assert registered.post("/api/crashes", json=crash_payload(), headers=AUTH).status_code == 201
    r = registered.post("/api/crashes", json=crash_payload(), headers=AUTH)
    assert r.status_code == 200
    assert r.json()["duplicate"] is True and r.json()["accepted"] is False
    assert len(registered.get("/api/crashes").json()) == 1


def test_minor_crash_is_detected_not_emergency(registered):
    r = registered.post(
        "/api/crashes", json=crash_payload(eventId="EVT-M", severity="MINOR", confidence=0.3), headers=AUTH
    )
    assert r.json()["status"] == "DETECTED"
    assert registered.get("/api/crashes/EVT-M").json()["actions"] == []


def test_normal_severity_rejected(registered):
    r = registered.post("/api/crashes", json=crash_payload(severity="NORMAL", confidence=0.05), headers=AUTH)
    assert r.status_code == 422


@pytest.mark.parametrize(
    "override",
    [
        {"latitude": 123.0},
        {"longitude": -200.0},
        {"confidence": 1.5},
        {"severity": "APOCALYPTIC"},
        {"eventId": "bad id with spaces"},
        {"timestamp": "not-a-date"},
        {"speed": -1},
    ],
)
def test_crash_validation(registered, override):
    assert registered.post("/api/crashes", json=crash_payload(**override), headers=AUTH).status_code == 422


def test_crash_requires_device_auth(registered):
    assert registered.post("/api/crashes", json=crash_payload()).status_code == 401
    assert registered.post("/api/crashes", json=crash_payload(deviceId="GD-404"), headers=AUTH).status_code == 401


def test_vehicle_id_defaults_from_device(registered):
    payload = crash_payload(eventId="EVT-NV")
    payload.pop("vehicleId")
    registered.post("/api/crashes", json=payload, headers=AUTH)
    assert registered.get("/api/crashes/EVT-NV").json()["vehicleId"] == "VH-001"


def test_acknowledge_then_resolve(registered):
    registered.post("/api/crashes", json=crash_payload(), headers=AUTH)
    r = registered.post("/api/crashes/EVT-001/acknowledge")
    assert r.status_code == 200 and r.json()["status"] == "ACKNOWLEDGED"
    r = registered.post("/api/crashes/EVT-001/resolve")
    assert r.json()["status"] == "RESOLVED"
    # Terminal: cannot cancel or acknowledge a resolved incident.
    assert registered.post("/api/crashes/EVT-001/cancel").status_code == 409
    assert registered.post("/api/crashes/EVT-001/acknowledge").status_code == 409


def test_cancel_is_idempotent(registered):
    registered.post("/api/crashes", json=crash_payload(), headers=AUTH)
    assert registered.post("/api/crashes/EVT-001/cancel").json()["status"] == "CANCELLED"
    assert registered.post("/api/crashes/EVT-001/cancel").status_code == 200
    assert registered.post("/api/crashes/EVT-001/resolve").status_code == 409
    assert registered.post("/api/crashes/EVT-404/cancel").status_code == 404


def test_list_filters(registered):
    registered.post("/api/crashes", json=crash_payload(eventId="E1"), headers=AUTH)
    registered.post("/api/crashes", json=crash_payload(eventId="E2", timestamp="2026-10-05T11:00:00Z"), headers=AUTH)
    registered.post("/api/crashes/E1/resolve")
    active = registered.get("/api/crashes", params={"active": "true"}).json()
    assert [c["eventId"] for c in active] == ["E2"]
    closed = registered.get("/api/crashes", params={"status": "RESOLVED"}).json()
    assert [c["eventId"] for c in closed] == ["E1"]
    assert len(registered.get("/api/crashes", params={"deviceId": DEVICE_ID}).json()) == 2


def test_websocket_broadcasts_crash_and_update(registered):
    with registered.websocket_connect("/ws/emergency") as ws:
        assert ws.receive_json()["type"] == "CONNECTED"
        registered.post("/api/crashes", json=crash_payload(), headers=AUTH)
        msg = ws.receive_json()
        assert msg["type"] == "CRASH_DETECTED"
        assert msg["event"]["eventId"] == "EVT-001"
        assert msg["event"]["phoneConnected"] is False
        assert msg["event"]["severity"] == "CRITICAL"

        # A duplicate must not be re-broadcast; the next message is the acknowledge update.
        registered.post("/api/crashes", json=crash_payload(), headers=AUTH)
        registered.post("/api/crashes/EVT-001/acknowledge")
        msg = ws.receive_json()
        assert msg["type"] == "CRASH_UPDATED"
        assert msg["event"]["status"] == "ACKNOWLEDGED"
