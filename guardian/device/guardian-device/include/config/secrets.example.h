#pragma once
// Copy to secrets.h (git-ignored) and fill in. Never commit real secrets.

#define GUARDIAN_DEVICE_ID "GD-001"
#define GUARDIAN_VEHICLE_ID "VH-001"
// Must match the secret used when registering the device with the backend.
#define GUARDIAN_DEVICE_SECRET "dev-secret-change-me"

// Backend reached over the independent network path (cellular, or Wi-Fi in dev builds).
#define GUARDIAN_BACKEND_HOST "192.168.1.10"
#define GUARDIAN_BACKEND_PORT 8000
#define GUARDIAN_BACKEND_TLS 0

// Dev builds only: Wi-Fi stands in for the LTE modem.
#define GUARDIAN_WIFI_SSID "your-ssid"
#define GUARDIAN_WIFI_PASSWORD "your-password"

// LTE modem APN
#define GUARDIAN_APN "internet"
#define GUARDIAN_APN_USER ""
#define GUARDIAN_APN_PASS ""
