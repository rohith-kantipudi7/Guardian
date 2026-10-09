from tests.conftest import AUTH, DEVICE_ID, DEVICE_SECRET


def test_health(client):
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json()["status"] == "ok"


def test_create_and_get_user_with_contacts(client):
    r = client.post(
        "/api/users",
        json={
            "id": "USR-1",
            "name": "Ravi",
            "phone": "+919999999999",
            "emergencyContacts": [{"name": "Asha", "phone": "+918888888888", "relationship": "Spouse"}],
        },
    )
    assert r.status_code == 201, r.text
    body = client.get("/api/users/USR-1").json()
    assert body["emergencyContacts"][0]["relationship"] == "Spouse"
    assert client.post("/api/users", json={"id": "USR-1", "name": "X", "phone": "123"}).status_code == 409
    assert client.get("/api/users/nope").status_code == 404


def test_vehicle_registration(client):
    r = client.post("/api/vehicles", json={"id": "VH-001", "registrationNumber": "KA01AB1234", "year": 2024})
    assert r.status_code == 201
    assert r.json()["registrationNumber"] == "KA01AB1234"
    r = client.post("/api/vehicles", json={"id": "VH-001", "registrationNumber": "KA01ZZ0001"})
    assert r.status_code == 200
    assert client.get("/api/vehicles/VH-001").json()["registrationNumber"] == "KA01ZZ0001"
    assert client.get("/api/vehicles/VH-404").status_code == 404


def test_device_registration_and_secret_protection(client):
    r = client.post("/api/devices/register", json={"deviceId": DEVICE_ID, "deviceSecret": DEVICE_SECRET})
    assert r.status_code == 201
    assert "deviceSecret" not in r.json() and "secretHash" not in r.json()
    assert client.post("/api/devices/register", json={"deviceId": DEVICE_ID, "deviceSecret": DEVICE_SECRET}).status_code == 200
    hijack = client.post("/api/devices/register", json={"deviceId": DEVICE_ID, "deviceSecret": "other-secret"})
    assert hijack.status_code == 401
    assert client.get(f"/api/devices/{DEVICE_ID}").json()["online"] is False


def test_heartbeat_updates_state(registered):
    hb = {
        "deviceId": DEVICE_ID,
        "timestamp": "2026-10-05T10:30:00Z",
        "batteryLevel": 91,
        "gpsStatus": "AVAILABLE",
        "cellularStatus": "CONNECTED",
        "bleStatus": "CONNECTED",
        "phoneConnected": True,
        "vehicleConnected": True,
    }
    r = registered.post(f"/api/devices/{DEVICE_ID}/heartbeat", json=hb, headers=AUTH)
    assert r.status_code == 200, r.text
    d = registered.get(f"/api/devices/{DEVICE_ID}").json()
    assert d["batteryLevel"] == 91
    assert d["online"] is True
    assert d["phoneConnected"] is True
    assert d["lastHeartbeat"].endswith("Z")


def test_heartbeat_requires_secret(registered):
    hb = {"deviceId": DEVICE_ID, "timestamp": "2026-10-05T10:30:00Z"}
    assert registered.post(f"/api/devices/{DEVICE_ID}/heartbeat", json=hb).status_code == 401
    bad = {"X-Device-Secret": "wrong-secret"}
    assert registered.post(f"/api/devices/{DEVICE_ID}/heartbeat", json=hb, headers=bad).status_code == 401


def test_heartbeat_body_must_match_url(registered):
    hb = {"deviceId": "GD-999", "timestamp": "2026-10-05T10:30:00Z"}
    assert registered.post(f"/api/devices/{DEVICE_ID}/heartbeat", json=hb, headers=AUTH).status_code == 422


def test_heartbeat_validation(registered):
    hb = {"deviceId": DEVICE_ID, "timestamp": "2026-10-05T10:30:00Z", "batteryLevel": 150}
    assert registered.post(f"/api/devices/{DEVICE_ID}/heartbeat", json=hb, headers=AUTH).status_code == 422
