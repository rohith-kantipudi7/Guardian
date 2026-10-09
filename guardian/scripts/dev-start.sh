#!/usr/bin/env bash
# Starts the Guardian backend and dashboard (Linux/macOS). Ctrl+C stops both.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

API="$ROOT/backend/guardian-api"
if [ ! -d "$API/.venv" ]; then
  python3 -m venv "$API/.venv"
  "$API/.venv/bin/pip" install -q -r "$API/requirements.txt"
fi
(cd "$API" && .venv/bin/uvicorn app.main:app --reload --host 0.0.0.0 --port 8000) &
API_PID=$!

DASH="$ROOT/dashboard/guardian-dashboard"
[ -d "$DASH/node_modules" ] || (cd "$DASH" && npm install)
(cd "$DASH" && npm run dev) &
DASH_PID=$!

trap 'kill $API_PID $DASH_PID 2>/dev/null' EXIT
echo "Backend:   http://localhost:8000/docs"
echo "Dashboard: http://localhost:5173"
echo "Then: cd simulator/vehicle-simulator && python3 simulator.py seed"
wait
