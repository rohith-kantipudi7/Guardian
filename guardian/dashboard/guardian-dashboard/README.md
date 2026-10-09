# Guardian Emergency Dashboard

React + TypeScript + Vite + MUI + React Query. Receives live events from the backend over
WebSocket (`/ws/emergency`) and shows incidents, devices, vehicles and a live map.

## Run

```powershell
npm install
copy .env.example .env      # optional; defaults point to http://localhost:8000
npm run dev                 # http://localhost:5173
```

Build: `npm run build` (type-check + bundle into `dist/`).

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `VITE_API_URL` | `http://localhost:8000` | Backend REST base URL |
| `VITE_WS_URL` | derived from API URL | Emergency WebSocket |
| `VITE_MAP_PROVIDER` | `leaflet` | `leaflet` (OSM tiles, needs internet) or `none` (offline coordinates view) |

## Pages

| Route | Purpose |
|---|---|
| `/` | Response center: active incidents, live map, device status, recent events |
| `/incidents` | Full history with Active/Closed filter |
| `/incidents/:id` | Incident detail, ACKNOWLEDGE / RESOLVE, simulated workflow log |
| `/devices` | Device health (online, phone, cellular, GPS, battery) |
| `/vehicles` | Registered vehicles |

The key state — `phoneConnected=false` with device ONLINE — is rendered as a normal incident
with an explicit "reported directly by the vehicle device" banner.

## Map abstraction

`src/components/map/MapView.tsx` selects a provider. To add a provider (e.g. Google/Mapbox),
implement a component taking `MapViewProps` and add it to `MapView`.
