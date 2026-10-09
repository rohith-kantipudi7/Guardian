import type { ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Alert, Box, Button, Card, CardContent, Chip, CircularProgress, Container, Stack, Typography } from '@mui/material';
import ShieldOutlined from '@mui/icons-material/ShieldOutlined';
import MapOutlined from '@mui/icons-material/MapOutlined';
import CallOutlined from '@mui/icons-material/CallOutlined';
import PhoneDisabledOutlined from '@mui/icons-material/PhoneDisabledOutlined';
import { useParams } from 'react-router-dom';
import { MapView } from '../components/map/MapView';
import { formatClock, formatDuration, formatPercent, formatTime, recipientLabel, severityHex } from '../format';
import { useNow } from '../hooks/useNow';
import { api } from '../services/api';
import type { AlertView } from '../types';

const fmt = (v: number | null, unit: string, digits = 1) => (v === null ? 'n/a' : `${v.toFixed(digits)} ${unit}`);

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Card>
      <CardContent>
        <Typography variant="overline" color="text.secondary">
          {title}
        </Typography>
        <Box sx={{ mt: 0.5 }}>{children}</Box>
      </CardContent>
    </Card>
  );
}

function CallRow({ label, name, phone }: { label: string; name: string; phone: string }) {
  return (
    <Stack direction="row" spacing={1} sx={{ alignItems: 'center', py: 0.75 }}>
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography variant="caption" color="text.secondary">
          {label}
        </Typography>
        <Typography sx={{ fontWeight: 700 }}>{name}</Typography>
      </Box>
      <Button variant="outlined" size="small" startIcon={<CallOutlined />} href={`tel:${phone}`}>
        {phone}
      </Button>
    </Stack>
  );
}

function Closed({ a }: { a: AlertView }) {
  if (a.status === 'CANCELLED') {
    return (
      <Alert severity="info" variant="filled" sx={{ fontSize: 16 }}>
        <b>Stand down.</b> The driver confirmed they are safe — no help needed.
      </Alert>
    );
  }
  return (
    <Alert severity="success" variant="filled" sx={{ fontSize: 16 }}>
      <b>Incident closed.</b>
      {a.resolution ? ` ${a.resolution}.` : ''}
    </Alert>
  );
}

