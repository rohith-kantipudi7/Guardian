from pydantic import Field

from app.schemas.common import ID_PATTERN, CamelModel, UtcDatetime


class VehicleCreate(CamelModel):
    id: str = Field(pattern=ID_PATTERN)
    user_id: str | None = Field(default=None, pattern=ID_PATTERN)
    registration_number: str = Field(min_length=1, max_length=32)
    make: str | None = Field(default=None, max_length=64)
    model: str | None = Field(default=None, max_length=64)
    year: int | None = Field(default=None, ge=1950, le=2100)
    device_id: str | None = Field(default=None, pattern=ID_PATTERN)


class VehicleOut(CamelModel):
    id: str
    user_id: str | None
    registration_number: str
    make: str | None
    model: str | None
    year: int | None
    device_id: str | None
    created_at: UtcDatetime
