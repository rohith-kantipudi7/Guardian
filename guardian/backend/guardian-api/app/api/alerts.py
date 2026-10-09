from fastapi import APIRouter, BackgroundTasks, Depends
from sqlalchemy.orm import Session

from app.api.crashes import _event
from app.database import get_db
from app.realtime import broadcaster
from app.schemas import AlertView
from app.services.alert_service import AlertService

router = APIRouter(prefix="/api/alerts", tags=["responder alerts"])


@router.get("/{token}", response_model=AlertView)
def open_alert(token: str, background: BackgroundTasks, db: Session = Depends(get_db)) -> AlertView:
    view, crash, first_open = AlertService(db).open(token)
    if first_open:
        background.add_task(broadcaster.broadcast, _event("CRASH_UPDATED", crash))
    return view
