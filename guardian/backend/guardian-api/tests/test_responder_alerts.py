from tests.conftest import AUTH, crash_payload


def _with_owner(client):
    client.post(
        "/api/users",
        json={
            "id": "USR-1",
            "name": "Ravi",
            "phone": "+910000000000",
            "emergencyContacts": [{"name": "Meera", "phone": "+910000000001", "relationship": "Spouse"}],
        },
    )
    client.post(
        "/api/vehicles",
        json={"id": "VH-001", "userId": "USR-1", "registrationNumber": "KA01AB1234", "deviceId": "GD-001"},
    )
    return client


def _alerts(client) -> dict[str, dict]:
    crash = client.get("/api/crashes/EVT-001").json()
    return {n["recipientType"]: n for n in crash["notifications"]}


def test_crash_automatically_alerts_police_hospital_and_family(registered):
    _with_owner(registered).post("/api/crashes", json=crash_payload(), headers=AUTH)
    alerts = _alerts(registered)
    assert set(alerts) == {"POLICE", "HOSPITAL", "EMERGENCY_CONTACT"}
    sms = alerts["POLICE"]["message"]
    for detail in ("CRITICAL", "KA01AB1234", "Ravi", "+910000000000", "maps.google.com", "/alert/", "NOT reachable"):
        assert detail in sms
    assert alerts["POLICE"]["token"] != alerts["HOSPITAL"]["token"]


def test_alert_link_shows_full_details_and_records_read_receipt(registered):
    _with_owner(registered).post("/api/crashes", json=crash_payload(), headers=AUTH)
    token = _alerts(registered)["HOSPITAL"]["token"]
    view = registered.get(f"/api/alerts/{token}").json()
    assert view["recipientType"] == "HOSPITAL"
    assert view["vehicleRegistration"] == "KA01AB1234"
    assert view["driver"]["phone"] == "+910000000000"
    assert view["emergencyContacts"][0]["relationship"] == "Spouse"
    assert view["mapsUrl"].startswith("https://maps.google.com/")
    assert len(view["responders"]) == 3
    assert _alerts(registered)["HOSPITAL"]["openedAt"] is not None
    # Read receipt is recorded once.
    registered.get(f"/api/alerts/{token}")
    opened = [a for a in registered.get("/api/crashes/EVT-001").json()["actions"] if a["status"] == "OPENED"]
    assert len(opened) == 1


def test_first_open_marks_incident_seen_without_any_click(registered):
    _with_owner(registered).post("/api/crashes", json=crash_payload(), headers=AUTH)
    alerts = _alerts(registered)
    registered.get(f"/api/alerts/{alerts['HOSPITAL']['token']}")
    crash = registered.get("/api/crashes/EVT-001").json()
    assert crash["status"] == "ACKNOWLEDGED" and crash["acknowledgedBy"] == "Nearest hospital / ambulance"
    # A later open by someone else does not change who saw it first.
    registered.get(f"/api/alerts/{alerts['POLICE']['token']}")
    assert registered.get("/api/crashes/EVT-001").json()["acknowledgedBy"] == "Nearest hospital / ambulance"


def test_responders_cannot_change_the_incident(registered):
    _with_owner(registered).post("/api/crashes", json=crash_payload(), headers=AUTH)
    token = _alerts(registered)["POLICE"]["token"]
    assert registered.post(f"/api/alerts/{token}/respond", json={"response": "COMPLETED"}).status_code in (404, 405)
    assert registered.get("/api/crashes/EVT-001").json()["status"] == "EMERGENCY_ACTIVE"


def test_driver_safe_is_visible_to_responders(registered):
    _with_owner(registered).post("/api/crashes", json=crash_payload(), headers=AUTH)
    token = _alerts(registered)["POLICE"]["token"]
    registered.post("/api/crashes/EVT-001/cancel", json={"operator": "Driver (Guardian app)"})
    assert registered.get(f"/api/alerts/{token}").json()["status"] == "CANCELLED"


def test_unknown_token(registered):
    assert registered.get("/api/alerts/not-a-real-token").status_code == 404
