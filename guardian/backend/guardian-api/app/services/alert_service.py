"""Responder-facing alert links: police, hospital and family open them from the SMS. No login, no buttons."""

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.domain import ActionChannel, CrashStatus, utcnow
from app.models import CrashEvent, Notification
from app.schemas import AlertView, ContactOut, ResponderStatusOut
from app.services.emergency_service import EmergencyService, maps_url
from app.services.errors import NotFoundError


class AlertService:
    def __init__(self, db: Session):
        self.db = db
        self.emergency = EmergencyService(db)

    def _get(self, token: str) -> Notification:
        n = self.db.scalar(select(Notification).where(Notification.token == token))
        if n is None:
            raise NotFoundError("Alert not found")
        return n

    def open(self, token: str) -> tuple[AlertView, CrashEvent, bool]:
        """Returns (view, crash, first_open). Opening the link is the read receipt; nobody has to click anything."""
        n = self._get(token)
        first = n.opened_at is None
        crash = n.crash
        if first:
            now = utcnow()
            n.opened_at = now
            if crash.status == CrashStatus.EMERGENCY_ACTIVE:
                crash.status = CrashStatus.ACKNOWLEDGED.value
                crash.acknowledged_by, crash.acknowledged_at = n.recipient_name, now
            crash = self.emergency.record(
                crash, ActionChannel(n.recipient_type), "OPENED", f"Seen by {n.recipient_name}", None
            )
        return self._view(n, crash), crash, first

    def _view(self, n: Notification, crash: CrashEvent) -> AlertView:
        vehicle, user = self.emergency.owner_of(crash)
        has_fix = crash.latitude is not None and crash.longitude is not None
        return AlertView(
            recipient_type=n.recipient_type,
            recipient_name=n.recipient_name,
            event_id=crash.event_id,
            status=CrashStatus(crash.status),
            severity=crash.severity,
            confidence=crash.confidence,
            timestamp=crash.timestamp,
            latitude=crash.latitude,
            longitude=crash.longitude,
            maps_url=maps_url(crash.latitude, crash.longitude) if has_fix else None,
            speed=crash.speed,
            acceleration_peak=crash.acceleration_peak,
            rotation_peak=crash.rotation_peak,
            phone_connected=crash.phone_connected,
            cellular_connected=crash.cellular_connected,
            vehicle_registration=vehicle.registration_number if vehicle else None,
            vehicle_description=" ".join(str(p) for p in (vehicle.make, vehicle.model, vehicle.year) if p) or None
            if vehicle
            else None,
            driver=ContactOut(name=user.name, phone=user.phone) if user else None,
            emergency_contacts=[
                ContactOut(name=c.name, phone=c.phone, relationship=c.relationship_)
                for c in (user.emergency_contacts if user else [])
            ],
            responders=[
                ResponderStatusOut(
                    recipient_type=r.recipient_type,
                    recipient_name=r.recipient_name,
                    opened_at=r.opened_at,
                )
                for r in crash.notifications
            ],
            closed_by=crash.closed_by,
            resolution=crash.resolution,
        )
