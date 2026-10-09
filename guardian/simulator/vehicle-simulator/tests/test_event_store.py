from event_store import FAILED, PENDING, SENT, EventStore
from network import NetworkError, NetworkMode, SimulatedNetwork


class FakeBackend:
    """In-memory backend implementing the dedup contract of POST /api/crashes."""

    def __init__(self):
        self.stored: dict[str, dict] = {}
        self.fail_after_store = 0  # simulate lost ACKs
        self.status_code: int | None = None

    def request(self, method, path, body, headers):
        if self.status_code:
            return self.status_code, {"detail": "forced"}
        if body["eventId"] in self.stored:
            return 200, {"accepted": False, "duplicate": True, "eventId": body["eventId"], "status": "EMERGENCY_ACTIVE"}
        self.stored[body["eventId"]] = body
        if self.fail_after_store:
            self.fail_after_store -= 1
            raise NetworkError("timeout waiting for response")
        return 201, {"accepted": True, "duplicate": False, "eventId": body["eventId"], "status": "EMERGENCY_ACTIVE"}


class Clock:
    def __init__(self):
        self.t = 1000.0

    def __call__(self):
        return self.t


def ev(i: int) -> dict:
    return {"eventId": f"EVT-{i}", "deviceId": "GD-001", "severity": "CRITICAL"}


def test_offline_queue_then_exactly_once_delivery(tmp_path):
    backend, clock = FakeBackend(), Clock()
    net = SimulatedNetwork(backend, NetworkMode.OFFLINE)
    store = EventStore(tmp_path / "q.json", clock=clock)
    store.enqueue(ev(1))

    assert store.flush(net, {}) == []
    item = store.get("EVT-1")
    assert item.status == PENDING and item.attempts == 1

    # Not due yet -> no attempt.
    net.mode = NetworkMode.ONLINE
    assert store.flush(net, {}) == []
    clock.t += 5
    delivered = store.flush(net, {})
    assert [d.event_id for d in delivered] == ["EVT-1"]
    assert len(backend.stored) == 1
    assert store.flush(net, {}, force=True) == []  # nothing pending anymore


def test_lost_ack_is_retried_and_deduplicated(tmp_path):
    backend, clock = FakeBackend(), Clock()
    backend.fail_after_store = 1
    store = EventStore(tmp_path / "q.json", clock=clock)
    store.enqueue(ev(2))

    store.flush(backend, {})
    assert store.get("EVT-2").status == PENDING  # server stored it, device never saw ACK
    clock.t += 10
    store.flush(backend, {})
    item = store.get("EVT-2")
    assert item.status == SENT
    assert "duplicate=True" in item.history[-1]
    assert len(backend.stored) == 1


def test_queue_survives_restart(tmp_path):
    path = tmp_path / "q.json"
    store = EventStore(path)
    store.enqueue(ev(3))
    store.enqueue(ev(3))  # same id is not queued twice
    reloaded = EventStore(path)
    assert [i.event_id for i in reloaded.pending()] == ["EVT-3"]


def test_backoff_grows_and_is_capped(tmp_path):
    clock = Clock()
    net = SimulatedNetwork(FakeBackend(), NetworkMode.OFFLINE)
    store = EventStore(tmp_path / "q.json", clock=clock)
    store.enqueue(ev(4))
    delays = []
    for _ in range(9):
        store.flush(net, {}, force=True)
        delays.append(store.get("EVT-4").next_attempt_at - clock.t)
    assert delays[:4] == [1, 2, 4, 8]
    assert max(delays) == 60


def test_validation_rejection_is_permanent(tmp_path):
    backend = FakeBackend()
    backend.status_code = 422
    store = EventStore(tmp_path / "q.json")
    store.enqueue(ev(5))
    store.flush(backend, {}, force=True)
    assert store.get("EVT-5").status == FAILED


def test_server_error_is_retried(tmp_path):
    backend = FakeBackend()
    backend.status_code = 503
    store = EventStore(tmp_path / "q.json")
    store.enqueue(ev(6))
    store.flush(backend, {}, force=True)
    assert store.get("EVT-6").status == PENDING
