import { Alert, Box, Stack, Typography } from '@mui/material';
import { DeviceStatus } from '../components/DeviceStatus';
import { useDevices } from '../services/queries';

export function Devices() {
  const devices = useDevices();
  return (
    <Stack spacing={2}>
      <Typography variant="h4" sx={{ fontWeight: 800 }}>
        Devices
      </Typography>
      {devices.isError && <Alert severity="error">{(devices.error as Error).message}</Alert>}
      {devices.data?.length === 0 && <Typography color="text.secondary">No registered devices.</Typography>}
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: 'minmax(0, 1fr)', md: 'repeat(2, minmax(0, 1fr))', xl: 'repeat(3, minmax(0, 1fr))' } }}>
        {(devices.data ?? []).map((d) => (
          <DeviceStatus key={d.id} device={d} />
        ))}
      </Box>
    </Stack>
  );
}
