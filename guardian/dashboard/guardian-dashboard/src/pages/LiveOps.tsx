import { useEffect } from 'react';
import { Alert, Box, Card, CardContent, Stack, Typography } from '@mui/material';
import WarningAmberOutlined from '@mui/icons-material/WarningAmberOutlined';
import TimerOutlined from '@mui/icons-material/TimerOutlined';
import RouterOutlined from '@mui/icons-material/RouterOutlined';
import PhoneDisabledOutlined from '@mui/icons-material/PhoneDisabledOutlined';
import { useSearchParams } from 'react-router-dom';
import { IncidentDetail } from '../components/IncidentDetail';
import { IncidentQueue } from '../components/IncidentQueue';
import { KpiCard } from '../components/KpiCard';
import { VehicleMap } from '../components/VehicleMap';
import { formatDuration, triageOrder } from '../format';
import { useCrash, useCrashes, useStats } from '../services/queries';
import { ACTIVE_STATUSES } from '../types';

export function LiveOps() {
  const [params, setParams] = useSearchParams();
  const active = useCrashes({ active: true });
  const stats = useStats();
  const queue = [...(active.data ?? [])].filter((c) => ACTIVE_STATUSES.includes(c.status)).sort(triageOrder);
  const selectedId = params.get('incident') ?? queue[0]?.eventId;
  const selected = useCrash(selectedId);
  const s = stats.data;

  // Keep the URL pointing at a real incident once the queue loads.
  useEffect(() => {
    if (!params.get('incident') && queue[0]) setParams({ incident: queue[0].eventId }, { replace: true });
  }, [params, queue, setParams]);

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4">Live monitor</Typography>
        <Typography color="text.secondary">Every crash and who is responding, updated live.</Typography>
      </Box>

      {active.isError && <Alert severity="error">Backend unreachable: {(active.error as Error).message}</Alert>}

      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(4, minmax(0, 1fr))' } }}>
        <KpiCard
          label="Open incidents"
          value={s?.activeIncidents ?? '—'}
          hint={s?.criticalActive ? `${s.criticalActive} critical` : undefined}
          icon={<WarningAmberOutlined />}
          tone={s?.activeIncidents ? 'danger' : 'success'}
        />
        <KpiCard
          label="Avg. time to seen"
          value={s?.avgAcknowledgeSeconds != null ? formatDuration(s.avgAcknowledgeSeconds * 1000) : '—'}
          hint="target < 60 s"
          icon={<TimerOutlined />}
          tone={s?.avgAcknowledgeSeconds != null && s.avgAcknowledgeSeconds > 60 ? 'warning' : 'default'}
        />
        <KpiCard
          label="Devices online"
          value={s ? `${s.devicesOnline}/${s.devicesTotal}` : '—'}
          icon={<RouterOutlined />}
          tone={s && s.devicesOnline < s.devicesTotal ? 'warning' : 'success'}
        />
        <KpiCard
          label="Sent without phone"
          value={s?.phoneIndependentIncidents ?? '—'}
          hint={s ? `of ${s.totalIncidents} total` : undefined}
          icon={<PhoneDisabledOutlined />}
        />
      </Box>

      {queue.length === 0 ? (
        <Card>
          <CardContent>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={3} sx={{ alignItems: 'center' }}>
              <Box sx={{ flex: 1 }}>
                <Typography variant="h5" color="success.main">
                  All clear
                </Typography>
                <Typography color="text.secondary">No open incidents. Run a crash from the Showcase to see one here.</Typography>
              </Box>
              <Box sx={{ width: { xs: '100%', md: 420 } }}>
                <VehicleMap incidents={[]} height={200} />
              </Box>
            </Stack>
          </CardContent>
        </Card>
      ) : (
        <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: 'minmax(0, 1fr)', lg: '320px minmax(0, 1fr)' }, alignItems: 'start' }}>
          <Box>
            <Typography variant="overline" color="text.secondary">
              Incident queue ({queue.length})
            </Typography>
            <IncidentQueue
              incidents={queue}
              selectedId={selectedId}
              onSelect={(id) => setParams({ incident: id })}
            />
          </Box>
          <Box>{selected.data && <IncidentDetail incident={selected.data} />}</Box>
        </Box>
      )}
    </Stack>
  );
}
