from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str = "sqlite:///./guardian.db"
    # A device is reported ONLINE if its last heartbeat is newer than this.
    device_offline_after_seconds: int = 30
    require_device_auth: bool = True
    cors_origins: str = "http://localhost:5173,http://127.0.0.1:5173"
    # Days to keep precise location on closed incidents (0 = keep forever).
    location_retention_days: int = 30
    demo_mode: bool = True
    # Base of the per-responder alert link included in every notification.
    alert_link_base: str = "http://localhost:5173/alert"
    police_desk_name: str = "Police control room"
    police_desk_phone: str = "112"
    hospital_desk_name: str = "Nearest hospital / ambulance"
    hospital_desk_phone: str = "108"

    @property
    def cors_origin_list(self) -> list[str]:
        return [o.strip() for o in self.cors_origins.split(",") if o.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
