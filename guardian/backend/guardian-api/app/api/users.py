import uuid

from fastapi import APIRouter, Depends, status
from sqlalchemy.orm import Session

from app.database import get_db
from app.models import EmergencyContact, User
from app.repositories import UserRepository
from app.schemas import UserCreate, UserOut
from app.services.errors import ConflictError, NotFoundError

router = APIRouter(prefix="/api/users", tags=["users"])


@router.post("", response_model=UserOut, status_code=status.HTTP_201_CREATED)
def create_user(body: UserCreate, db: Session = Depends(get_db)) -> User:
    repo = UserRepository(db)
    user_id = body.id or f"USR-{uuid.uuid4().hex[:8].upper()}"
    if repo.get(user_id):
        raise ConflictError(f"User {user_id} already exists")
    user = User(
        id=user_id,
        name=body.name,
        phone=body.phone,
        emergency_contacts=[
            EmergencyContact(name=c.name, phone=c.phone, relationship_=c.relationship) for c in body.emergency_contacts
        ],
    )
    return repo.add(user)


@router.get("/{user_id}", response_model=UserOut)
def get_user(user_id: str, db: Session = Depends(get_db)) -> User:
    user = UserRepository(db).get(user_id)
    if user is None:
        raise NotFoundError(f"User {user_id} not found")
    return user
