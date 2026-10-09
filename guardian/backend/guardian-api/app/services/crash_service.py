from __future__ import annotations

from dataclasses import dataclass
from datetime import timedelta

from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.config import get_settings
from app.domain import ACTIVE_STATUSES, ActionChannel, CrashStatus, Severity, utcnow
from app.models import BlackBoxEvidence, CrashEvent
from app.repositories import CrashRepository, DeviceRepository
from app.schemas import BlackBoxUpload, CrashCreate, EvidenceIn, OperatorActionCreate, OperatorActionKind, StatsOut
from app.services.device_service import DeviceService, is_online
from app.services.emergency_service import EmergencyService
from app.services.errors import ConflictError, InvalidRequestError, NotFoundError

EMERGENCY_SEVERITIES = {Severity.MODERATE, Severity.CRITICAL}

# Response-center quick actions. All SIMULATED: nothing is actually dispatched.
OPERATOR_ACTIONS: dict[OperatorActionKind, tuple[ActionChannel, str]] = {
    OperatorActionKind.CALLED_DRIVER: (ActionChannel.DRIVER, "Called the driver"),
    OperatorActionKind.CALLED_EMERGENCY_CONTACT: (ActionChannel.EMERGENCY_CONTACT, "Called the emergency contact"),
    OperatorActionKind.AMBULANCE_DISPATCHED: (ActionChannel.HOSPITAL, "[SIMULATED] Ambulance dispatched to location"),
    OperatorActionKind.POLICE_NOTIFIED: (ActionChannel.POLICE, "[SIMULATED] Police informed"),
    OperatorActionKind.TOW_REQUESTED: (ActionChannel.TOW, "[SIMULATED] Tow / roadside assistance requested"),
    OperatorActionKind.NOTE: (ActionChannel.RESPONSE_CENTER, "Note"),
}

# target status -> statuses it may be entered from
TRANSITIONS: dict[CrashStatus, set[CrashStatus]] = {
    CrashStatus.ACKNOWLEDGED: {CrashStatus.DETECTED, CrashStatus.VERIFYING, CrashStatus.EMERGENCY_ACTIVE},
    CrashStatus.CANCELLED: {
        CrashStatus.DETECTED,
        CrashStatus.VERIFYING,
        CrashStatus.EMERGENCY_ACTIVE,
        CrashStatus.ACKNOWLEDGED,
    },
    CrashStatus.RESOLVED: {CrashStatus.DETECTED, CrashStatus.EMERGENCY_ACTIVE, CrashStatus.ACKNOWLEDGED},
}


@dataclass
class CreateResult:
    crash: CrashEvent
    created: bool


def initial_status(severity: Severity) -> CrashStatus:
    return CrashStatus.EMERGENCY_ACTIVE if severity in EMERGENCY_SEVERITIES else CrashStatus.DETECTED


