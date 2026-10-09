from fastapi import APIRouter, Depends, Response, status
from sqlalchemy.orm import Session

from app.database import get_db
from app.models import Vehicle
from app.repositories import VehicleRepository
from app.schemas import VehicleCreate, VehicleOut
from app.services.errors import NotFoundError

router = APIRouter(prefix="/api/vehicles", tags=["vehicles"])


@router.post("", response_model=VehicleOut, status_code=status.HTTP_201_CREATED)
def upsert_vehicle(body: VehicleCreate, response: Response, db: Session = Depends(get_db)) -> Vehicle:
    repo = VehicleRepository(db)
    vehicle = repo.get(body.id)
    if vehicle is None:
        vehicle = Vehicle(id=body.id)
    else:
        response.status_code = status.HTTP_200_OK
    for field in ("user_id", "registration_number", "make", "model", "year", "device_id"):
        setattr(vehicle, field, getattr(body, field))
    return repo.save(vehicle)


@router.get("", response_model=list[VehicleOut])
def list_vehicles(db: Session = Depends(get_db)) -> list[Vehicle]:
    return VehicleRepository(db).list()


@router.get("/{vehicle_id}", response_model=VehicleOut)
def get_vehicle(vehicle_id: str, db: Session = Depends(get_db)) -> Vehicle:
    vehicle = VehicleRepository(db).get(vehicle_id)
    if vehicle is None:
        raise NotFoundError(f"Vehicle {vehicle_id} not found")
    return vehicle
