from fastapi import APIRouter, BackgroundTasks, Depends, Header, Response, status
from sqlalchemy.orm import Session

from app.database import get_db
from app.realtime import broadcaster
from app.schemas import BlackBoxUpload, CrashOut, DeviceOut, DeviceRegister, Heartbeat
from app.services.crash_service import CrashService
from app.services.device_service import DeviceService, to_device_out

router = APIRouter(prefix="/api/devices", tags=["devices"])


def _event(kind: str, device: DeviceOut) -> dict:
    return {"type": kind, "device": device.model_dump(mode="json", by_alias=True)}


def _crash_event(kind: str, crash) -> dict:
    return {"type": kind, "event": CrashOut.model_validate(crash).model_dump(mode="json", by_alias=True)}


@router.post("/register", response_model=DeviceOut, status_code=status.HTTP_201_CREATED)
def register_device(
    body: DeviceRegister, response: Response, background: BackgroundTasks, db: Session = Depends(get_db)
) -> DeviceOut:
    device, created = DeviceService(db).register(body)
    if not created:
        response.status_code = status.HTTP_200_OK
    out = to_device_out(device)
    background.add_task(broadcaster.broadcast, _event("DEVICE_REGISTERED", out))
    return out


@router.get("", response_model=list[DeviceOut])
def list_devices(db: Session = Depends(get_db)) -> list[DeviceOut]:
    return [to_device_out(d) for d in DeviceService(db).list()]


@router.get("/{device_id}", response_model=DeviceOut)
def get_device(device_id: str, db: Session = Depends(get_db)) -> DeviceOut:
    return to_device_out(DeviceService(db).get(device_id))


@router.post("/{device_id}/heartbeat", response_model=DeviceOut)
def heartbeat(
    device_id: str,
    body: Heartbeat,
    background: BackgroundTasks,
    x_device_secret: str | None = Header(default=None),
    db: Session = Depends(get_db),
) -> DeviceOut:
    device = DeviceService(db).heartbeat(device_id, body, x_device_secret)
    out = to_device_out(device)
    background.add_task(broadcaster.broadcast, _event("DEVICE_HEARTBEAT", out))
    return out


@router.post("/{device_id}/blackbox", response_model=CrashOut, status_code=status.HTTP_201_CREATED)
def upload_blackbox(
    device_id: str,
    body: BlackBoxUpload,
    background: BackgroundTasks,
    x_device_secret: str | None = Header(default=None),
    db: Session = Depends(get_db),
) -> CrashOut:
    """Authenticated black-box dump: the vehicle device uploads evidence it stored for a crash."""
    crash = CrashService(db).add_blackbox(device_id, body, x_device_secret)
    background.add_task(broadcaster.broadcast, _crash_event("CRASH_UPDATED", crash))
    return CrashOut.model_validate(crash)