/** What police, hospital and family see when they open the link in their alert SMS. */
export function ResponderAlert() {
  const { token = '' } = useParams();
  const now = useNow();
  const alert = useQuery({
    queryKey: ['alert', token],
    queryFn: () => api.getAlert(token),
    refetchInterval: 3000,
    retry: false,
  });

  if (alert.isLoading) {
    return (
      <Box sx={{ display: 'grid', placeItems: 'center', minHeight: '100vh' }}>
        <CircularProgress />
      </Box>
    );
  }
  if (alert.isError || !alert.data) {
    return (
      <Container maxWidth="sm" sx={{ py: 6 }}>
        <Alert severity="error">This alert link is invalid or has expired.</Alert>
      </Container>
    );
  }

  const a = alert.data;
  const open = ['DETECTED', 'VERIFYING', 'EMERGENCY_ACTIVE', 'ACKNOWLEDGED'].includes(a.status);
  const hasFix = a.latitude !== null && a.longitude !== null;

  return (
    <Box sx={{ height: '100vh', overflowY: 'auto', overflowX: 'hidden', bgcolor: 'background.default', pb: 4 }}>
      <Box sx={{ bgcolor: severityHex[a.severity], color: '#fff', py: 2 }}>
        <Container maxWidth="sm">
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            <ShieldOutlined />
            <Typography sx={{ fontWeight: 900, letterSpacing: 1.5, flex: 1 }}>GUARDIAN CRASH ALERT</Typography>
            <Chip size="small" label="SIMULATED" sx={{ bgcolor: 'rgba(0,0,0,.25)', color: '#fff', fontWeight: 700 }} />
          </Stack>
          <Typography variant="h4" sx={{ fontWeight: 900, mt: 1 }}>
            {a.severity} crash
          </Typography>
          <Typography sx={{ opacity: 0.9 }}>
            {formatTime(a.timestamp)} · {formatDuration(now - Date.parse(a.timestamp))} ago ·{' '}
            {formatPercent(a.confidence)} confidence
          </Typography>
          <Typography variant="body2" sx={{ opacity: 0.9, mt: 0.5 }}>
            Sent to: {recipientLabel[a.recipientType]} · {a.recipientName}
          </Typography>
        </Container>
      </Box>

      <Container maxWidth="sm" sx={{ mt: 2 }}>
        <Stack spacing={2}>
          {!open && <Closed a={a} />}

          {!a.phoneConnected && (
            <Alert icon={<PhoneDisabledOutlined />} severity="warning" variant="outlined">
              The driver's phone was <b>not reachable</b>. This alert was sent by the vehicle's own crash device.
            </Alert>
          )}

          <Section title="Location">
            {hasFix ? (
              <>
                <MapView
                  markers={[
                    {
                      id: a.eventId,
                      latitude: a.latitude as number,
                      longitude: a.longitude as number,
                      label: a.vehicleRegistration ?? 'Crash',
                      severity: a.severity,
                    },
                  ]}
                  focusId={a.eventId}
                  height={220}
                />
                <Stack direction="row" spacing={1} sx={{ mt: 1.5, alignItems: 'center' }}>
                  <Typography sx={{ flex: 1, fontFamily: 'monospace' }}>
                    {a.latitude?.toFixed(5)}, {a.longitude?.toFixed(5)}
                  </Typography>
                  <Button
                    variant="contained"
                    startIcon={<MapOutlined />}
                    href={a.mapsUrl ?? '#'}
                    target="_blank"
                    rel="noopener"
                  >
                    Navigate
                  </Button>
                </Stack>
              </>
            ) : (
              <Typography color="text.secondary">No GPS fix at the time of the crash.</Typography>
            )}
          </Section>

          <Section title="Vehicle and crash data">
            <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 1.5 }}>
              <Box>
                <Typography variant="caption" color="text.secondary">
                  Vehicle
                </Typography>
                <Typography sx={{ fontWeight: 700 }}>{a.vehicleRegistration ?? 'Unknown'}</Typography>
                {a.vehicleDescription && (
                  <Typography variant="body2" color="text.secondary">
                    {a.vehicleDescription}
                  </Typography>
                )}
              </Box>
              <Box>
                <Typography variant="caption" color="text.secondary">
                  Speed before crash
                </Typography>
                <Typography sx={{ fontWeight: 700 }}>{fmt(a.speed, 'km/h', 0)}</Typography>
              </Box>
              <Box>
                <Typography variant="caption" color="text.secondary">
                  Impact
                </Typography>
                <Typography sx={{ fontWeight: 700 }}>{fmt(a.accelerationPeak, 'g')}</Typography>
              </Box>
              <Box>
                <Typography variant="caption" color="text.secondary">
                  Rotation
                </Typography>
                <Typography sx={{ fontWeight: 700 }}>{fmt(a.rotationPeak, 'rad/s')}</Typography>
              </Box>
            </Box>
          </Section>

          <Section title="People">
            {a.driver ? (
              <CallRow label="Driver / owner" name={a.driver.name} phone={a.driver.phone} />
            ) : (
              <Typography color="text.secondary">No owner profile on file.</Typography>
            )}
            {a.emergencyContacts.map((c) => (
              <CallRow
                key={c.phone}
                label={`Emergency contact${c.relationship ? ` · ${c.relationship}` : ''}`}
                name={c.name}
                phone={c.phone}
              />
            ))}
          </Section>

          <Section title="Who else was alerted">
            <Stack spacing={1}>
              {a.responders.map((r) => (
                <Stack key={`${r.recipientType}-${r.recipientName}`} direction="row" sx={{ alignItems: 'center' }}>
                  <Typography sx={{ flex: 1 }}>
                    <b>{recipientLabel[r.recipientType]}</b> · {r.recipientName}
                  </Typography>
                  <Chip
                    size="small"
                    color={r.openedAt ? 'success' : 'default'}
                    label={r.openedAt ? `Seen ${formatClock(r.openedAt)}` : 'Alerted'}
                  />
                </Stack>
              ))}
            </Stack>
          </Section>

          <Typography variant="caption" color="text.secondary" sx={{ textAlign: 'center' }}>
            Prototype: no real police, hospital or phone number is contacted. Incident {a.eventId}.
          </Typography>
        </Stack>
      </Container>
    </Box>
  );
}
