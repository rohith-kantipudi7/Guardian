"""Simulated emergency response workflow.

This POC NEVER contacts real police, hospitals or phone numbers. Every action is
recorded as SIMULATED so the dashboard can show the response pipeline.
"""

import logging
import secrets

from sqlalchemy.orm import Session

from app.config import get_settings
from app.domain import ActionChannel
from app.models import CrashEvent, EmergencyAction, Notification, User, Vehicle
from app.repositories import CrashRepository, UserRepository, VehicleRepository

log = logging.getLogger(__name__)


def maps_url(lat: float, lon: float) -> str:
    return f"https://maps.google.com/?q={lat:.5f},{lon:.5f}"


class EmergencyService:
    def __init__(self, db: Session):
        self.crashes = CrashRepository(db)
        self.vehicles = VehicleRepository(db)
        self.users = UserRepository(db)

    def owner_of(self, crash: CrashEvent) -> tuple[Vehicle | None, User | None]:
        vehicle = self.vehicles.get(crash.vehicle_id) if crash.vehicle_id else None
        user = self.users.get(vehicle.user_id) if vehicle and vehicle.user_id else None
        return vehicle, user

    def _alert_text(self, crash: CrashEvent, vehicle: Vehicle | None, user: User | None, link: str) -> str:
        lines = [
            f"[SIMULATED] GUARDIAN CRASH ALERT - {crash.severity}",
            f"Time: {crash.timestamp:%d %b %Y %H:%M:%S} UTC",
        ]
        if crash.latitude is not None and crash.longitude is not None:
            lines.append(f"Location: {maps_url(crash.latitude, crash.longitude)}")
        else:
            lines.append("Location: unavailable (no GPS fix)")
        if vehicle:
            desc = " ".join(str(p) for p in (vehicle.make, vehicle.model) if p)
            lines.append(f"Vehicle: {vehicle.registration_number}{f' ({desc})' if desc else ''}")
        if user:
            lines.append(f"Driver: {user.name}, {user.phone}")
        details = [f"impact {crash.acceleration_peak:.1f} g" if crash.acceleration_peak is not None else None,
                   f"speed {crash.speed:.0f} km/h" if crash.speed is not None else None]
        if any(details):
            lines.append("Crash data: " + ", ".join(d for d in details if d))
        if not crash.phone_connected:
            lines.append("Driver's phone: NOT reachable - sent by the vehicle device")
        lines.append(f"Full details & respond: {link}")
        return "\n".join(lines)

    def activate(self, crash: CrashEvent) -> CrashEvent:
        """Automatically alerts every responder with full details. No operator in the loop."""
        settings = get_settings()
        vehicle, user = self.owner_of(crash)
        recipients: list[tuple[ActionChannel, str, str | None]] = [
            (ActionChannel.POLICE, settings.police_desk_name, settings.police_desk_phone),
            (ActionChannel.HOSPITAL, settings.hospital_desk_name, settings.hospital_desk_phone),
        ]
        recipients += [(ActionChannel.EMERGENCY_CONTACT, c.name, c.phone) for c in (user.emergency_contacts if user else [])]

        actions: list[EmergencyAction] = []
        for channel, name, phone in recipients:
            token = secrets.token_urlsafe(18)
            link = f"{settings.alert_link_base.rstrip('/')}/{token}"
            crash.notifications.append(
                Notification(
                    recipient_type=channel.value,
                    recipient_name=name,
                    recipient_phone=phone,
                    token=token,
                    message=self._alert_text(crash, vehicle, user, link),
                )
            )
            actions.append(
                EmergencyAction(
                    channel=channel,
                    status="SIMULATED_NOTIFIED",
                    message=f"[SIMULATED] Alert with full details sent to {name}{f' ({phone})' if phone else ''}",
                )
            )
        if user is None or not user.emergency_contacts:
            actions.append(
                EmergencyAction(
                    channel=ActionChannel.EMERGENCY_CONTACT, status="SKIPPED", message="No emergency contacts on file"
                )
            )
        for a in actions:
            log.warning("%s %s", crash.event_id, a.message)
        return self.crashes.add_actions(crash, actions)

    def stand_down(self, crash: CrashEvent, reason: str, operator: str | None = None) -> CrashEvent:
        action = EmergencyAction(
            channel=ActionChannel.RESPONSE_CENTER,
            status="SIMULATED_STOOD_DOWN",
            message=f"[SIMULATED] Responders stood down: {reason}",
            operator=operator,
        )
        return self.crashes.add_actions(crash, [action])

    def record(
        self, crash: CrashEvent, channel: ActionChannel, status: str, message: str, operator: str | None
    ) -> CrashEvent:
        action = EmergencyAction(channel=channel, status=status, message=message, operator=operator)
        log.info("%s %s (%s)", crash.event_id, message, operator or "system")
        return self.crashes.add_actions(crash, [action])
