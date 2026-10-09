import { lazy, Suspense } from 'react';
import { Box, CircularProgress } from '@mui/material';
import { config } from '../../config';
import { CoordinatesMap } from './CoordinatesMap';
import type { MapViewProps } from './types';

export type { MapMarker, MapViewProps } from './types';

// Provider is chosen at build time; Leaflet is loaded lazily so the "none" provider ships no map code.
const LeafletMap = lazy(() => import('./LeafletMap'));

export function MapView(props: MapViewProps) {
  if (config.mapProvider === 'none') return <CoordinatesMap {...props} />;
  return (
    <Suspense
      fallback={
        <Box sx={{ height: props.height ?? 320, display: 'grid', placeItems: 'center' }}>
          <CircularProgress size={24} />
        </Box>
      }
    >
      <LeafletMap {...props} />
    </Suspense>
  );
}
