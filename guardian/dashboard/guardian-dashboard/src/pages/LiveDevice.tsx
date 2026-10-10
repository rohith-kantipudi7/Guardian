import { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import RouterOutlined from '@mui/icons-material/RouterOutlined';
import SmartphoneOutlined from '@mui/icons-material/SmartphoneOutlined';
import WarningAmberOutlined from '@mui/icons-material/WarningAmberOutlined';
import SensorsOutlined from '@mui/icons-material/SensorsOutlined';
import { KpiCard } from '../components/KpiCard';
import { config } from '../config';
import { useBlackBox, useDeviceData, usePhoneData } from '../services/device';

function Stat({ label, value }: Readonly<{ label: string; value: string }>) {
  return (
    <Box>
      <Typography variant="h5" sx={{ fontWeight: 800 }}>
        {value}
      </Typography>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
    </Box>
  );
}

export function LiveDevice() {
  const [input, setInput] = useState(config.deviceUrl);
  const [baseUrl, setBaseUrl] = useState(config.deviceUrl);

  const data = useDeviceData(baseUrl);
  const phone = usePhoneData(baseUrl);
  const blackbox = useBlackBox(baseUrl);

  const deviceOnline = !data.isError && !!data.data;
  const d = data.data;
  const p = phone.data;
  const bb = blackbox.data;
  const phoneOnline = !!p?.phoneOnline;

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4">Live device</Typography>
        <Typography color="text.secondary">
          Real‑time feed straight from the Guardian IoT device — its own sensors and the phone it is paired with. No
          backend, no simulator.
        </Typography>
      </Box>

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ alignItems: 'center' }}>
        <TextField
          size="small"
          label="Device address"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          sx={{ minWidth: 320 }}
        />
        <Button variant="contained" onClick={() => setBaseUrl(input.replace(/\/$/, ''))}>
          Connect
        </Button>
        <Chip
          label={deviceOnline ? 'DEVICE ONLINE' : 'DEVICE OFFLINE'}
          sx={{ fontWeight: 800, letterSpacing: 1 }}
          color={deviceOnline ? 'success' : 'warning'}
          variant="outlined"
        />
      </Stack>

      {data.isError && (
        <Alert severity="warning">
          Can't reach the device at {baseUrl}. Make sure this machine is on the same Wi‑Fi/hotspot and the IP is
          correct. ({(data.error as Error).message})
        </Alert>
      )}

      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(4, minmax(0, 1fr))' } }}>
        <KpiCard
          label="Device"
          value={deviceOnline ? 'Online' : 'Offline'}
          icon={<RouterOutlined />}
          tone={deviceOnline ? 'success' : 'warning'}
        />
        <KpiCard
          label="Phone link"
          value={phoneOnline ? 'Online' : 'Offline'}
          hint={p?.state}
          icon={<SmartphoneOutlined />}
          tone={phoneOnline ? 'success' : 'warning'}
        />
        <KpiCard
          label="Total collisions"
          value={d?.count ?? '—'}
          icon={<WarningAmberOutlined />}
          tone={d?.count ? 'danger' : 'default'}
        />
        <KpiCard
          label="Phone detection"
          value={p?.detection ? 'Active' : 'Off'}
          icon={<SensorsOutlined />}
          tone={p?.detection ? 'success' : 'default'}
        />
      </Box>

      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' } }}>
        <Card>
          <CardContent>
            <Typography variant="overline" color="text.secondary">
              This device (ESP32 + IMU)
            </Typography>
            <Stack direction="row" spacing={4} sx={{ mt: 1 }}>
              <Stat label="Acceleration" value={`${(d?.accel ?? 0).toFixed(2)} g`} />
              <Stat label="Rotation" value={`${Math.round(d?.gyro ?? 0)} dps`} />
              <Stat label="Uptime" value={`${Math.floor((d?.uptime ?? 0) / 1000)} s`} />
            </Stack>
          </CardContent>
        </Card>

        <Card>
          <CardContent>
            <Typography variant="overline" color="text.secondary">
              Phone (primary) {phoneOnline ? '· streaming' : '· offline'}
            </Typography>
            <Stack direction="row" spacing={4} sx={{ mt: 1 }}>
              <Stat label="Acceleration" value={`${(p?.accelG ?? 0).toFixed(2)} g`} />
              <Stat label="Rotation" value={`${(p?.rotationRadS ?? 0).toFixed(2)} rad/s`} />
              <Stat label="State" value={p?.state ?? '—'} />
            </Stack>
          </CardContent>
        </Card>
      </Box>

      <Card>
        <CardContent>
          <Typography variant="overline" color="text.secondary">
            Last black box (streamed from the phone)
          </Typography>
          {bb?.eventId ? (
            <Stack spacing={1} sx={{ mt: 1 }}>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                <Chip size="small" color="error" label={bb.severity ?? '—'} />
                <Chip size="small" variant="outlined" label={`${Math.round((bb.confidence ?? 0) * 100)}%`} />
                <Chip size="small" variant="outlined" label={`${bb.images ?? 0} photos`} />
                <Chip size="small" variant="outlined" label={`${bb.preAudioSec ?? 0}s + ${bb.audioSec ?? 0}s audio`} />
                <Typography variant="caption" color="text.secondary">
                  {bb.eventId}
                </Typography>
              </Stack>
              {bb.sensorLog && <Typography variant="body2">{bb.sensorLog}</Typography>}
              {bb.assessment && (
                <Typography variant="body2" color="text.secondary">
                  {bb.assessment}
                </Typography>
              )}
            </Stack>
          ) : (
            <Typography color="text.secondary" sx={{ mt: 1 }}>
              No black box received yet. Trigger a crash (or the app's “Send black box → device”).
            </Typography>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Typography variant="overline" color="text.secondary">
            Collision events (device‑detected)
          </Typography>
          <Table size="small" sx={{ mt: 1 }}>
            <TableHead>
              <TableRow>
                <TableCell>#</TableCell>
                <TableCell align="right">Accel (g)</TableCell>
                <TableCell align="right">Jerk (g)</TableCell>
                <TableCell align="right">Gyro (dps)</TableCell>
                <TableCell align="right">Uptime (s)</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {(d?.events ?? []).map((e) => (
                <TableRow key={`${e.n}-${e.t}`}>
                  <TableCell>{e.n}</TableCell>
                  <TableCell align="right">{e.accel.toFixed(2)}</TableCell>
                  <TableCell align="right">{e.jerk.toFixed(2)}</TableCell>
                  <TableCell align="right">{Math.round(e.gyro)}</TableCell>
                  <TableCell align="right">{(e.t / 1000).toFixed(1)}</TableCell>
                </TableRow>
              ))}
              {(d?.events?.length ?? 0) === 0 && (
                <TableRow>
                  <TableCell colSpan={5}>
                    <Typography color="text.secondary">No collisions recorded.</Typography>
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </Stack>
  );
}
