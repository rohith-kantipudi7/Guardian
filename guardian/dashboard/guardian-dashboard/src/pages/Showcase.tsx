import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  ButtonGroup,
  Card,
  CardContent,
  Chip,
  LinearProgress,
  Stack,
  Typography,
} from '@mui/material';
import PlayArrowRounded from '@mui/icons-material/PlayArrowRounded';
import RestartAltRounded from '@mui/icons-material/RestartAltRounded';
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded';
import { Link as RouterLink } from 'react-router-dom';
import { ImpactChart } from '../components/ImpactChart';
import { NotifiedResponders } from '../components/NotifiedResponders';
import { PhoneMockup } from '../components/PhoneMockup';
import { SeverityBadge, StatusBadge } from '../components/SeverityBadge';
import { formatClock, formatPercent, severityHex } from '../format';
import { api } from '../services/api';
import { useCrashes, useSimState } from '../services/queries';
import { simulator, type SimState } from '../services/simulator';

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

interface StoryStep {
  title: string;
  narration: string;
  run: () => Promise<unknown>;
}

const STORY: StoryStep[] = [
  {
    title: 'Meet Guardian',
    narration:
      'A car fitted with the Guardian device (IMU, GPS, its own LTE modem, backup battery). The driver\'s phone is paired over Bluetooth. Normal driving — nothing happens.',
    run: async () => {
      await api.demoReset();
      await simulator.reset();
      await simulator.seed();
      await simulator.scenario('normal');
    },
  },
  {
    title: 'Severe crash',
    narration:
      'A severe frontal impact with a spin. The device scores it CRITICAL in under a second, alerts the phone over Bluetooth, and over its own LTE the backend instantly sends police, hospital and family an SMS with full details and a live link.',
    run: () => simulator.scenario('severe_crash'),
  },
  {
    title: 'The phone is gone',
    narration:
      'Now imagine the phone is destroyed, thrown from the car, or simply flat. The Bluetooth link drops — Guardian does not care.',
    run: () => simulator.phone(false),
  },
  {
    title: 'Crash without the phone',
    narration:
      'Another severe crash. No phone anywhere — the device still detects it and reports it directly. This is the core of Guardian.',
    run: () => simulator.scenario('severe_crash'),
  },
  {
    title: 'Dead zone',
    narration:
      'A crash in an area with no mobile coverage. The device stores the event in flash and keeps retrying.',
    run: async () => {
      await simulator.network('offline');
      await simulator.scenario('severe_crash');
    },
  },
  {
    title: 'Coverage returns',
    narration: 'As soon as the network is back the queued incident is delivered — exactly once, never duplicated.',
    run: () => simulator.network('online'),
  },
  {
    title: 'No false alarms',
    narration:
      'A pothole and an emergency stop produce sharp signals, but the crash engine rejects them. Nobody is alerted.',
    run: async () => {
      await simulator.scenario('pothole');
      await sleep(400);
      await simulator.scenario('hard_brake');
    },
  },
  {
    title: 'Phone reconnects',
    narration: 'The phone comes back and the device syncs the open emergency to it. The driver can still tap I\'M SAFE.',
    run: () => simulator.phone(true),
  },
  {
    title: 'Responders read the alert',
    narration:
      'Ambulance, police and family open the link in their SMS and get location, vehicle, driver and family numbers. Opening it is recorded as "seen" automatically — nobody has to click anything.',
    run: async () => {
      const [c] = await api.listCrashes({ active: true, limit: 1 });
      if (!c) return;
      for (const type of ['HOSPITAL', 'POLICE', 'EMERGENCY_CONTACT']) {
        const n = c.notifications.find((x) => x.recipientType === type);
        if (n) await api.getAlert(n.token);
        await sleep(700);
      }
    },
  },
  {
    title: 'Driver is safe',
    narration:
      "The driver taps I'M SAFE in the app (or on the device). The incident closes and every responder's page switches to \"Stand down\".",
    run: async () => {
      const [c] = await api.listCrashes({ active: true, limit: 1 });
      if (c) await api.cancel(c.eventId, 'Driver (Guardian app)', "Driver tapped I'M SAFE");
    },
  },
];

const SCENARIOS: { name: string; label: string }[] = [
  { name: 'normal', label: 'Normal drive' },
  { name: 'hard_brake', label: 'Hard brake' },
  { name: 'pothole', label: 'Pothole' },
  { name: 'minor_impact', label: 'Minor impact' },
  { name: 'rollover', label: 'Rollover' },
  { name: 'severe_crash', label: 'Severe crash' },
];

