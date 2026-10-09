# Guardian API (FastAPI)

Receives heartbeats and crash events directly from Guardian devices (over cellular),
stores them, runs the **simulated** emergency workflow and pushes live updates to
the emergency dashboard over WebSocket.

## Run

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1          # Linux/macOS: source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

- OpenAPI UI: http://localhost:8000/docs
- Health: http://localhost:8000/health
- WebSocket: ws://localhost:8000/ws/emergency

## Test

```powershell
python -m pytest -q
```

## Configuration (env or `.env`)

| Variable | Default | Purpose |
|---|---|---|
| `DATABASE_URL` | `sqlite:///./guardian.db` | Any SQLAlchemy URL (PostgreSQL-compatible schema) |
| `REQUIRE_DEVICE_AUTH` | `true` | Require `X-Device-Secret` on heartbeat/crash |
| `DEVICE_OFFLINE_AFTER_SECONDS` | `30` | Heartbeat age after which a device is OFFLINE |
| `CORS_ORIGINS` | `http://localhost:5173,...` | Dashboard origins |
| `LOCATION_RETENTION_DAYS` | `30` | Clear coordinates of closed incidents after N days (0 = keep) |
| `DEMO_MODE` | `true` | Reported on `/health` |

## Layout

```text
app/
├── main.py              # app factory, CORS, error mapping, /health
├── config.py            # settings
├── domain.py            # enums + utcnow
├── database/            # engine/session/Base
├── models/              # SQLAlchemy models
├── schemas/             # Pydantic (camelCase JSON)
├── repositories/        # data access
├── services/            # device, crash (state transitions, dedup), emergency (SIMULATED)
├── realtime/            # WebSocket broadcaster
└── api/                 # routers: users, vehicles, devices, crashes, ws
```

See [../../docs/api.md](../../docs/api.md) for the full contract.
