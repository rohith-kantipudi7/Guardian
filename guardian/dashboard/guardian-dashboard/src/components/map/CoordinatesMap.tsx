import { Box, Link, Stack, Typography } from '@mui/material';
import { severityHex } from '../../format';
import type { MapViewProps } from './types';

/** Offline-safe map provider: shows coordinates with an external map link. */
export function CoordinatesMap({ markers, height = 320 }: MapViewProps) {
  return (
    <Box
      sx={{
        height,
        borderRadius: 2,
        border: '1px dashed',
        borderColor: 'divider',
        p: 2,
        overflow: 'auto',
      }}
    >
      <Typography variant="overline" color="text.secondary">
        Map (coordinates mode)
      </Typography>
      {markers.length === 0 && <Typography color="text.secondary">No locations</Typography>}
      <Stack spacing={1} sx={{ mt: 1 }}>
        {markers.map((m) => (
          <Stack key={m.id} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            <Box sx={{ width: 12, height: 12, borderRadius: '50%', bgcolor: severityHex[m.severity ?? 'CRITICAL'] }} />
            <Typography sx={{ fontFamily: 'monospace' }}>
              {m.latitude.toFixed(5)}, {m.longitude.toFixed(5)}
            </Typography>
            <Typography color="text.secondary">{m.label}</Typography>
            <Link
              href={`https://www.openstreetmap.org/?mlat=${m.latitude}&mlon=${m.longitude}#map=16/${m.latitude}/${m.longitude}`}
              target="_blank"
              rel="noopener noreferrer"
            >
              open
            </Link>
          </Stack>
        ))}
      </Stack>
    </Box>
  );
}
