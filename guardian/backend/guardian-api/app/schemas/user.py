from pydantic import AliasChoices, Field

from app.schemas.common import ID_PATTERN, CamelModel, UtcDatetime


class EmergencyContactIn(CamelModel):
    name: str = Field(min_length=1, max_length=120)
    phone: str = Field(min_length=3, max_length=32)
    relationship: str | None = Field(default=None, max_length=64)


class EmergencyContactOut(CamelModel):
    id: int
    name: str
    phone: str
    # ORM attribute is `relationship_` (avoids clashing with sqlalchemy.orm.relationship).
    relationship: str | None = Field(default=None, validation_alias=AliasChoices("relationship_", "relationship"))


class UserCreate(CamelModel):
    id: str | None = Field(default=None, pattern=ID_PATTERN)
    name: str = Field(min_length=1, max_length=120)
    phone: str = Field(min_length=3, max_length=32)
    emergency_contacts: list[EmergencyContactIn] = Field(default_factory=list, max_length=10)


class UserOut(CamelModel):
    id: str
    name: str
    phone: str
    created_at: UtcDatetime
    emergency_contacts: list[EmergencyContactOut]