function DeviceFlags({ sim }: { sim: SimState }) {
  const flag = (label: string, ok: boolean) => (
    <Chip size="small" label={label} color={ok ? 'success' : 'error'} variant={ok ? 'outlined' : 'filled'} />
  );
  return (
    <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
      {flag(sim.lastHeartbeatOk === false ? 'Device offline' : 'Device online', sim.lastHeartbeatOk !== false)}
      {flag(`LTE ${sim.network.toLowerCase()}`, sim.network !== 'OFFLINE')}
      {flag(sim.phoneConnected ? 'Phone linked' : 'Phone gone', sim.phoneConnected)}
      {sim.pendingEvents > 0 && <Chip size="small" color="warning" label={`${sim.pendingEvents} queued on device`} />}
    </Stack>
  );
}

export function Showcase() {
  const queryClient = useQueryClient();
  const sim = useSimState();
  const recent = useCrashes({ limit: 6 });
  const [step, setStep] = useState(0);
  const s = sim.data;

  const refresh = (state?: unknown) => {
    if (state && typeof state === 'object' && 'deviceId' in state) queryClient.setQueryData(['sim'], state);
    queryClient.invalidateQueries();
  };
  const run = useMutation({ mutationFn: (fn: () => Promise<unknown>) => fn(), onSuccess: refresh });
  const runStep = useMutation({
    mutationFn: async (i: number) => STORY[i].run(),
    onSuccess: (state, i) => {
      refresh(state);
      setStep(Math.min(i + 1, STORY.length));
    },
  });
  const busy = run.isPending || runStep.isPending;

  // Phone alert: while linked, the device pushes its newest open emergency to the phone
  // (including ones raised while the phone was away, synced on reconnect).
  const phoneAlert = s?.phoneConnected
    ? ((recent.data ?? []).find((c) => c.status === 'EMERGENCY_ACTIVE' || c.status === 'ACKNOWLEDGED') ?? null)
    : null;
  const imSafe = useMutation({
    mutationFn: (id: string) => api.cancel(id, "Driver (Guardian app)", "Occupant tapped I'M SAFE on the phone"),
    onSuccess: () => refresh(),
  });

  const outcome = s?.lastOutcome;
  const error = (run.error ?? runStep.error ?? imSafe.error) as Error | null;
  const focus =
    [...(recent.data ?? [])].sort((a, b) => Date.parse(b.updatedAt) - Date.parse(a.updatedAt))[0] ?? null;

  return (
    <Stack spacing={3}>
      <Stack direction={{ xs: 'column', md: 'row' }} sx={{ justifyContent: 'space-between', gap: 2 }}>
        <Box>
          <Typography variant="h4">Showcase</Typography>
          <Typography color="text.secondary">
            One screen, three actors: the <b>vehicle device</b>, the <b>driver's phone</b> and the <b>responders</b>{' '}
            (police, hospital, family) who are alerted automatically. Everything below is driven by the real backend and
            the device's real crash engine — only the sensors are simulated.
          </Typography>
        </Box>
        <Button
          startIcon={<RestartAltRounded />}
          color="inherit"
          variant="outlined"
          disabled={busy || !s}
          onClick={() => {
            setStep(0);
            run.mutate(async () => {
              await api.demoReset();
              return simulator.reset();
            });
          }}
          sx={{ alignSelf: 'flex-start', whiteSpace: 'nowrap' }}
        >
          Reset demo
        </Button>
      </Stack>

      {sim.isError && (
        <Alert severity="warning">
          The virtual vehicle device is not running. Start it in a terminal:{' '}
          <code>cd simulator/vehicle-simulator &amp;&amp; python simulator.py serve</code>
        </Alert>
      )}
      {error && <Alert severity="error">{error.message}</Alert>}

      <Card sx={{ borderColor: 'primary.dark' }}>
        <CardContent>
          <Stack direction="row" useFlexGap spacing={1} sx={{ flexWrap: 'wrap', pb: 1 }}>
            {STORY.map((st, i) => (
              <Chip
                key={st.title}
                label={`${i + 1}. ${st.title}`}
                onClick={() => setStep(i)}
                color={i === step ? 'primary' : i < step ? 'success' : 'default'}
                variant={i === step ? 'filled' : 'outlined'}
              />
            ))}
          </Stack>
          {step < STORY.length ? (
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ alignItems: { md: 'center' }, mt: 1.5 }}>
              <Box sx={{ flex: 1 }}>
                <Typography variant="h6">
                  Step {step + 1} of {STORY.length} — {STORY[step].title}
                </Typography>
                <Typography color="text.secondary">{STORY[step].narration}</Typography>
              </Box>
              <Button
                size="large"
                variant="contained"
                startIcon={<PlayArrowRounded />}
                disabled={busy || !s}
                onClick={() => runStep.mutate(step)}
                sx={{ whiteSpace: 'nowrap' }}
              >
                Run step {step + 1}
              </Button>
            </Stack>
          ) : (
            <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mt: 1.5 }}>
              <Typography sx={{ flex: 1 }}>
                <b>Story complete.</b> "The phone is gone. The emergency signal isn't."
              </Typography>
              <Button component={RouterLink} to="/" variant="contained" endIcon={<ArrowForwardRounded />}>
                See every incident in the Live monitor
              </Button>
            </Stack>
          )}
          {busy && <LinearProgress sx={{ mt: 2 }} />}
        </CardContent>
      </Card>

      <Box
        sx={{
          display: 'grid',
          gap: 2,
          gridTemplateColumns: {
            xs: 'minmax(0, 1fr)',
            md: 'minmax(0, 1fr) 300px',
            xl: 'minmax(0, 5fr) minmax(280px, 3fr) minmax(0, 4fr)',
          },
          alignItems: 'start',
        }}
      >
        <Card>
          <CardContent>
            <Typography variant="overline" color="text.secondary">
              1 · Vehicle device (virtual)
            </Typography>
            {s ? (
              <Stack spacing={2} sx={{ mt: 1 }}>
                <DeviceFlags sim={s} />
                <Box>
                  <Typography variant="caption" color="text.secondary">
                    Drive a scenario
                  </Typography>
                  <Box sx={{ display: 'grid', gap: 1, gridTemplateColumns: 'repeat(auto-fill, minmax(110px, 1fr))', mt: 0.5 }}>
                    {SCENARIOS.map((sc) => (
                      <Button
                        key={sc.name}
                        size="small"
                        variant={sc.name === 'severe_crash' ? 'contained' : 'outlined'}
                        color={sc.name === 'severe_crash' ? 'error' : 'primary'}
                        disabled={busy}
                        onClick={() => run.mutate(() => simulator.scenario(sc.name))}
                      >
                        {sc.label}
                      </Button>
                    ))}
                  </Box>
                </Box>
                <Stack direction="row" spacing={2} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
                  <ButtonGroup size="small" disabled={busy}>
                    <Button
                      variant={s.phoneConnected ? 'contained' : 'outlined'}
                      onClick={() => run.mutate(() => simulator.phone(true))}
                    >
                      Phone on
                    </Button>
                    <Button
                      variant={!s.phoneConnected ? 'contained' : 'outlined'}
                      color="secondary"
                      onClick={() => run.mutate(() => simulator.phone(false))}
                    >
                      Phone gone
                    </Button>
                  </ButtonGroup>
                  <ButtonGroup size="small" disabled={busy}>
                    <Button
                      variant={s.network === 'ONLINE' ? 'contained' : 'outlined'}
                      onClick={() => run.mutate(() => simulator.network('online'))}
                    >
                      LTE on
                    </Button>
                    <Button
                      variant={s.network === 'OFFLINE' ? 'contained' : 'outlined'}
                      color="warning"
                      onClick={() => run.mutate(() => simulator.network('offline'))}
                    >
                      Dead zone
                    </Button>
                  </ButtonGroup>
                </Stack>

                <Box sx={{ p: 2, borderRadius: 2, bgcolor: 'action.hover' }}>
                  {outcome ? (
                    <Stack spacing={1}>
                      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 1 }}>
                        <Typography sx={{ fontWeight: 800 }}>{outcome.scenario.replace('_', ' ')}</Typography>
                        {outcome.severity && <SeverityBadge severity={outcome.severity} />}
                        {outcome.confidence != null && (
                          <Typography sx={{ color: severityHex[outcome.severity ?? 'NORMAL'], fontWeight: 800 }}>
                            {formatPercent(outcome.confidence)}
                          </Typography>
                        )}
                        <Box sx={{ flex: 1 }} />
                        <Chip
                          size="small"
                          color={!outcome.emergency ? 'default' : outcome.delivered ? 'success' : 'warning'}
                          label={
                            !outcome.emergency
                              ? 'No emergency'
                              : outcome.delivered
                                ? 'Responders alerted'
                                : 'Queued on device'
                          }
                        />
                      </Stack>
                      {outcome.reasons.length > 0 && (
                        <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', rowGap: 0.5 }}>
                          {outcome.reasons.map((r) => (
                            <Chip key={r} size="small" variant="outlined" label={r.replace(/_/g, ' ')} />
                          ))}
                        </Stack>
                      )}
                      <Typography variant="caption" color="text.secondary">
                        {outcome.transitions.length ? outcome.transitions.join(' → ') : 'NORMAL (no trigger)'}
                      </Typography>
                      <ImpactChart trace={outcome.trace} />
                    </Stack>
                  ) : (
                    <Typography color="text.secondary">Run a scenario to see what the crash engine decides.</Typography>
                  )}
                </Box>
              </Stack>
            ) : (
              <Typography color="text.secondary" sx={{ mt: 1 }}>
                Waiting for the virtual device…
              </Typography>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardContent>
            <Typography variant="overline" color="text.secondary">
              2 · Driver's phone (companion app)
            </Typography>
            <Box sx={{ mt: 1 }}>
              <PhoneMockup
                phoneConnected={s?.phoneConnected ?? true}
                alert={phoneAlert}
                battery={s?.battery}
                busy={imSafe.isPending}
                onImSafe={() => phoneAlert && imSafe.mutate(phoneAlert.eventId)}
              />
            </Box>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1.5, textAlign: 'center' }}>
              Same screens as the Android app. The phone only shows alerts — it never carries the emergency.
            </Typography>
          </CardContent>
        </Card>

        <Card>
          <CardContent>
            <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
              <Typography variant="overline" color="text.secondary">
                3 · Responders (alerted automatically)
              </Typography>
              <Button component={RouterLink} to="/" size="small" endIcon={<ArrowForwardRounded />}>
                Monitor
              </Button>
            </Stack>
            {focus && (
              <Box sx={{ mt: 1, mb: 2 }}>
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1 }}>
                  <SeverityBadge severity={focus.severity} />
                  <StatusBadge status={focus.status} />
                  <Typography variant="caption" color="text.secondary">
                    {formatClock(focus.timestamp)}
                  </Typography>
                </Stack>
                <NotifiedResponders notifications={focus.notifications} compact />
              </Box>
            )}
            <Typography variant="caption" color="text.secondary">
              All incidents
            </Typography>
            <Stack spacing={1} sx={{ mt: 0.5 }}>
              {(recent.data ?? []).length === 0 && <Typography color="text.secondary">No incidents yet.</Typography>}
              {(recent.data ?? []).map((c) => (
                <Box
                  key={c.eventId}
                  component={RouterLink}
                  to={`/?incident=${encodeURIComponent(c.eventId)}`}
                  sx={{
                    p: 1.5,
                    borderRadius: 2,
                    border: 1,
                    borderColor: 'divider',
                    borderLeft: 4,
                    borderLeftColor: severityHex[c.severity],
                    textDecoration: 'none',
                    color: 'inherit',
                    '&:hover': { borderColor: 'primary.main' },
                  }}
                >
                  <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                    <Typography sx={{ fontWeight: 800, color: severityHex[c.severity] }}>
                      {c.severity} · {formatPercent(c.confidence)}
                    </Typography>
                    <StatusBadge status={c.status} />
                  </Stack>
                  <Typography variant="body2" color="text.secondary">
                    {formatClock(c.timestamp)} · {c.vehicleId} ·{' '}
                    <Box component="span" sx={{ color: c.phoneConnected ? 'text.secondary' : 'secondary.main', fontWeight: 700 }}>
                      {c.phoneConnected ? 'phone linked' : 'NO PHONE'}
                    </Box>
                  </Typography>
                </Box>
              ))}
            </Stack>
          </CardContent>
        </Card>
      </Box>

      {s && s.log.length > 0 && (
        <Card>
          <CardContent>
            <Typography variant="overline" color="text.secondary">
              Device log
            </Typography>
            <Box
              component="pre"
              sx={{ m: 0, mt: 1, maxHeight: 180, overflow: 'auto', fontSize: 12, color: 'text.secondary', whiteSpace: 'pre-wrap' }}
            >
              {s.log.slice(-15).join('\n')}
            </Box>
          </CardContent>
        </Card>
      )}
    </Stack>
  );
}
