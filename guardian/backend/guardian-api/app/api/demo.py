from fastapi import APIRouter, BackgroundTasks, Depends
from sqlalchemy import delete
from sqlalchemy.orm import Session

from app.config import get_settings
from app.database import get_db
from app.models import CrashEvent, EmergencyAction, Notification
from app.realtime import broadcaster
from app.services.errors import NotFoundError

router = APIRouter(prefix="/api/demo", tags=["demo"])


@router.post("/reset")
def reset_incidents(background: BackgroundTasks, db: Session = Depends(get_db)) -> dict:
    """Clears incidents for a fresh showcase run. Disabled unless DEMO_MODE=true."""
    if not get_settings().demo_mode:
        raise NotFoundError("Not found")
    db.execute(delete(EmergencyAction))
    db.execute(delete(Notification))
    removed = db.execute(delete(CrashEvent)).rowcount
    db.commit()
    background.add_task(broadcaster.broadcast, {"type": "DEMO_RESET"})
    return {"removedIncidents": removed}
