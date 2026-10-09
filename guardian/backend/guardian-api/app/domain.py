from datetime import UTC, datetime
from enum import StrEnum


def utcnow() -> datetime:
    """Naive UTC datetime (stored as-is; SQLite has no timezone support)."""
    return datetime.now(UTC).replace(tzinfo=None)


class Severity(StrEnum):
    NORMAL = "NORMAL"
    MINOR = "MINOR"
    MODERATE = "MODERATE"
    CRITICAL = "CRITICAL"


class CrashStatus(StrEnum):
    DETECTED = "DETECTED"
    VERIFYING = "VERIFYING"
    CANCELLED = "CANCELLED"
    EMERGENCY_ACTIVE = "EMERGENCY_ACTIVE"
    ACKNOWLEDGED = "ACKNOWLEDGED"
    RESOLVED = "RESOLVED"


ACTIVE_STATUSES = frozenset(
    {CrashStatus.DETECTED, CrashStatus.VERIFYING, CrashStatus.EMERGENCY_ACTIVE, CrashStatus.ACKNOWLEDGED}
)


class GpsStatus(StrEnum):
    AVAILABLE = "AVAILABLE"
    NO_FIX = "NO_FIX"
    UNAVAILABLE = "UNAVAILABLE"


class CellularStatus(StrEnum):
    CONNECTED = "CONNECTED"
    CONNECTING = "CONNECTING"
    DISCONNECTED = "DISCONNECTED"
    UNAVAILABLE = "UNAVAILABLE"


class BleStatus(StrEnum):
    CONNECTED = "CONNECTED"
    ADVERTISING = "ADVERTISING"
    DISCONNECTED = "DISCONNECTED"


class ActionChannel(StrEnum):
    POLICE = "POLICE"
    HOSPITAL = "HOSPITAL"
    EMERGENCY_CONTACT = "EMERGENCY_CONTACT"
    DRIVER = "DRIVER"
    TOW = "TOW"
    RESPONSE_CENTER = "RESPONSE_CENTER"


class AlertSource(StrEnum):
    """Who actually delivered the emergency alert to the backend."""

    PHONE = "PHONE"
    DEVICE = "DEVICE"


class EvidenceSource(StrEnum):
    """Which actor captured or stored a piece of black-box evidence."""

    PHONE = "PHONE"
    DEVICE = "DEVICE"


class EvidenceKind(StrEnum):
    AUDIO = "AUDIO"
    IMAGE = "IMAGE"
    SENSOR_LOG = "SENSOR_LOG"
    TRANSCRIPT = "TRANSCRIPT"
    AI_ASSESSMENT = "AI_ASSESSMENT"