class CrashService:
    def __init__(self, db: Session):
        self.db = db
        self.repo = CrashRepository(db)
        self.devices = DeviceRepository(db)
        self.device_service = DeviceService(db)
        self.emergency = EmergencyService(db)

    def create(self, req: CrashCreate, device_secret: str | None) -> CreateResult:
        device = self.device_service.authenticate(req.device_id, device_secret)

        existing = self.repo.get_by_event_id(req.event_id)
        if existing is not None:
            # Idempotent retry from the device: never store or broadcast twice.
            return CreateResult(existing, created=False)

        if req.severity == Severity.NORMAL:
            raise InvalidRequestError("NORMAL severity is not a crash event")

        crash = CrashEvent(
            event_id=req.event_id,
            device_id=req.device_id,
            vehicle_id=req.vehicle_id or (device.vehicle_id if device else None),
            timestamp=req.timestamp,
            latitude=req.latitude,
            longitude=req.longitude,
            speed=req.speed,
            acceleration_peak=req.acceleration_peak,
            rotation_peak=req.rotation_peak,
            confidence=req.confidence,
            severity=req.severity.value,
            phone_connected=req.phone_connected,
            cellular_connected=req.cellular_connected,
            status=initial_status(req.severity).value,
            alert_source=req.alert_source.value,
            ai_assessment=req.ai_assessment,
        )
        try:
            crash = self.repo.add(crash)
        except IntegrityError:
            # Concurrent retry of the same eventId won the race.
            self.db.rollback()
            existing = self.repo.get_by_event_id(req.event_id)
            if existing is None:
                raise
            return CreateResult(existing, created=False)

        if device is not None:
            # A crash report is also proof the device is alive on its own network path.
            device.last_heartbeat = utcnow()
            device.phone_connected = req.phone_connected
            if req.cellular_connected:
                device.cellular_status = "CONNECTED"
            if req.latitude is not None and req.longitude is not None:
                device.last_latitude, device.last_longitude = req.latitude, req.longitude
            self.devices.save(device)

        if crash.status == CrashStatus.EMERGENCY_ACTIVE:
            crash = self.emergency.activate(crash)
        return CreateResult(crash, created=True)

    def get(self, event_id: str) -> CrashEvent:
        crash = self.repo.get_by_event_id(event_id)
        if crash is None:
            raise NotFoundError(f"Crash event {event_id} not found")
        return crash

    def list(self, **filters) -> list[CrashEvent]:
        return self.repo.list(**filters)

    def add_evidence(self, event_id: str, items: list[EvidenceIn], default_stored_by: str | None = None) -> CrashEvent:
        """Attach black-box evidence (audio, images, sensor logs, AI assessment) to a crash."""
        crash = self.get(event_id)
        records = [
            BlackBoxEvidence(
                kind=item.kind.value,
                source=item.source.value,
                label=item.label,
                content_text=item.content_text,
                media_url=item.media_url,
                media_mime=item.media_mime,
                captured_at=item.captured_at or utcnow(),
                stored_by=item.stored_by or default_stored_by,
            )
            for item in items
        ]
        return self.repo.add_evidence(crash, records)

    def add_blackbox(self, device_id: str, upload: BlackBoxUpload, device_secret: str | None) -> CrashEvent:
        """Authenticated batch dump from a vehicle black box after a crash."""
        self.device_service.authenticate(device_id, device_secret)
        return self.add_evidence(upload.event_id, upload.evidence, default_stored_by=device_id)

    def transition(
        self, event_id: str, target: CrashStatus, operator: str | None = None, note: str | None = None
    ) -> tuple[CrashEvent, bool]:
        """Returns (crash, changed). Repeating the same transition is idempotent."""
        crash = self.get(event_id)
        current = CrashStatus(crash.status)
        if current == target:
            return crash, False
        if current not in TRANSITIONS[target]:
            raise ConflictError(f"Cannot move crash {event_id} from {current} to {target}")
        crash.status = target.value
        now = utcnow()
        if target == CrashStatus.ACKNOWLEDGED:
            crash.acknowledged_by, crash.acknowledged_at = operator or "operator", now
        else:
            crash.closed_by, crash.closed_at = operator or "occupant", now
            crash.resolution = note
        crash = self.repo.save(crash)
        who = f" by {operator}" if operator else ""
        if target == CrashStatus.ACKNOWLEDGED:
            crash = self.emergency.record(
                crash, ActionChannel.RESPONSE_CENTER, "ACKNOWLEDGED", f"Incident acknowledged{who}", operator
            )
        elif target == CrashStatus.CANCELLED:
            reason = note or ("occupant reported safe" if not operator else "false alarm confirmed")
            crash = self.emergency.stand_down(crash, f"{reason}{who}", operator)
        elif target == CrashStatus.RESOLVED:
            crash = self.emergency.stand_down(crash, f"{note or 'incident resolved'}{who}", operator)
        return crash, True

    def add_operator_action(self, event_id: str, req: OperatorActionCreate) -> CrashEvent:
        crash = self.get(event_id)
        if CrashStatus(crash.status) not in ACTIVE_STATUSES:
            raise ConflictError(f"Crash {event_id} is closed ({crash.status})")
        channel, default_message = OPERATOR_ACTIONS[req.kind]
        return self.emergency.record(
            crash, channel, "OPERATOR_ACTION", req.message or default_message, req.operator
        )

    def stats(self) -> StatsOut:
        crashes = self.repo.list(limit=10_000)
        active = [c for c in crashes if CrashStatus(c.status) in ACTIVE_STATUSES]
        ack_times = [
            (c.acknowledged_at - c.created_at).total_seconds()
            for c in crashes
            if c.acknowledged_at is not None
        ]
        devices = self.devices.list()
        return StatsOut(
            active_incidents=len(active),
            critical_active=sum(1 for c in active if c.severity == Severity.CRITICAL),
            total_incidents=len(crashes),
            phone_independent_incidents=sum(1 for c in crashes if not c.phone_connected),
            devices_total=len(devices),
            devices_online=sum(1 for d in devices if is_online(d)),
            avg_acknowledge_seconds=round(sum(ack_times) / len(ack_times), 1) if ack_times else None,
        )

    def purge_old_locations(self) -> int:
        days = get_settings().location_retention_days
        if days <= 0:
            return 0
        cutoff = utcnow() - timedelta(days=days)
        old = self.repo.list_closed_before(cutoff)
        for crash in old:
            crash.latitude = None
            crash.longitude = None
        if old:
            self.db.commit()
        return len(old)
