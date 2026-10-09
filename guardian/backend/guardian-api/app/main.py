import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.api import ai, alerts, crashes, demo, devices, users, vehicles, ws
from app.config import get_settings
from app.database import SessionLocal, init_db
from app.realtime import broadcaster
from app.services.crash_service import CrashService
from app.services.errors import ServiceError

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("guardian")

VERSION = "0.1.0"


@asynccontextmanager
async def lifespan(_: FastAPI):
    init_db()
    with SessionLocal() as db:
        purged = CrashService(db).purge_old_locations()
        if purged:
            log.info("Location retention: cleared coordinates on %d closed incident(s)", purged)
    yield


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(
        title="Guardian API",
        version=VERSION,
        description="Guardian vehicle crash-response POC backend. Emergency workflows are SIMULATED.",
        lifespan=lifespan,
    )
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origin_list,
        allow_methods=["GET", "POST"],
        allow_headers=["Content-Type", "X-Device-Secret"],
    )

    @app.exception_handler(ServiceError)
    async def service_error_handler(_: Request, exc: ServiceError) -> JSONResponse:
        return JSONResponse(status_code=exc.status_code, content={"detail": exc.message})

    @app.get("/health", tags=["health"])
    def health() -> dict:
        return {
            "status": "ok",
            "version": VERSION,
            "demoMode": settings.demo_mode,
            "websocketClients": broadcaster.client_count,
        }

    for r in (
        users.router,
        vehicles.router,
        devices.router,
        crashes.router,
        crashes.stats_router,
        alerts.router,
        demo.router,
        ai.router,
        ws.router,
    ):
        app.include_router(r)
    return app


app = create_app()
