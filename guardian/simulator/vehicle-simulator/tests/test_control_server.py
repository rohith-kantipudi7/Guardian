"""Showcase control panel: virtual device driven over HTTP, reporting into the in-process backend."""

import json
import threading
import urllib.request

import pytest

pytest.importorskip("fastapi")

from tests.test_e2e_backend import env  # noqa: E402, F401  (fixture)

from control_server import DeviceController, make_handler  # noqa: E402
from http.server import ThreadingHTTPServer  # noqa: E402


def test_controller_drives_full_story(env):  # noqa: F811
    client, dev = env
    ctl = DeviceController(dev, lambda *_: None)

    state = ctl.run("/scenario/severe_crash", {})
    out = state["lastOutcome"]
    assert out["emergency"] and out["delivered"] and out["severity"] == "CRITICAL"
    assert len(out["trace"]) > 50 and max(p[1] for p in out["trace"]) > 5

    ctl.run("/phone/disconnect", {})
    out = ctl.run("/scenario/severe_crash", {})["lastOutcome"]
    assert out["phoneConnected"] is False and out["delivered"]

    ctl.run("/network/offline", {})
    assert ctl.run("/scenario/severe_crash", {})["pendingEvents"] == 1
    assert ctl.run("/network/online", {})["pendingEvents"] == 0

    crashes = client.get("/api/crashes").json()
    assert len(crashes) == 3
    assert sorted(c["phoneConnected"] for c in crashes) == [False, False, True]

    with pytest.raises(KeyError):
        ctl.run("/scenario/meteor_strike", {})


def test_http_handler_and_cors(env):  # noqa: F811
    _, dev = env
    ctl = DeviceController(dev, lambda *_: None)
    server = ThreadingHTTPServer(("127.0.0.1", 0), make_handler(ctl))
    threading.Thread(target=server.serve_forever, daemon=True).start()
    base = f"http://127.0.0.1:{server.server_address[1]}"
    try:
        req = urllib.request.Request(base + "/state", headers={"Origin": "http://localhost:5173"})
        with urllib.request.urlopen(req) as res:
            assert res.headers["Access-Control-Allow-Origin"] == "http://localhost:5173"
            assert json.loads(res.read())["deviceId"] == "GD-001"

        req = urllib.request.Request(base + "/scenario/pothole", data=b"{}", method="POST")
        with urllib.request.urlopen(req) as res:
            out = json.loads(res.read())["lastOutcome"]
            assert out["emergency"] is False and out["severity"] == "NORMAL"

        evil = urllib.request.Request(base + "/state", headers={"Origin": "http://evil.example"})
        with urllib.request.urlopen(evil) as res:
            assert res.headers.get("Access-Control-Allow-Origin") is None
    finally:
        server.shutdown()
