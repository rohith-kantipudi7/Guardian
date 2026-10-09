"""Persistent crash-event queue with retry and exactly-once delivery semantics.

Delivery rule (shared with firmware): an event is marked SENT when the backend answers
201 (accepted) or 200 with duplicate=true (it already has it). Connectivity errors and 5xx
are retried with exponential backoff. Other 4xx are permanent failures.
"""

from __future__ import annotations

import json
import time
from dataclasses import asdict, dataclass, field
from pathlib import Path

from network import NetworkError, Transport

PENDING, SENT, FAILED = "PENDING", "SENT", "FAILED"
MAX_BACKOFF_S = 60.0


@dataclass
class QueuedEvent:
    event: dict
    status: str = PENDING
    attempts: int = 0
    next_attempt_at: float = 0.0
    last_error: str | None = None
    server_status: str | None = None
    history: list[str] = field(default_factory=list)

    @property
    def event_id(self) -> str:
        return self.event["eventId"]


class EventStore:
    def __init__(self, path: Path | None = None, clock=time.time):
        self.path = path
        self.clock = clock
        self.items: list[QueuedEvent] = []
        if path and path.exists():
            self.items = [QueuedEvent(**d) for d in json.loads(path.read_text())]

    def _persist(self) -> None:
        if self.path:
            self.path.write_text(json.dumps([asdict(i) for i in self.items], indent=2))

    def enqueue(self, event: dict) -> QueuedEvent:
        existing = self.get(event["eventId"])
        if existing:
            return existing
        item = QueuedEvent(event=event, next_attempt_at=self.clock())
        self.items.append(item)
        self._persist()
        return item

    def get(self, event_id: str) -> QueuedEvent | None:
        return next((i for i in self.items if i.event_id == event_id), None)

    def pending(self) -> list[QueuedEvent]:
        return [i for i in self.items if i.status == PENDING]

    def flush(self, transport: Transport, headers: dict[str, str], force: bool = False) -> list[QueuedEvent]:
        """Try to send every due pending event. Returns the events that were delivered now."""
        delivered: list[QueuedEvent] = []
        now = self.clock()
        for item in self.pending():
            if not force and item.next_attempt_at > now:
                continue
            item.attempts += 1
            try:
                code, body = transport.request("POST", "/api/crashes", item.event, headers)
            except NetworkError as e:
                self._backoff(item, f"network: {e}")
                continue
            if code == 201 or (code == 200 and body.get("duplicate")):
                item.status = SENT
                item.server_status = body.get("status")
                item.last_error = None
                item.history.append(f"ACK {code} duplicate={bool(body.get('duplicate'))}")
                delivered.append(item)
            elif code >= 500 or code == 429:
                self._backoff(item, f"server {code}")
            else:
                item.status = FAILED
                item.last_error = f"rejected {code}: {body.get('detail')}"
                item.history.append(item.last_error)
        self._persist()
        return delivered

    def _backoff(self, item: QueuedEvent, reason: str) -> None:
        delay = min(MAX_BACKOFF_S, 2 ** (item.attempts - 1))
        item.next_attempt_at = self.clock() + delay
        item.last_error = reason
        item.history.append(f"retry in {delay:.0f}s ({reason})")

    def clear(self) -> None:
        self.items = []
        self._persist()
