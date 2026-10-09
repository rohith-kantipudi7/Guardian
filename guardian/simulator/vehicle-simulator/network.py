"""Transport + network simulator (ONLINE / OFFLINE / DELAYED).

The simulated device talks to the backend exactly like the firmware's cellular path:
HTTP JSON with an X-Device-Secret header. No phone is involved.
"""

from __future__ import annotations

import json
import time
import urllib.error
import urllib.request
from enum import Enum
from typing import Protocol


class NetworkMode(str, Enum):
    ONLINE = "ONLINE"
    OFFLINE = "OFFLINE"
    DELAYED = "DELAYED"


class NetworkError(Exception):
    """Connectivity failure — the request may or may not have reached the server."""


class Transport(Protocol):
    def request(self, method: str, path: str, body: dict | None, headers: dict[str, str]) -> tuple[int, dict]: ...


class HttpTransport:
    def __init__(self, base_url: str, timeout_s: float = 10.0):
        self.base_url = base_url.rstrip("/")
        self.timeout_s = timeout_s

    def request(self, method: str, path: str, body: dict | None, headers: dict[str, str]) -> tuple[int, dict]:
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(
            self.base_url + path, data=data, method=method, headers={"Content-Type": "application/json", **headers}
        )
        try:
            with urllib.request.urlopen(req, timeout=self.timeout_s) as res:
                return res.status, json.loads(res.read() or b"{}")
        except urllib.error.HTTPError as e:
            try:
                payload = json.loads(e.read() or b"{}")
            except json.JSONDecodeError:
                payload = {}
            return e.code, payload
        except (urllib.error.URLError, TimeoutError, ConnectionError, OSError) as e:
            raise NetworkError(str(e)) from e


class SimulatedNetwork:
    """Wraps a real transport and applies the simulated cellular condition."""

    def __init__(self, inner: Transport, mode: NetworkMode = NetworkMode.ONLINE, delay_s: float = 3.0):
        self.inner = inner
        self.mode = mode
        self.delay_s = delay_s

    @property
    def connected(self) -> bool:
        return self.mode != NetworkMode.OFFLINE

    def request(self, method: str, path: str, body: dict | None, headers: dict[str, str]) -> tuple[int, dict]:
        if self.mode == NetworkMode.OFFLINE:
            raise NetworkError("cellular offline (simulated)")
        if self.mode == NetworkMode.DELAYED:
            time.sleep(self.delay_s)
        return self.inner.request(method, path, body, headers)
