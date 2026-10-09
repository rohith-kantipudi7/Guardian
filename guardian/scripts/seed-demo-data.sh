#!/usr/bin/env bash
# Seeds demo user/vehicle/device and one crash with the phone disconnected.
set -euo pipefail
cd "$(dirname "$0")/../simulator/vehicle-simulator"
python3 simulator.py reset
python3 simulator.py seed
python3 simulator.py phone_disconnect
python3 simulator.py severe_crash
python3 simulator.py phone_connect
