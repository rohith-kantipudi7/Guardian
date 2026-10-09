import { Alert, Card, CardContent, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material';
import { useDevices, useVehicles } from '../services/queries';

export function Vehicles() {
  const vehicles = useVehicles();
  const devices = useDevices();
  const online = new Map((devices.data ?? []).map((d) => [d.id, d.online]));

  return (
    <Stack spacing={2}>
      <Typography variant="h4" sx={{ fontWeight: 800 }}>
        Vehicles
      </Typography>
      {vehicles.isError && <Alert severity="error">{(vehicles.error as Error).message}</Alert>}
      <Card variant="outlined">
        <CardContent sx={{ overflowX: 'auto' }}>
          <Table size="small" sx={{ minWidth: 640 }}>
            <TableHead>
              <TableRow>
                <TableCell>Vehicle</TableCell>
                <TableCell>Registration</TableCell>
                <TableCell>Make / Model</TableCell>
                <TableCell>Year</TableCell>
                <TableCell>Device</TableCell>
                <TableCell>Owner</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {(vehicles.data ?? []).map((v) => (
                <TableRow key={v.id}>
                  <TableCell sx={{ fontWeight: 700 }}>{v.id}</TableCell>
                  <TableCell>{v.registrationNumber}</TableCell>
                  <TableCell>{[v.make, v.model].filter(Boolean).join(' ') || '—'}</TableCell>
                  <TableCell>{v.year ?? '—'}</TableCell>
                  <TableCell sx={{ color: v.deviceId && online.get(v.deviceId) ? 'success.main' : 'text.secondary' }}>
                    {v.deviceId ? `${v.deviceId} (${online.get(v.deviceId) ? 'ONLINE' : 'OFFLINE'})` : '—'}
                  </TableCell>
                  <TableCell>{v.userId ?? '—'}</TableCell>
                </TableRow>
              ))}
              {vehicles.data?.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6}>No vehicles registered.</TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </Stack>
  );
}
