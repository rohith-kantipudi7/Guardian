from pydantic import Field

from app.domain import BleStatus, CellularStatus, GpsStatus
from app.schemas.common import ID_PATTERN, CamelModel, UtcDatetime


class DeviceRegister(CamelModel):
    device_id: str = Field(pattern=ID_PATTERN)
    device_secret: str = Field(min_length=8, max_length=128)
    serial_number: str | None = Field(default=None, max_length=64)
    firmware_version: str | None = Field(default=None, max_length=32)
    vehicle_id: str | None = Field(default=None, pattern=ID_PATTERN)


class Heartbeat(CamelModel):
    device_id: str = Field(pattern=ID_PATTERN)
    timestamp: UtcDatetime
    battery_level: int | None = Field(default=None, ge=0, le=100)
    gps_status: GpsStatus | None = None
    cellular_status: CellularStatus | None = None
    ble_status: BleStatus | None = None
    phone_connected: bool | None = None
    vehicle_connected: bool | None = None
    latitude: float | None = Field(default=None, ge=-90, le=90)
    longitude: float | None = Field(default=None, ge=-180, le=180)
    firmware_version: str | None = Field(default=None, max_length=32)


class DeviceOut(CamelModel):
    id: str
    serial_number: str | None
    firmware_version: str | None
    vehicle_id: str | None
    battery_level: int | None
    gps_status: str | None
    cellular_status: str | None
    ble_status: str | None
    phone_connected: bool
    vehicle_connected: bool
    last_latitude: float | None
    last_longitude: float | None
    last_heartbeat: UtcDatetime | None
    created_at: UtcDatetime
    online: bool = False
