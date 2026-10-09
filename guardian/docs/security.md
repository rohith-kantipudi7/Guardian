# Security (POC scope)

| Control | Implementation | Production follow-up |
|---|---|---|
| Device authentication | Per-device secret, `X-Device-Secret` on heartbeat + crash; stored as salted HMAC-SHA256; re-registration requires the original secret | mTLS / per-device certificates, secret rotation, provisioning service |
| No committed secrets | `.env`, `include/config/secrets.h` git-ignored; `*.example` templates only | Secret manager (e.g. Azure Key Vault) |
| Transport | `GUARDIAN_BACKEND_TLS=1` for device HTTPS (modem SSL / WiFiClientSecure). Android allows cleartext only for local POC networking | TLS everywhere, CA pinning on device, HSTS |
| Request validation | Pydantic: ID pattern, coordinate/confidence/battery ranges, enums, sizes | Rate limiting, WAF |
| Exactly-once / replay | Unique `eventId` constraint; duplicates never stored or broadcast | Signed payloads with timestamps/nonces |
| State transitions | Explicit allow-list; invalid → 409 | Operator authentication + audit log for dashboard actions |
| BLE | Optional encrypted + bonded link (`GUARDIAN_BLE_REQUIRE_ENCRYPTION=1`); demo commands compiled out with `GUARDIAN_DEMO_MODE=0` | Passkey/OOB pairing, signed commands |
| Location minimisation | Coordinates cleared on closed incidents after `LOCATION_RETENTION_DAYS` (default 30) | Data-retention policy, consent |
| Vehicle safety | OBD read-only allow-list (AT + Mode 01) in firmware | Hardware CAN-TX disable |
| CORS | Allow-list via `CORS_ORIGINS`; only GET/POST and needed headers | — |
| Dashboard / user endpoints | **Unauthenticated in the POC** (local demo) | OIDC (e.g. Entra ID) for operators and app users |

Known POC limitations: the dashboard and the app's cancel endpoint are not authenticated;
the backend runs on plain HTTP locally; the default dev device secret must be changed.
