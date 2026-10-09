from datetime import UTC, datetime
from typing import Annotated

from pydantic import AfterValidator, BaseModel, ConfigDict, PlainSerializer
from pydantic.alias_generators import to_camel


def _to_naive_utc(value: datetime) -> datetime:
    if value.tzinfo is not None:
        value = value.astimezone(UTC).replace(tzinfo=None)
    return value


def _serialize_utc(value: datetime) -> str:
    return _to_naive_utc(value).isoformat(timespec="milliseconds") + "Z"


UtcDatetime = Annotated[datetime, AfterValidator(_to_naive_utc), PlainSerializer(_serialize_utc, return_type=str)]

# Identifiers like GD-001 / VH-001 / EVT-... ; restrictive to keep logs and URLs clean.
ID_PATTERN = r"^[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}$"


class CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, from_attributes=True)
