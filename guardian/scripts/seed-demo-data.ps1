# Seeds demo user/vehicle/device and one crash with the phone disconnected.
$ErrorActionPreference = "Stop"
$sim = Join-Path (Split-Path -Parent $PSScriptRoot) "simulator\vehicle-simulator"
Push-Location $sim
try {
    python simulator.py reset
    python simulator.py seed
    python simulator.py phone_disconnect
    python simulator.py severe_crash
    python simulator.py phone_connect
} finally {
    Pop-Location
}
