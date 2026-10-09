from __future__ import annotations

from datetime import datetime

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.domain import ACTIVE_STATUSES
from app.models import BlackBoxEvidence, CrashEvent, Device, EmergencyAction, User, Vehicle


class UserRepository:
    def __init__(self, db: Session):
        self.db = db

    def get(self, user_id: str) -> User | None:
        return self.db.get(User, user_id)

    def add(self, user: User) -> User:
        self.db.add(user)
        self.db.commit()
        self.db.refresh(user)
        return user


class VehicleRepository:
    def __init__(self, db: Session):
        self.db = db

    def get(self, vehicle_id: str) -> Vehicle | None:
        return self.db.get(Vehicle, vehicle_id)

    def list(self) -> list[Vehicle]:
        return list(self.db.scalars(select(Vehicle).order_by(Vehicle.id)))

    def save(self, vehicle: Vehicle) -> Vehicle:
        self.db.add(vehicle)
        self.db.commit()
        self.db.refresh(vehicle)
        return vehicle


class DeviceRepository:
    def __init__(self, db: Session):
        self.db = db

    def get(self, device_id: str) -> Device | None:
        return self.db.get(Device, device_id)

    def list(self) -> list[Device]:
        return list(self.db.scalars(select(Device).order_by(Device.id)))

    def save(self, device: Device) -> Device:
        self.db.add(device)
        self.db.commit()
        self.db.refresh(device)
        return device


class CrashRepository:
    def __init__(self, db: Session):
        self.db = db

    def get_by_event_id(self, event_id: str) -> CrashEvent | None:
        return self.db.scalar(select(CrashEvent).where(CrashEvent.event_id == event_id))

    def list(
        self,
        *,
        status: str | None = None,
        device_id: str | None = None,
        vehicle_id: str | None = None,
        active: bool | None = None,
        limit: int = 100,
    ) -> list[CrashEvent]:
        stmt = select(CrashEvent)
        if status:
            stmt = stmt.where(CrashEvent.status == status)
        if device_id:
            stmt = stmt.where(CrashEvent.device_id == device_id)
        if vehicle_id:
            stmt = stmt.where(CrashEvent.vehicle_id == vehicle_id)
        if active is True:
            stmt = stmt.where(CrashEvent.status.in_([s.value for s in ACTIVE_STATUSES]))
        elif active is False:
            stmt = stmt.where(CrashEvent.status.not_in([s.value for s in ACTIVE_STATUSES]))
        stmt = stmt.order_by(CrashEvent.timestamp.desc(), CrashEvent.id.desc()).limit(limit)
        return list(self.db.scalars(stmt))

    def list_closed_before(self, cutoff: datetime) -> list[CrashEvent]:
        stmt = select(CrashEvent).where(
            CrashEvent.status.not_in([s.value for s in ACTIVE_STATUSES]),
            CrashEvent.updated_at < cutoff,
            CrashEvent.latitude.is_not(None),
        )
        return list(self.db.scalars(stmt))

    def add(self, crash: CrashEvent) -> CrashEvent:
        self.db.add(crash)
        self.db.commit()
        self.db.refresh(crash)
        return crash

    def add_actions(self, crash: CrashEvent, actions: list[EmergencyAction]) -> CrashEvent:
        crash.actions.extend(actions)
        self.db.commit()
        self.db.refresh(crash)
        return crash

    def add_evidence(self, crash: CrashEvent, items: list[BlackBoxEvidence]) -> CrashEvent:
        crash.evidence.extend(items)
        self.db.commit()
        self.db.refresh(crash)
        return crash

    def save(self, crash: CrashEvent) -> CrashEvent:
        self.db.commit()
        self.db.refresh(crash)
        return crash
