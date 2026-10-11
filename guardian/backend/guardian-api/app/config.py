from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

# Load the repo-root .env (guardian/.env) regardless of the process working directory, then any
# backend-local .env on top. Real environment variables still take precedence over both.
_APP_DIR = Path(__file__).resolve()
_ROOT_ENV = _APP_DIR.parents[3] / ".env"
_LOCAL_ENV = _APP_DIR.parents[1] / ".env"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=(str(_ROOT_ENV), str(_LOCAL_ENV)), extra="ignore")

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

    # Cloud AI (primary crash reasoner). Empty = not configured; the phone falls back on-device.
    azure_openai_endpoint: str = ""
    azure_openai_api_key: str = ""
    azure_openai_deployment: str = ""
    azure_openai_api_version: str = "2024-06-01"

    # Azure OpenAI text-to-speech for the spoken emergency call message.
    azure_tts_endpoint: str = ""
    azure_tts_api_key: str = ""
    azure_tts_deployment: str = "gpt-4o-mini-tts"
    azure_tts_api_version: str = "2025-03-01-preview"

    @property
    def ai_configured(self) -> bool:
        return bool(self.azure_openai_endpoint and self.azure_openai_api_key and self.azure_openai_deployment)

    @property
    def cors_origin_list(self) -> list[str]:
        return [o.strip() for o in self.cors_origins.split(",") if o.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
