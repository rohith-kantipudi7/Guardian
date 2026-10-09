import os

# Must be set before the app (and its engine) is imported.
os.environ["DATABASE_URL"] = "sqlite://"
os.environ["REQUIRE_DEVICE_AUTH"] = "true"

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

from app import models as _models  # noqa: E402, F401  (registers tables)
from app.database import Base, engine  # noqa: E402
from app.main import app  # noqa: E402

DEVICE_ID = "GD-001"
DEVICE_SECRET = "test-secret-123"
AUTH = {"X-Device-Secret": DEVICE_SECRET}


@pytest.fixture()
def client():
    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)
    with TestClient(app) as c:
        yield c


@pytest.fixture()
def registered(client):
    client.post(
        "/api/vehicles",
        json={"id": "VH-001", "registrationNumber": "KA01AB1234", "make": "Demo", "model": "Car", "deviceId": DEVICE_ID},
    )
    r = client.post(
        "/api/devices/register",
        json={"deviceId": DEVICE_ID, "deviceSecret": DEVICE_SECRET, "firmwareVersion": "0.1.0", "vehicleId": "VH-001"},
    )
    assert r.status_code == 201, r.text
    return client


def crash_payload(**overrides) -> dict:
    payload = {
        "eventId": "EVT-001",
        "deviceId": DEVICE_ID,
        "vehicleId": "VH-001",
        "timestamp": "2026-10-05T10:30:00Z",
        "latitude": 12.9716,
        "longitude": 77.5946,
        "speed": 72.0,
        "accelerationPeak": 8.4,
        "rotationPeak": 4.2,
        "confidence": 0.96,
        "severity": "CRITICAL",
        "phoneConnected": False,
        "cellularConnected": True,
    }
    payload.update(overrides)
    return payload
