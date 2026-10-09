# Starts the Guardian backend and dashboard in separate windows (Windows PowerShell).
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

$api = Join-Path $root "backend\guardian-api"
if (-not (Test-Path (Join-Path $api ".venv"))) {
    Write-Host "Creating backend virtualenv..."
    python -m venv (Join-Path $api ".venv")
    & (Join-Path $api ".venv\Scripts\python.exe") -m pip install -q -r (Join-Path $api "requirements.txt")
}
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$api'; .\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --host 0.0.0.0 --port 8000"

$dash = Join-Path $root "dashboard\guardian-dashboard"
if (-not (Test-Path (Join-Path $dash "node_modules"))) {
    Write-Host "Installing dashboard dependencies..."
    Push-Location $dash; npm install; Pop-Location
}
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$dash'; npm run dev"

Write-Host "Backend:   http://localhost:8000/docs"
Write-Host "Dashboard: http://localhost:5173"
Write-Host "Then: cd simulator\vehicle-simulator; python simulator.py seed"
