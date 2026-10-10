const apiUrl = (import.meta.env.VITE_API_URL ?? 'http://localhost:8000').replace(/\/$/, '');

export const config = {
  apiUrl,
  wsUrl: import.meta.env.VITE_WS_URL ?? `${apiUrl.replace(/^http/, 'ws')}/ws/emergency`,
  mapProvider: (import.meta.env.VITE_MAP_PROVIDER ?? 'leaflet') as 'leaflet' | 'none',
  simulatorUrl: (import.meta.env.VITE_SIMULATOR_URL ?? 'http://127.0.0.1:8100').replace(/\/$/, ''),
  // The real Guardian IoT device's web server (phone hotspot / LAN). No backend, no simulator.
  deviceUrl: (import.meta.env.VITE_DEVICE_URL ?? 'http://192.168.137.61').replace(/\/$/, ''),
};
