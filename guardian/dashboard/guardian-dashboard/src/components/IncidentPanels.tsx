import { Box, Card, CardContent, Chip, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material';
import CheckCircle from '@mui/icons-material/CheckCircle';
import RadioButtonUnchecked from '@mui/icons-material/RadioButtonUnchecked';
import type { BlackBox } from '../services/device';
import { useFirstSeen } from '../hooks/useFirstSeen';
import { MediaPhoto } from './MediaPhoto';

const fmt = (d?: Date) => (d ? d.toLocaleTimeString() : '—');

/** Media gallery: the real crash photos + audio from the phone (thumbnails if the phone is gone). */
export function MediaGallery({ bb }: Readonly<{ bb?: BlackBox }>) {
  const media = bb?.media ? bb.media.replace(/\/$/, '') : '';
  const id = bb?.eventId ?? '';
  const hasPhotos = (bb?.images ?? 0) > 0 && (media !== '' || !!bb?.thumbCabin || !!bb?.thumbRoad);
  return (
    <Card>
      <CardContent>
        <Typography variant="overline" color="text.secondary">Media gallery</Typography>
        {hasPhotos ? (
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ mt: 1 }}>
            <MediaPhoto src={`${media}/cabin.jpg?v=${id}`} label="Cabin camera" fallback={bb?.thumbCabin} maxHeight={260} />
            <MediaPhoto src={`${media}/road.jpg?v=${id}`} label="Road camera" fallback={bb?.thumbRoad} maxHeight={260} />
          </Stack>
        ) : (
          <Typography color="text.secondary" sx={{ mt: 1 }}>No photos captured for this incident yet.</Typography>
        )}
        {media && (bb?.preAudioSec ?? 0) > 0 && (
          <Box sx={{ mt: 1.5 }}>
            <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>Before impact ({bb?.preAudioSec}s)</Typography>
            <audio controls preload="none" src={`${media}/pre.m4a?v=${id}`} style={{ width: '100%' }} />
          </Box>
        )}
        {media && (bb?.audioSec ?? 0) > 0 && (
          <Box sx={{ mt: 1.5 }}>
            <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>After impact ({bb?.audioSec}s)</Typography>
            <audio controls preload="none" src={`${media}/after.m4a?v=${id}`} style={{ width: '100%' }} />
          </Box>
        )}
        {bb?.transcript && (
          <Typography variant="body2" sx={{ mt: 1.5, p: 1.25, bgcolor: 'action.hover', borderRadius: 1, fontStyle: 'italic' }}>
            “{bb.transcript}”
          </Typography>
        )}
      </CardContent>
    </Card>
  );
}

interface Stage { key: string; label: string; ok: boolean }

/** Transfer timeline: each stage with the time this dashboard first saw it complete. */
export function IncidentTimeline({ bb, collision, backupAlert }: Readonly<{ bb?: BlackBox; collision: boolean; backupAlert: boolean }>) {
  const record = !!bb?.eventId && bb.eventId !== 'LIVE' && (bb.severity ?? '').toUpperCase() !== 'MONITORING';
  const stages: Stage[] = [
    { key: 'collision', label: 'Collision detected', ok: collision },
    { key: 'record', label: 'Black box record received from the phone', ok: record },
    { key: 'photos', label: `Photos stored (${bb?.images ?? 0})`, ok: record && (bb?.images ?? 0) > 0 },
    { key: 'audio', label: `Cabin audio stored (${bb?.preAudioSec ?? 0}s + ${bb?.audioSec ?? 0}s)`, ok: record && (bb?.audioSec ?? 0) > 0 },
    { key: 'transcript', label: 'Audio transcribed on the phone (whisper)', ok: record && !!bb?.transcript },
    { key: 'ai', label: 'AI assessment attached', ok: record && !!bb?.assessment },
    { key: 'gps', label: 'GPS location attached', ok: record && !!bb?.location },
    { key: 'sms', label: `SMS sent to ${bb?.smsSent ?? 0} contact(s)`, ok: record && (bb?.smsSent ?? 0) > 0 },
    { key: 'backup', label: 'Backup alert raised by the device', ok: backupAlert },
  ];
  const seen = useFirstSeen(Object.fromEntries(stages.map((s) => [s.key, s.ok])), record ? (bb?.eventId ?? '') : '');
  return (
    <Card>
      <CardContent>
        <Typography variant="overline" color="text.secondary">Transfer timeline</Typography>
        <Stack spacing={0.9} sx={{ mt: 1 }}>
          {stages.map((s) => (
            <Stack key={s.key} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              {s.ok ? <CheckCircle fontSize="small" color="success" /> : <RadioButtonUnchecked fontSize="small" sx={{ color: 'text.disabled' }} />}
              <Typography variant="body2" sx={{ flex: 1, color: s.ok ? 'text.primary' : 'text.secondary' }}>{s.label}</Typography>
              <Typography variant="caption" color="text.secondary">{fmt(seen[s.key])}</Typography>
            </Stack>
          ))}
        </Stack>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
          Times are when this dashboard first observed each stage.
        </Typography>
      </CardContent>
    </Card>
  );
}

/** Dispatch log: what actually went out vs what is only shown (no SIM = calls are simulated). */
export function DispatchLog({ bb, active, backupAlert }: Readonly<{ bb?: BlackBox; active: boolean; backupAlert: boolean }>) {
  const sms = bb?.smsSent ?? 0;
  const rows: { target: string; channel: string; status: string; color: 'success' | 'warning' | 'error' | 'default' }[] = [
    { target: 'Family / emergency contacts', channel: 'SMS (cellular)', status: sms > 0 ? `Sent to ${sms}` : 'Not sent', color: sms > 0 ? 'success' : 'default' },
    { target: 'Police 112', channel: 'AI voice call', status: active ? 'Simulated (no SIM)' : 'Standby', color: active ? 'warning' : 'default' },
    { target: 'Ambulance 108', channel: 'AI voice call', status: active ? 'Simulated (no SIM)' : 'Standby', color: active ? 'warning' : 'default' },
    { target: 'Responders', channel: 'Backup alert', status: backupAlert ? 'Raised by device' : 'None', color: backupAlert ? 'error' : 'default' },
  ];
  return (
    <Card>
      <CardContent>
        <Typography variant="overline" color="text.secondary">Dispatch log</Typography>
        <Table size="small" sx={{ mt: 1 }}>
          <TableHead>
            <TableRow>
              <TableCell>Target</TableCell>
              <TableCell>Channel</TableCell>
              <TableCell align="right">Status</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((r) => (
              <TableRow key={r.target}>
                <TableCell>{r.target}</TableCell>
                <TableCell>{r.channel}</TableCell>
                <TableCell align="right"><Chip size="small" color={r.color} variant={r.color === 'default' ? 'outlined' : 'filled'} label={r.status} /></TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}
