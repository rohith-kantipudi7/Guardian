from datetime import datetime

from sqlalchemy import Boolean, DateTime, Float, ForeignKey, Integer, String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database.session import Base
from app.domain import utcnow


class User(Base):
    __tablename__ = "users"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    name: Mapped[str] = mapped_column(String(120))
    phone: Mapped[str] = mapped_column(String(32))
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)

    emergency_contacts: Mapped[list["EmergencyContact"]] = relationship(
        back_populates="user", cascade="all, delete-orphan", lazy="selectin"
    )


class EmergencyContact(Base):
    __tablename__ = "emergency_contacts"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    user_id: Mapped[str] = mapped_column(ForeignKey("users.id"), index=True)
    name: Mapped[str] = mapped_column(String(120))
    phone: Mapped[str] = mapped_column(String(32))
    relationship_: Mapped[str | None] = mapped_column("relationship", String(64), nullable=True)

    user: Mapped[User] = relationship(back_populates="emergency_contacts")


class Vehicle(Base):
    __tablename__ = "vehicles"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    user_id: Mapped[str | None] = mapped_column(ForeignKey("users.id"), nullable=True, index=True)
    registration_number: Mapped[str] = mapped_column(String(32))
    make: Mapped[str | None] = mapped_column(String(64), nullable=True)
    model: Mapped[str | None] = mapped_column(String(64), nullable=True)
    year: Mapped[int | None] = mapped_column(Integer, nullable=True)
    device_id: Mapped[str | None] = mapped_column(String(64), nullable=True, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class Device(Base):
    __tablename__ = "devices"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    serial_number: Mapped[str | None] = mapped_column(String(64), nullable=True)
    secret_hash: Mapped[str] = mapped_column(String(128))
    firmware_version: Mapped[str | None] = mapped_column(String(32), nullable=True)
    vehicle_id: Mapped[str | None] = mapped_column(String(64), nullable=True)
    battery_level: Mapped[int | None] = mapped_column(Integer, nullable=True)
    gps_status: Mapped[str | None] = mapped_column(String(16), nullable=True)
    cellular_status: Mapped[str | None] = mapped_column(String(16), nullable=True)
    ble_status: Mapped[str | None] = mapped_column(String(16), nullable=True)
    phone_connected: Mapped[bool] = mapped_column(Boolean, default=False)
    vehicle_connected: Mapped[bool] = mapped_column(Boolean, default=False)
    last_latitude: Mapped[float | None] = mapped_column(Float, nullable=True)
    last_longitude: Mapped[float | None] = mapped_column(Float, nullable=True)
    last_heartbeat: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class CrashEvent(Base):
    __tablename__ = "crash_events"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    event_id: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    device_id: Mapped[str] = mapped_column(String(64), index=True)
    vehicle_id: Mapped[str | None] = mapped_column(String(64), index=True, nullable=True)
    timestamp: Mapped[datetime] = mapped_column(DateTime)
    latitude: Mapped[float | None] = mapped_column(Float, nullable=True)
    longitude: Mapped[float | None] = mapped_column(Float, nullable=True)
    speed: Mapped[float | None] = mapped_column(Float, nullable=True)
    acceleration_peak: Mapped[float | None] = mapped_column(Float, nullable=True)
    rotation_peak: Mapped[float | None] = mapped_column(Float, nullable=True)
    confidence: Mapped[float] = mapped_column(Float)
    severity: Mapped[str] = mapped_column(String(16))
    phone_connected: Mapped[bool] = mapped_column(Boolean)
    cellular_connected: Mapped[bool] = mapped_column(Boolean, default=True)
    status: Mapped[str] = mapped_column(String(24), index=True)
    # Who delivered the alert to the backend: PHONE (primary) or DEVICE (black-box fallback).
    alert_source: Mapped[str | None] = mapped_column(String(16), nullable=True)
    # Short natural-language situation summary from the phone's on-device AI.
    ai_assessment: Mapped[str | None] = mapped_column(String(2000), nullable=True)
    acknowledged_by: Mapped[str | None] = mapped_column(String(64), nullable=True)
    acknowledged_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    closed_by: Mapped[str | None] = mapped_column(String(64), nullable=True)
    closed_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    resolution: Mapped[str | None] = mapped_column(String(255), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, onupdate=utcnow)

    actions: Mapped[list["EmergencyAction"]] = relationship(
        back_populates="crash", cascade="all, delete-orphan", lazy="selectin", order_by="EmergencyAction.id"
    )
    notifications: Mapped[list["Notification"]] = relationship(
        back_populates="crash", cascade="all, delete-orphan", lazy="selectin", order_by="Notification.id"
    )
    evidence: Mapped[list["BlackBoxEvidence"]] = relationship(
        back_populates="crash", cascade="all, delete-orphan", lazy="selectin", order_by="BlackBoxEvidence.id"
    )


class EmergencyAction(Base):
    """Simulated emergency workflow step. Never a real dispatch."""

    __tablename__ = "emergency_actions"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    crash_id: Mapped[int] = mapped_column(ForeignKey("crash_events.id"), index=True)
    channel: Mapped[str] = mapped_column(String(32))
    status: Mapped[str] = mapped_column(String(24))
    message: Mapped[str] = mapped_column(String(255))
    # Operator who performed the action; null for automatic system steps.
    operator: Mapped[str | None] = mapped_column(String(64), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)

    crash: Mapped[CrashEvent] = relationship(back_populates="actions")


class BlackBoxEvidence(Base):
    """Black-box record captured for a crash: in-cabin audio, images, sensor logs or the
    phone's on-device AI assessment. Media bytes are referenced by URL; this POC stores
    short text (transcripts, summaries, log excerpts) inline."""

    __tablename__ = "blackbox_evidence"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    crash_id: Mapped[int] = mapped_column(ForeignKey("crash_events.id"), index=True)
    kind: Mapped[str] = mapped_column(String(24))
    # Which actor captured/stored it: PHONE (primary) or DEVICE (black box).
    source: Mapped[str] = mapped_column(String(16))
    label: Mapped[str] = mapped_column(String(160))
    content_text: Mapped[str | None] = mapped_column(String(4000), nullable=True)
    media_url: Mapped[str | None] = mapped_column(String(2048), nullable=True)
    media_mime: Mapped[str | None] = mapped_column(String(64), nullable=True)
    captured_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
    # Device id of the black box that holds this copy, if stored on the device.
    stored_by: Mapped[str | None] = mapped_column(String(64), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)

    crash: Mapped[CrashEvent] = relationship(back_populates="evidence")


class Notification(Base):
    """Alert sent to a responder (police, hospital, family). Delivery is SIMULATED."""

    __tablename__ = "notifications"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    crash_id: Mapped[int] = mapped_column(ForeignKey("crash_events.id"), index=True)
    recipient_type: Mapped[str] = mapped_column(String(32))
    recipient_name: Mapped[str] = mapped_column(String(120))
    recipient_phone: Mapped[str | None] = mapped_column(String(32), nullable=True)
    # Unguessable capability token for the responder's alert link.
    token: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    message: Mapped[str] = mapped_column(String(1000))
    sent_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
    opened_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)

    crash: Mapped[CrashEvent] = relationship(back_populates="notifications")
