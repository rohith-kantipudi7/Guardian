import type { Severity } from '../../types';

export interface MapMarker {
  id: string;
  latitude: number;
  longitude: number;
  label: string;
  severity?: Severity;
}

export interface MapViewProps {
  markers: MapMarker[];
  height?: number;
  /** Marker to center on; defaults to the first marker. */
  focusId?: string;
}

export const DEFAULT_CENTER: [number, number] = [12.9716, 77.5946];
