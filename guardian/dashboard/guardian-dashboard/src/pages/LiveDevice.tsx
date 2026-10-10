import type { ReactNode } from 'react';
import {
  Alert,
  Avatar,
  Box,
  Card,
  CardContent,
  Chip,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import RouterOutlined from '@mui/icons-material/RouterOutlined';
import SmartphoneOutlined from '@mui/icons-material/SmartphoneOutlined';
import GroupsOutlined from '@mui/icons-material/GroupsOutlined';
import WarningAmberOutlined from '@mui/icons-material/WarningAmberOutlined';
import SensorsOutlined from '@mui/icons-material/SensorsOutlined';
import { KpiCard } from '../components/KpiCard';
import { TransferVisual } from '../components/TransferVisual';
import { CallVisual } from '../components/CallVisual';
import { config } from '../config';
import { useBackupAlert } from '../hooks/useBackupAlert';
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

function nodeState(online: boolean | null) {
  if (online == null) return { color: 'text.disabled', text: 'Standby', chip: 'default' as const };
  if (online) return { color: 'success.main', text: 'Online', chip: 'success' as const };
  return { color: 'warning.main', text: 'Offline', chip: 'warning' as const };
}

function Node({
  label,
  sub,
  online,
  icon,
}: Readonly<{ label: string; sub: string; online: boolean | null; icon: ReactNode }>) {
  const st = nodeState(online);
  return (
    <Stack spacing={0.5} sx={{ alignItems: 'center', minWidth: 110 }}>
      <Avatar sx={{ bgcolor: 'transparent', border: 2, borderColor: st.color, color: st.color, width: 48, height: 48 }}>
        {icon}
      </Avatar>
      <Typography variant="body2" sx={{ fontWeight: 700 }}>
        {label}
      </Typography>
      <Typography variant="caption" color="text.secondary">
        {sub}
      </Typography>
      <Chip size="small" label={st.text} variant="outlined" color={st.chip} sx={{ fontWeight: 700 }} />
    </Stack>
  );
}

function Link({ ok }: Readonly<{ ok: boolean }>) {
  return <Box sx={{ flex: 1, height: 2, maxWidth: 90, bgcolor: ok ? 'success.main' : 'divider', borderRadius: 1 }} />;
}

export function LiveDevice() {
  const baseUrl = config.deviceUrl;

  const data = useDeviceData(baseUrl);
  const phone = usePhoneData(baseUrl);
  const blackbox = useBlackBox(baseUrl);

  const deviceOnline = !data.isError && !!data.data;
  const d = data.data;
  const p = phone.data;
  const bb = blackbox.data;
  const phoneOnline = !!p?.phoneOnline;
  const [alert] = useBackupAlert(d, p);
  const backupAlert = alert.active;

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4">Live network</Typography>
        <Typography color="text.secondary">
          Real‑time feed from the Guardian network — the phone (primary) and the IoT device (backup). No backend, no
          simulator.
        </Typography>
      </Box>

      {backupAlert && (
        <Alert severity="error" variant="filled" sx={{ fontWeight: 700 }}>
          BACKUP ALERT — the device detected a collision while the phone was offline and escalated on its own.
        </Alert>
      )}

      <Card>
        <CardContent>
          <Typography variant="overline" color="text.secondary">
            Network
          </Typography>
          <Stack direction="row" spacing={1} sx={{ mt: 1.5, alignItems: 'center', justifyContent: 'center' }}>
            <Node label="Phone" sub="Primary" online={phoneOnline} icon={<SmartphoneOutlined />} />
            <Link ok={phoneOnline && deviceOnline} />
            <Node label="Black box" sub="IoT device" online={deviceOnline} icon={<RouterOutlined />} />
            <Link ok={backupAlert} />
            <Node
              label="Responders"
              sub={backupAlert ? 'Alerting' : 'Needs internet'}
              online={backupAlert ? true : null}
              icon={<GroupsOutlined />}
            />
          </Stack>
        </CardContent>
      </Card>

      {data.isError && (
        <Alert severity="warning">
          Can't reach the Guardian device. Make sure this machine is on the same Wi‑Fi/hotspot. ({(data.error as Error).message})
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
              Black box (ESP32 + IMU)
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

      <TransferVisual bb={bb} live={!!bb || phoneOnline} />

      <CallVisual bb={bb} />

      <Card>
        <CardContent>
          <Typography variant="overline" color="text.secondary">
            Contents transferred to the black box
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
