import { Card, CardContent, LinearProgress, Stack, Typography } from '@mui/material';
import { formatTime } from '../format';
import type { Device } from '../types';
import { ConnectionChip } from './SeverityBadge';

export function DeviceStatus({ device }: { device: Device }) {
  const battery = device.batteryLevel ?? 0;
  return (
    <Card variant="outlined">
      <CardContent>
        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="h6" sx={{ fontWeight: 700 }}>
            {device.id}
          </Typography>
          <ConnectionChip label="Device" ok={device.online} value={device.online ? 'ONLINE' : 'OFFLINE'} />
        </Stack>
        <Typography variant="body2" color="text.secondary">
          Vehicle {device.vehicleId ?? '—'} · FW {device.firmwareVersion ?? '—'}
        </Typography>

        <Stack direction="row" spacing={1} sx={{ mt: 1.5, flexWrap: 'wrap', rowGap: 1 }}>
          <ConnectionChip label="Phone" ok={device.phoneConnected} value={device.phoneConnected ? 'CONNECTED' : 'DISCONNECTED'} />
          <ConnectionChip
            label="Cellular"
            ok={device.cellularStatus === 'CONNECTED'}
            value={device.cellularStatus ?? 'UNKNOWN'}
          />
          <ConnectionChip label="GPS" ok={device.gpsStatus === 'AVAILABLE'} value={device.gpsStatus ?? 'UNKNOWN'} />
          <ConnectionChip
            label="Vehicle"
            ok={device.vehicleConnected}
            value={device.vehicleConnected ? 'CONNECTED' : 'NOT CONNECTED'}
          />
        </Stack>

        <Typography variant="body2" sx={{ mt: 1.5 }}>
          Battery {device.batteryLevel ?? '—'}%
        </Typography>
        <LinearProgress
          variant="determinate"
          value={battery}
          color={battery < 20 ? 'error' : 'success'}
          sx={{ height: 6, borderRadius: 3 }}
        />
        <Typography variant="caption" color="text.secondary">
          Last heartbeat {formatTime(device.lastHeartbeat)}
        </Typography>
      </CardContent>
    </Card>
  );
}
