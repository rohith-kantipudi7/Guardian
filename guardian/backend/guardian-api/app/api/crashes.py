from fastapi import APIRouter, BackgroundTasks, Depends, Header, Query, Response, status
from sqlalchemy.orm import Session

from app.database import get_db
from app.domain import CrashStatus
from app.models import CrashEvent
from app.realtime import broadcaster
from app.schemas import (
    CrashAccepted,
    CrashCreate,
    CrashOut,
    EvidenceIn,
    EvidenceOut,
    OperatorActionCreate,
    StatsOut,
    TransitionRequest,
)
from app.services.crash_service import CrashService

router = APIRouter(prefix="/api/crashes", tags=["crashes"])


def _event(kind: str, crash: CrashEvent) -> dict:
    return {"type": kind, "event": CrashOut.model_validate(crash).model_dump(mode="json", by_alias=True)}


@router.post("", response_model=CrashAccepted, status_code=status.HTTP_201_CREATED)
def create_crash(
    body: CrashCreate,
    response: Response,
    background: BackgroundTasks,
    x_device_secret: str | None = Header(default=None),
    db: Session = Depends(get_db),
) -> CrashAccepted:
    result = CrashService(db).create(body, x_device_secret)
    if not result.created:
        response.status_code = status.HTTP_200_OK
        return CrashAccepted(
            accepted=False, duplicate=True, event_id=result.crash.event_id, status=CrashStatus(result.crash.status)
        )
    background.add_task(broadcaster.broadcast, _event("CRASH_DETECTED", result.crash))
    return CrashAccepted(accepted=True, event_id=result.crash.event_id, status=CrashStatus(result.crash.status))


@router.get("", response_model=list[CrashOut])
def list_crashes(
    status_: CrashStatus | None = Query(default=None, alias="status"),
    device_id: str | None = Query(default=None, alias="deviceId"),
    vehicle_id: str | None = Query(default=None, alias="vehicleId"),
    active: bool | None = None,
    limit: int = Query(default=100, ge=1, le=500),
    db: Session = Depends(get_db),
) -> list[CrashEvent]:
    return CrashService(db).list(
        status=status_.value if status_ else None,
        device_id=device_id,
        vehicle_id=vehicle_id,
        active=active,
        limit=limit,
    )


@router.get("/{event_id}", response_model=CrashOut)
def get_crash(event_id: str, db: Session = Depends(get_db)) -> CrashEvent:
    return CrashService(db).get(event_id)


@router.get("/{event_id}/evidence", response_model=list[EvidenceOut])
def list_evidence(event_id: str, db: Session = Depends(get_db)) -> list:
    return CrashService(db).get(event_id).evidence


@router.post("/{event_id}/evidence", response_model=CrashOut, status_code=status.HTTP_201_CREATED)
def add_evidence(
    event_id: str, body: EvidenceIn, background: BackgroundTasks, db: Session = Depends(get_db)
) -> CrashEvent:
    """Attach a black-box record (phone-captured audio/image/sensor log or AI assessment)."""
    crash = CrashService(db).add_evidence(event_id, [body])
    background.add_task(broadcaster.broadcast, _event("CRASH_UPDATED", crash))
    return crash


def _transition(
    event_id: str, target: CrashStatus, body: TransitionRequest | None, background: BackgroundTasks, db: Session
) -> CrashEvent:
    body = body or TransitionRequest()
    crash, changed = CrashService(db).transition(event_id, target, body.operator, body.note)
    if changed:
        background.add_task(broadcaster.broadcast, _event("CRASH_UPDATED", crash))
    return crash


@router.post("/{event_id}/acknowledge", response_model=CrashOut)
def acknowledge(
    event_id: str,
    background: BackgroundTasks,
    body: TransitionRequest | None = None,
    db: Session = Depends(get_db),
) -> CrashEvent:
    return _transition(event_id, CrashStatus.ACKNOWLEDGED, body, background, db)


@router.post("/{event_id}/cancel", response_model=CrashOut)
def cancel(
    event_id: str,
    background: BackgroundTasks,
    body: TransitionRequest | None = None,
    db: Session = Depends(get_db),
) -> CrashEvent:
    return _transition(event_id, CrashStatus.CANCELLED, body, background, db)


@router.post("/{event_id}/resolve", response_model=CrashOut)
def resolve(
    event_id: str,
    background: BackgroundTasks,
    body: TransitionRequest | None = None,
    db: Session = Depends(get_db),
) -> CrashEvent:
    return _transition(event_id, CrashStatus.RESOLVED, body, background, db)


@router.post("/{event_id}/actions", response_model=CrashOut, status_code=status.HTTP_201_CREATED)
def add_action(
    event_id: str, body: OperatorActionCreate, background: BackgroundTasks, db: Session = Depends(get_db)
) -> CrashEvent:
    crash = CrashService(db).add_operator_action(event_id, body)
    background.add_task(broadcaster.broadcast, _event("CRASH_UPDATED", crash))
    return crash


stats_router = APIRouter(prefix="/api/stats", tags=["stats"])


@stats_router.get("", response_model=StatsOut)
def stats(db: Session = Depends(get_db)) -> StatsOut:
    return CrashService(db).stats()
