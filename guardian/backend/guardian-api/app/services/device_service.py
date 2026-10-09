import hashlib
import hmac
import secrets
from datetime import timedelta

from sqlalchemy.orm import Session

from app.config import get_settings
from app.domain import utcnow
from app.models import Device
from app.repositories import DeviceRepository
from app.schemas import DeviceOut, DeviceRegister, Heartbeat
from app.services.errors import InvalidRequestError, NotFoundError, UnauthorizedError


def hash_secret(secret: str) -> str:
    salt = secrets.token_hex(16)
    digest = hmac.new(salt.encode(), secret.encode(), hashlib.sha256).hexdigest()
    return f"{salt}${digest}"


def verify_secret(secret: str, stored: str) -> bool:
    try:
        salt, digest = stored.split("$", 1)
    except ValueError:
        return False
    candidate = hmac.new(salt.encode(), secret.encode(), hashlib.sha256).hexdigest()
    return hmac.compare_digest(candidate, digest)


def is_online(device: Device) -> bool:
    if device.last_heartbeat is None:
        return False
    window = timedelta(seconds=get_settings().device_offline_after_seconds)
    return utcnow() - device.last_heartbeat <= window


def to_device_out(device: Device) -> DeviceOut:
    out = DeviceOut.model_validate(device)
    out.online = is_online(device)
    return out


class DeviceService:
    def __init__(self, db: Session):
        self.repo = DeviceRepository(db)

    def register(self, req: DeviceRegister) -> tuple[Device, bool]:
        """Returns (device, created). Re-registration requires the original secret."""
        device = self.repo.get(req.device_id)
        created = device is None
        if device is None:
            device = Device(id=req.device_id, secret_hash=hash_secret(req.device_secret))
        elif not verify_secret(req.device_secret, device.secret_hash):
            raise UnauthorizedError("Device already registered with a different secret")
        device.serial_number = req.serial_number or device.serial_number
        device.firmware_version = req.firmware_version or device.firmware_version
        device.vehicle_id = req.vehicle_id or device.vehicle_id
        return self.repo.save(device), created

    def get(self, device_id: str) -> Device:
        device = self.repo.get(device_id)
        if device is None:
            raise NotFoundError(f"Device {device_id} not found")
        return device

    def list(self) -> list[Device]:
        return self.repo.list()

    def authenticate(self, device_id: str, secret: str | None) -> Device | None:
        """Validates the device secret. Returns the device (None only if auth is disabled and unknown)."""
        device = self.repo.get(device_id)
        if not get_settings().require_device_auth:
            return device
        if device is None or not secret or not verify_secret(secret, device.secret_hash):
            raise UnauthorizedError("Invalid device credentials")
        return device

    def heartbeat(self, device_id: str, hb: Heartbeat, secret: str | None) -> Device:
        if hb.device_id != device_id:
            raise InvalidRequestError("deviceId in body does not match URL")
        device = self.authenticate(device_id, secret)
        if device is None:
            raise NotFoundError(f"Device {device_id} not registered")
        device.last_heartbeat = utcnow()
        for attr in (
            "battery_level",
            "gps_status",
            "cellular_status",
            "ble_status",
            "phone_connected",
            "vehicle_connected",
            "firmware_version",
        ):
            value = getattr(hb, attr)
            if value is not None:
                setattr(device, attr, value.value if hasattr(value, "value") else value)
        if hb.latitude is not None and hb.longitude is not None:
            device.last_latitude = hb.latitude
            device.last_longitude = hb.longitude
        return self.repo.save(device)
