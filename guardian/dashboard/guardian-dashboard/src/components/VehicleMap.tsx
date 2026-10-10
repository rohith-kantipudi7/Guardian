import { MapView, type MapMarker } from './map/MapView';
import type { CrashEvent } from '../types';

export function VehicleMap({ incidents, focusId, height }: { incidents: CrashEvent[]; focusId?: string; height?: number }) {
  const markers: MapMarker[] = incidents
    .filter((c) => c.latitude !== null && c.longitude !== null)
    .map((c) => ({
      id: c.eventId,
      latitude: c.latitude as number,
      longitude: c.longitude as number,
      label: `${c.vehicleId ?? c.deviceId} · collision`,
      severity: c.severity,
    }));
  return <MapView markers={markers} focusId={focusId} height={height} />;
}
