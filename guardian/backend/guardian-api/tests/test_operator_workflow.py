from tests.conftest import AUTH, crash_payload


def test_acknowledge_records_operator_and_timeline(registered):
    registered.post("/api/crashes", json=crash_payload(), headers=AUTH)
    r = registered.post("/api/crashes/EVT-001/acknowledge", json={"operator": "Priya (ERC)"})
    body = r.json()
    assert body["status"] == "ACKNOWLEDGED"
    assert body["acknowledgedBy"] == "Priya (ERC)"
    assert body["acknowledgedAt"].endswith("Z")
    assert body["actions"][-1]["operator"] == "Priya (ERC)"


def test_transitions_still_work_without_body(registered):
    registered.post("/api/crashes", json=crash_payload(), headers=AUTH)
    assert registered.post("/api/crashes/EVT-001/cancel").json()["closedBy"] == "occupant"


def test_operator_actions_and_resolution(registered):
    registered.post("/api/crashes", json=crash_payload(), headers=AUTH)
    r = registered.post(
        "/api/crashes/EVT-001/actions", json={"kind": "AMBULANCE_DISPATCHED", "operator": "Priya (ERC)"}
    )
    assert r.status_code == 201
    last = r.json()["actions"][-1]
    assert last["channel"] == "HOSPITAL" and "SIMULATED" in last["message"]

    r = registered.post(
        "/api/crashes/EVT-001/resolve", json={"operator": "Priya (ERC)", "note": "Driver taken to hospital"}
    )
    body = r.json()
    assert body["status"] == "RESOLVED"
    assert body["closedBy"] == "Priya (ERC)"
    assert body["resolution"] == "Driver taken to hospital"
    # Closed incidents accept no further dispatch actions.
    r = registered.post("/api/crashes/EVT-001/actions", json={"kind": "NOTE", "operator": "x", "message": "late"})
    assert r.status_code == 409


def test_operator_action_validation(registered):
    registered.post("/api/crashes", json=crash_payload(), headers=AUTH)
    assert registered.post("/api/crashes/EVT-001/actions", json={"kind": "LAUNCH_ROCKET", "operator": "x"}).status_code == 422
    assert registered.post("/api/crashes/EVT-001/actions", json={"kind": "NOTE"}).status_code == 422


def test_demo_reset_clears_incidents_only(registered):
    registered.post("/api/crashes", json=crash_payload(), headers=AUTH)
    r = registered.post("/api/demo/reset")
    assert r.status_code == 200 and r.json()["removedIncidents"] == 1
    assert registered.get("/api/crashes").json() == []
    assert registered.get("/api/devices/GD-001").status_code == 200


def test_stats(registered):
    registered.post("/api/crashes", json=crash_payload(eventId="A", phoneConnected=False), headers=AUTH)
    registered.post("/api/crashes", json=crash_payload(eventId="B", phoneConnected=True), headers=AUTH)
    registered.post("/api/crashes/A/acknowledge", json={"operator": "op"})
    s = registered.get("/api/stats").json()
    assert s["activeIncidents"] == 2
    assert s["criticalActive"] == 2
    assert s["phoneIndependentIncidents"] == 1
    assert s["devicesOnline"] == 1
    assert s["avgAcknowledgeSeconds"] is not None
