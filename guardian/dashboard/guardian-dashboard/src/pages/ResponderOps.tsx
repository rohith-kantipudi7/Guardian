import {
  Alert,
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
import LocalPoliceOutlined from '@mui/icons-material/LocalPoliceOutlined';
import LocalHospitalOutlined from '@mui/icons-material/LocalHospitalOutlined';
import FamilyRestroomOutlined from '@mui/icons-material/FamilyRestroomOutlined';
import WarningAmberOutlined from '@mui/icons-material/WarningAmberOutlined';
import ShieldOutlined from '@mui/icons-material/ShieldOutlined';
import SmartphoneOutlined from '@mui/icons-material/SmartphoneOutlined';
import type { ReactNode } from 'react';
import { KpiCard } from '../components/KpiCard';
import { config } from '../config';
import { useBlackBox, useDeviceData, usePhoneData } from '../services/device';

function severityColor(sev?: string): 'error' | 'warning' | 'info' | 'default' {
  if (sev === 'CRITICAL') return 'error';
  if (sev === 'MODERATE') return 'warning';
  if (sev === 'MINOR') return 'info';
  return 'default';
}

function Responder({
  name,
  contact,
  icon,
  active,
}: Readonly<{ name: string; contact: string; icon: ReactNode; active: boolean }>) {
  return (
    <Stack
      direction="row"
      spacing={1.5}
      sx={{ alignItems: 'center', p: 1.5, borderRadius: 2, border: 1, borderColor: 'divider' }}
    >
      <Box sx={{ color: active ? 'error.main' : 'text.secondary' }}>{icon}</Box>
      <Box sx={{ flex: 1 }}>
        <Typography sx={{ fontWeight: 700 }}>{name}</Typography>
        <Typography variant="caption" color="text.secondary">
          {contact}
        </Typography>
      </Box>
      <Chip
        size="small"
        label={active ? 'Alerting' : 'Standby'}
        color={active ? 'error' : 'default'}
        variant={active ? 'filled' : 'outlined'}
        sx={{ fontWeight: 700 }}
      />
    </Stack>
  );
}

export function ResponderOps() {
  const data = useDeviceData(config.deviceUrl);
  const phone = usePhoneData(config.deviceUrl);
  const blackbox = useBlackBox(config.deviceUrl);

  const d = data.data;
  const p = phone.data;
  const bb = blackbox.data;
  const deviceOnline = !data.isError && !!d;
  const phoneOnline = !!p?.phoneOnline;
  const backupAlert = !!d?.backupAlert;
  const active = backupAlert || p?.state === 'EMERGENCY' || !!bb?.eventId;

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4">Responder ops</Typography>
        <Typography color="text.secondary">
          Incidents and escalation, fed live from the Guardian device — no backend required.
        </Typography>
      </Box>

      {!deviceOnline && (
        <Alert severity="warning">Device offline. Connect to the Guardian hotspot to see live incidents.</Alert>
      )}
      {backupAlert && (
        <Alert severity="error" variant="filled" sx={{ fontWeight: 700 }}>
          BACKUP ALERT — the device escalated on its own because the phone was offline during a collision.
        </Alert>
      )}

      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(4, minmax(0, 1fr))' } }}>
        <KpiCard
          label="Active incident"
          value={active ? 'Yes' : 'None'}
          icon={<WarningAmberOutlined />}
          tone={active ? 'danger' : 'success'}
        />
        <KpiCard label="Total collisions" value={d?.count ?? '—'} icon={<ShieldOutlined />} tone={d?.count ? 'warning' : 'default'} />
        <KpiCard
          label="Phone (primary)"
          value={phoneOnline ? 'Online' : 'Offline'}
          hint={p?.state}
          icon={<SmartphoneOutlined />}
          tone={phoneOnline ? 'success' : 'warning'}
        />
        <KpiCard
          label="Device (backup)"
          value={deviceOnline ? 'Online' : 'Offline'}
          icon={<ShieldOutlined />}
          tone={deviceOnline ? 'success' : 'warning'}
        />
      </Box>

      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '3fr 2fr' } }}>
        <Card>
          <CardContent>
            <Typography variant="overline" color="text.secondary">
              Latest incident
            </Typography>
            {bb?.eventId ? (
              <Stack spacing={1.2} sx={{ mt: 1 }}>
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                  <Chip size="small" color={severityColor(bb.severity)} label={bb.severity ?? '—'} />
                  <Chip size="small" variant="outlined" label={`${Math.round((bb.confidence ?? 0) * 100)}% confidence`} />
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
                No incident yet. A phone crash (or a device collision while the phone is offline) will appear here.
              </Typography>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardContent>
            <Typography variant="overline" color="text.secondary">
              Escalation {active ? '· ALERTING' : '· standby'}
            </Typography>
            <Stack spacing={1.2} sx={{ mt: 1 }}>
              <Responder name="Police" contact="112" icon={<LocalPoliceOutlined />} active={active} />
              <Responder name="Ambulance" contact="108" icon={<LocalHospitalOutlined />} active={active} />
              <Responder name="Family" contact="Emergency contacts" icon={<FamilyRestroomOutlined />} active={active} />
            </Stack>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1.5 }}>
              Delivery to responders needs the Guardian server + internet; on this hotspot it shows the escalation state.
            </Typography>
          </CardContent>
        </Card>
      </Box>

      <Card>
        <CardContent>
          <Typography variant="overline" color="text.secondary">
            Device‑detected impacts
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
                    <Typography color="text.secondary">No impacts recorded.</Typography>
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
