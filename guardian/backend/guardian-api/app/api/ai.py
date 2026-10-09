"""Cloud crash-reasoning endpoint.

The phone calls this as its PRIMARY crash-understanding path. When an Azure OpenAI deployment is
configured (see app.config), the signals are sent to the hosted LLM; otherwise this returns 503 so
the phone transparently falls back to its on-device model. Emergency handling stays SIMULATED.
"""

from __future__ import annotations

import json
import logging

import httpx
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.config import get_settings

log = logging.getLogger("guardian.ai")

router = APIRouter(prefix="/api/ai", tags=["ai"])


class AssessRequest(BaseModel):
    severity: str
    confidence: float
    accel_peak_g: float = Field(alias="accelPeakG")
    rotation_peak_rad_s: float = Field(alias="rotationPeakRadS")
    speed_kmh: float | None = Field(default=None, alias="speedKmh")
    transcript: str | None = None

    model_config = {"populate_by_name": True}


class AssessResponse(BaseModel):
    headline: str
    what_happened: str = Field(serialization_alias="whatHappened")
    occupant_state: str = Field(serialization_alias="occupantState")
    recommendation: str
    model: str

    model_config = {"populate_by_name": True}


_SYSTEM_PROMPT = (
    "You are Guardian, an emergency crash-response assistant. Given vehicle crash sensor signals "
    "(and any in-cabin transcript), produce a concise, factual incident assessment for first "
    "responders. Respond ONLY with JSON: "
    '{"headline","whatHappened","occupantState","recommendation"}.'
)


def _user_prompt(body: AssessRequest) -> str:
    parts = [
        f"severity={body.severity}",
        f"confidence={body.confidence:.2f}",
        f"peak_deceleration_g={body.accel_peak_g:.1f}",
        f"peak_rotation_rad_s={body.rotation_peak_rad_s:.1f}",
    ]
    if body.speed_kmh is not None:
        parts.append(f"speed_before_kmh={body.speed_kmh:.0f}")
    if body.transcript:
        parts.append(f"cabin_transcript={body.transcript!r}")
    return "Crash signals: " + ", ".join(parts)


@router.post("/assess", response_model=AssessResponse, response_model_by_alias=True)
async def assess(body: AssessRequest) -> AssessResponse:
    settings = get_settings()
    if not settings.ai_configured:
        # Not wired to a cloud model yet: tell the phone to use its on-device reasoner.
        raise HTTPException(status_code=503, detail="Cloud AI is not configured")

    url = (
        f"{settings.azure_openai_endpoint.rstrip('/')}/openai/deployments/"
        f"{settings.azure_openai_deployment}/chat/completions"
        f"?api-version={settings.azure_openai_api_version}"
    )
    payload = {
        "messages": [
            {"role": "system", "content": _SYSTEM_PROMPT},
            {"role": "user", "content": _user_prompt(body)},
        ],
        "temperature": 0.2,
        "response_format": {"type": "json_object"},
    }
    try:
        async with httpx.AsyncClient(timeout=12.0) as client:
            resp = await client.post(url, headers={"api-key": settings.azure_openai_api_key}, json=payload)
            resp.raise_for_status()
            content = resp.json()["choices"][0]["message"]["content"]
            parsed = json.loads(content)
    except (httpx.HTTPError, KeyError, ValueError) as exc:
        log.warning("cloud AI call failed: %s", exc)
        raise HTTPException(status_code=502, detail="Cloud AI request failed") from exc

    return AssessResponse(
        headline=parsed.get("headline", f"{body.severity.title()} crash"),
        what_happened=parsed.get("whatHappened", ""),
        occupant_state=parsed.get("occupantState", ""),
        recommendation=parsed.get("recommendation", ""),
        model=f"Azure/{settings.azure_openai_deployment}",
    )
