import asyncio
import json
import logging
from typing import Any

from fastapi import WebSocket

log = logging.getLogger(__name__)


class EmergencyBroadcaster:
    """Fan-out of emergency events to connected dashboards."""

    def __init__(self) -> None:
        self._clients: set[WebSocket] = set()
        self._lock = asyncio.Lock()

    @property
    def client_count(self) -> int:
        return len(self._clients)

    async def connect(self, ws: WebSocket) -> None:
        await ws.accept()
        async with self._lock:
            self._clients.add(ws)

    async def disconnect(self, ws: WebSocket) -> None:
        async with self._lock:
            self._clients.discard(ws)

    async def broadcast(self, message: dict[str, Any]) -> None:
        payload = json.dumps(message)
        async with self._lock:
            clients = list(self._clients)
        dead: list[WebSocket] = []
        for ws in clients:
            try:
                await ws.send_text(payload)
            except Exception:  # client went away mid-send
                dead.append(ws)
        if dead:
            async with self._lock:
                for ws in dead:
                    self._clients.discard(ws)
            log.info("Dropped %d stale websocket client(s)", len(dead))


broadcaster = EmergencyBroadcaster()
