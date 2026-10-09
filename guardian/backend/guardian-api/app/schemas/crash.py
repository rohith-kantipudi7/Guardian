from enum import StrEnum

from pydantic import Field

from app.domain import AlertSource, CrashStatus, EvidenceKind, EvidenceSource, Severity
from app.schemas.common import ID_PATTERN, CamelModel, UtcDatetime


class CrashCreate(CamelModel):
    event_id: str = Field(pattern=ID_PATTERN)
    device_id: str = Field(pattern=ID_PATTERN)
    vehicle_id: str | None = Field(default=None, pattern=ID_PATTERN)
    timestamp: UtcDatetime
    latitude: float | None = Field(default=None, ge=-90, le=90)
    longitude: float | None = Field(default=None, ge=-180, le=180)
    speed: float | None = Field(default=None, ge=0, le=500)
    acceleration_peak: float | None = Field(default=None, ge=0, le=500)
    rotation_peak: float | None = Field(default=None, ge=0, le=500)
    confidence: float = Field(ge=0, le=1)
    severity: Severity
    phone_connected: bool
    cellular_connected: bool = True
    # PHONE if the companion app delivered it; DEVICE if the vehicle black box did.
    alert_source: AlertSource = AlertSource.DEVICE
    # Optional natural-language summary from the phone's on-device AI.
    ai_assessment: str | None = Field(default=None, max_length=2000)


class EvidenceIn(CamelModel):
    kind: EvidenceKind
    source: EvidenceSource
    label: str = Field(min_length=1, max_length=160)
    content_text: str | None = Field(default=None, max_length=4000)
    media_url: str | None = Field(default=None, max_length=2048)
    media_mime: str | None = Field(default=None, max_length=64)
    captured_at: UtcDatetime | None = None
    stored_by: str | None = Field(default=None, pattern=ID_PATTERN)


class BlackBoxUpload(CamelModel):
    """Batch dump a black box sends when it connects to the backend after a crash."""

    event_id: str = Field(pattern=ID_PATTERN)
    evidence: list[EvidenceIn] = Field(min_length=1)


class EvidenceOut(CamelModel):
    kind: EvidenceKind
    source: EvidenceSource
    label: str
    content_text: str | None = None
    media_url: str | None = None
    media_mime: str | None = None
    captured_at: UtcDatetime
    stored_by: str | None = None
    created_at: UtcDatetime


class CrashAccepted(CamelModel):
    accepted: bool
    duplicate: bool = False
    event_id: str
    status: CrashStatus


class EmergencyActionOut(CamelModel):
    channel: str
    status: str
    message: str
    operator: str | None = None
    created_at: UtcDatetime


class TransitionRequest(CamelModel):
    """Who performed an acknowledge / cancel / resolve, and why."""

    operator: str | None = Field(default=None, max_length=64)
    note: str | None = Field(default=None, max_length=255)


class OperatorActionKind(StrEnum):
    CALLED_DRIVER = "CALLED_DRIVER"
    CALLED_EMERGENCY_CONTACT = "CALLED_EMERGENCY_CONTACT"
    AMBULANCE_DISPATCHED = "AMBULANCE_DISPATCHED"
    POLICE_NOTIFIED = "POLICE_NOTIFIED"
    TOW_REQUESTED = "TOW_REQUESTED"
    NOTE = "NOTE"


class OperatorActionCreate(CamelModel):
    kind: OperatorActionKind
    operator: str = Field(min_length=1, max_length=64)
    message: str | None = Field(default=None, max_length=255)


class StatsOut(CamelModel):
    active_incidents: int
    critical_active: int
    total_incidents: int
    phone_independent_incidents: int
    devices_total: int
    devices_online: int
    avg_acknowledge_seconds: float | None


class NotificationOut(CamelModel):
    recipient_type: str
    recipient_name: str
    recipient_phone: str | None
    token: str
    message: str
    sent_at: UtcDatetime
    opened_at: UtcDatetime | None = None


class CrashOut(CamelModel):
    event_id: str
    device_id: str
    vehicle_id: str | None
    timestamp: UtcDatetime
    latitude: float | None
    longitude: float | None
    speed: float | None
    acceleration_peak: float | None
    rotation_peak: float | None
    confidence: float
    severity: Severity
    phone_connected: bool
    cellular_connected: bool
    status: CrashStatus
    alert_source: AlertSource | None = None
    ai_assessment: str | None = None
    acknowledged_by: str | None = None
    acknowledged_at: UtcDatetime | None = None
    closed_by: str | None = None
    closed_at: UtcDatetime | None = None
    resolution: str | None = None
    created_at: UtcDatetime
    updated_at: UtcDatetime
    actions: list[EmergencyActionOut] = Field(default_factory=list)
    notifications: list[NotificationOut] = Field(default_factory=list)
    evidence: list[EvidenceOut] = Field(default_factory=list)


class ContactOut(CamelModel):
    name: str
    phone: str
    relationship: str | None = None


class ResponderStatusOut(CamelModel):
    recipient_type: str
    recipient_name: str
    opened_at: UtcDatetime | None = None


class AlertView(CamelModel):
    """Everything a responder needs, served from their alert link."""

    recipient_type: str
    recipient_name: str
    event_id: str
    status: CrashStatus
    severity: Severity
    confidence: float
    timestamp: UtcDatetime
    latitude: float | None
    longitude: float | None
    maps_url: str | None
    speed: float | None
    acceleration_peak: float | None
    rotation_peak: float | None
    phone_connected: bool
    cellular_connected: bool
    vehicle_registration: str | None
    vehicle_description: str | None
    driver: ContactOut | None
    emergency_contacts: list[ContactOut]
    responders: list[ResponderStatusOut]
    closed_by: str | None
    resolution: str | None
