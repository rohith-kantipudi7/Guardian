import type { ReactNode } from 'react';
import { Box, Card, CardContent, Chip, Stack, Step, StepLabel, Stepper, Typography } from '@mui/material';
import PhoneDisabledOutlined from '@mui/icons-material/PhoneDisabledOutlined';
import PhoneAndroidOutlined from '@mui/icons-material/PhoneAndroidOutlined';
import MemoryOutlined from '@mui/icons-material/MemoryOutlined';
import PsychologyOutlined from '@mui/icons-material/PsychologyOutlined';
import GraphicEqOutlined from '@mui/icons-material/GraphicEqOutlined';
import ImageOutlined from '@mui/icons-material/ImageOutlined';
import DescriptionOutlined from '@mui/icons-material/DescriptionOutlined';
import ShowChartOutlined from '@mui/icons-material/ShowChartOutlined';
import SignalCellularConnectedNoInternet0BarOutlined from '@mui/icons-material/SignalCellularConnectedNoInternet0BarOutlined';
import { formatClock, formatCoords, formatDuration, severityHex } from '../format';
import { useNow } from '../hooks/useNow';
import { useDevices, useUser, useVehicles } from '../services/queries';
import type { BlackBoxEvidence, CrashEvent, EmergencyAction, EvidenceKind } from '../types';
import { NotifiedResponders } from './NotifiedResponders';
import { SeverityBadge, StatusBadge } from './SeverityBadge';
import { VehicleMap } from './VehicleMap';

function Section({ title, extra, children }: { title: string; extra?: ReactNode; children: ReactNode }) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Stack direction="row" sx={{ alignItems: 'center', mb: 1.5 }}>
          <Typography variant="overline" color="text.secondary" sx={{ flex: 1, lineHeight: 1.5 }}>
            {title}
          </Typography>
          {extra}
        </Stack>
        {children}
      </CardContent>
    </Card>
  );
}

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Typography component="div" sx={{ fontWeight: 700 }}>
        {children}
      </Typography>
    </Box>
  );
}

const fmt = (v: number | null, unit: string, digits = 1) => (v === null ? '—' : `${v.toFixed(digits)} ${unit}`);

const EVIDENCE_ICON: Record<EvidenceKind, typeof ImageOutlined> = {
  AUDIO: GraphicEqOutlined,
  IMAGE: ImageOutlined,
  SENSOR_LOG: ShowChartOutlined,
  TRANSCRIPT: DescriptionOutlined,
  AI_ASSESSMENT: PsychologyOutlined,
};

function EvidenceRow({ e }: { e: BlackBoxEvidence }) {
  const Icon = EVIDENCE_ICON[e.kind] ?? DescriptionOutlined;
  return (
    <Stack direction="row" spacing={1.5} sx={{ py: 0.75 }}>
      <Icon fontSize="small" sx={{ mt: 0.25, color: 'text.secondary', flexShrink: 0 }} />
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
          <Typography variant="body2" sx={{ fontWeight: 600 }}>
            {e.label}
          </Typography>
          <Chip
            size="small"
            label={e.source === 'DEVICE' ? 'Device black box' : 'Phone'}
            variant="outlined"
            sx={{ height: 18, fontSize: 10 }}
          />
        </Stack>
        {e.kind === 'IMAGE' && e.mediaUrl && (
          <Box
            component="img"
            src={e.mediaUrl}
            alt={e.label}
            sx={{ mt: 0.75, width: '100%', maxWidth: 300, borderRadius: 1, display: 'block' }}
          />
        )}
        {e.contentText && (
          <Typography
            component="pre"
            variant="caption"
            color="text.secondary"
            sx={{ mt: 0.5, m: 0, whiteSpace: 'pre-wrap', fontFamily: 'ui-monospace, monospace' }}
          >
            {e.contentText}
          </Typography>
        )}
        {e.kind === 'AUDIO' && !e.contentText && (
          <Typography variant="caption" color="text.secondary">
            Stored in black box · {e.mediaMime ?? 'audio'}
          </Typography>
        )}
      </Box>
    </Stack>
  );
}

function BlackBox({ incident: c }: { incident: CrashEvent }) {
  if (!c.aiAssessment && c.evidence.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        No black-box data captured for this incident.
      </Typography>
    );
  }
  return (
    <Stack spacing={1}>
      {c.aiAssessment && (
        <Box
          sx={{
            p: 1.5,
            borderRadius: 1,
            bgcolor: 'rgba(109,74,224,.08)',
            border: '1px solid',
            borderColor: 'rgba(109,74,224,.3)',
          }}
        >
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 0.5 }}>
            <PsychologyOutlined fontSize="small" color="info" />
            <Typography variant="caption" sx={{ fontWeight: 700 }}>
              On-device AI assessment
            </Typography>
          </Stack>
          <Typography variant="body2">{c.aiAssessment}</Typography>
        </Box>
      )}
      {c.evidence.map((e, i) => (
        <EvidenceRow key={i} e={e} />
      ))}
    </Stack>
  );
}

function progressStep(c: CrashEvent): number {
  if (c.status === 'RESOLVED' || c.status === 'CANCELLED') return 4;
  if (c.acknowledgedAt) return 3;
  if (c.notifications.length) return 2;
  return 1;
}

function TimelineItem({ a }: { a: EmergencyAction }) {
  const human = !!a.operator || a.status === 'OPENED';
  return (
    <Stack direction="row" spacing={1.5} sx={{ py: 0.75 }}>
      <Box
        sx={{ mt: 0.75, width: 8, height: 8, borderRadius: '50%', flexShrink: 0, bgcolor: human ? 'primary.main' : 'text.disabled' }}
      />
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography variant="body2">{a.message.replace('[SIMULATED] ', '')}</Typography>
        <Typography variant="caption" color="text.secondary">
          {formatClock(a.createdAt)}
        </Typography>
      </Box>
    </Stack>
  );
}

/** Read-only incident view: everything here happens automatically or is driven by the responders. */
export function IncidentDetail({ incident: c }: { incident: CrashEvent }) {
  const now = useNow();
  const devices = useDevices();
  const vehicles = useVehicles();
  const vehicle = vehicles.data?.find((v) => v.id === c.vehicleId);
  const owner = useUser(vehicle?.userId);
  const device = devices.data?.find((d) => d.id === c.deviceId);

  const open = ['DETECTED', 'VERIFYING', 'EMERGENCY_ACTIVE', 'ACKNOWLEDGED'].includes(c.status);
  const step = progressStep(c);
  const elapsed = (c.closedAt ? Date.parse(c.closedAt) : now) - Date.parse(c.timestamp);
  const caption = (text: ReactNode) => (
    <Typography variant="caption" color="text.secondary">
      {text}
    </Typography>
  );

  return (
    <Stack spacing={2}>
      <Card sx={{ borderTop: 4, borderTopColor: severityHex[c.severity] }}>
        <CardContent>
          <Stack direction="row" spacing={2} sx={{ alignItems: 'flex-start' }}>
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <Stack direction="row" useFlexGap spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap', mb: 1 }}>
                <SeverityBadge severity={c.severity} size="medium" />
                <StatusBadge status={c.status} />
                {c.alertSource === 'DEVICE' && (
                  <Chip
                    size="small"
                    icon={<MemoryOutlined />}
                    label="Alert sent by device black box"
                    sx={{ bgcolor: 'rgba(82,97,122,.14)', color: 'secondary.main' }}
                  />
                )}
                {c.alertSource === 'PHONE' && (
                  <Chip
                    size="small"
                    icon={<PhoneAndroidOutlined />}
                    label="Alert sent by phone"
                    sx={{ bgcolor: 'rgba(30,142,78,.14)', color: 'success.main' }}
                  />
                )}
                {c.alertSource === null && !c.phoneConnected && (
                  <Chip
                    size="small"
                    icon={<PhoneDisabledOutlined />}
                    label="No phone · sent by device"
                    sx={{ bgcolor: 'rgba(82,97,122,.14)', color: 'secondary.main' }}
                  />
                )}
                {!c.cellularConnected && (
                  <Chip
                    size="small"
                    icon={<SignalCellularConnectedNoInternet0BarOutlined />}
                    label="Sent after coverage gap"
                    sx={{ bgcolor: 'rgba(245,158,11,.15)', color: 'warning.main' }}
                  />
                )}
              </Stack>
              <Typography variant="h5">{vehicle?.registrationNumber ?? c.vehicleId ?? 'Unknown vehicle'}</Typography>
              <Typography variant="body2" color="text.secondary">
                {[vehicle && `${vehicle.make ?? ''} ${vehicle.model ?? ''}`.trim(), c.vehicleId, `crash at ${formatClock(c.timestamp)}`]
                  .filter(Boolean)
                  .join(' · ')}
              </Typography>
            </Box>
            <Box sx={{ textAlign: 'right', flexShrink: 0 }}>
              <Typography variant="caption" color="text.secondary">
                {open ? 'Since crash' : 'Closed in'}
              </Typography>
              <Typography variant="h4" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                {formatDuration(elapsed)}
              </Typography>
            </Box>
          </Stack>

          <Stepper activeStep={step} alternativeLabel sx={{ mt: 3 }}>
            <Step completed={step > 0}>
              <StepLabel optional={caption(formatClock(c.timestamp))}>Crash detected</StepLabel>
            </Step>
            <Step completed={step > 1}>
              <StepLabel optional={caption(`${c.notifications.length} alerted`)}>Responders alerted</StepLabel>
            </Step>
            <Step completed={step > 2}>
              <StepLabel optional={c.acknowledgedBy && caption(`first: ${c.acknowledgedBy}`)}>Seen by responders</StepLabel>
            </Step>
            <Step completed={step > 3}>
              <StepLabel error={c.status === 'CANCELLED'} optional={c.closedBy && caption(c.closedBy)}>
                {c.status === 'CANCELLED' ? 'Driver safe' : 'Closed'}
              </StepLabel>
            </Step>
          </Stepper>
          {!open && c.resolution && (
            <Typography variant="body2" color="text.secondary" sx={{ textAlign: 'center', mt: 1.5 }}>
              {c.resolution}
            </Typography>
          )}
        </CardContent>
      </Card>

      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: 'minmax(0, 1fr)', xl: 'repeat(2, minmax(0, 1fr))' } }}>
        <Box sx={{ gridColumn: { xl: '1 / -1' } }}>
          <Section
            title="Black box"
            extra={<Chip size="small" label={`${c.evidence.length} record${c.evidence.length === 1 ? '' : 's'}`} variant="outlined" />}
          >
            <BlackBox incident={c} />
          </Section>
        </Box>

        <Section title="Responders">
          <NotifiedResponders notifications={c.notifications} />
        </Section>

        <Section title="Location" extra={caption(formatCoords(c.latitude, c.longitude))}>
          <VehicleMap incidents={[c]} focusId={c.eventId} height={260} />
        </Section>

        <Section title="Crash data">
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: 'repeat(auto-fill, minmax(130px, 1fr))' }}>
            <Fact label="Impact">{fmt(c.accelerationPeak, 'g')}</Fact>
            <Fact label="Rotation">{fmt(c.rotationPeak, 'rad/s')}</Fact>
            <Fact label="Speed before">{fmt(c.speed, 'km/h', 0)}</Fact>
            <Fact label="Driver">{owner.data ? owner.data.name : '—'}</Fact>
            <Fact label="Driver phone">{owner.data?.phone ?? '—'}</Fact>
            <Fact label="Phone at crash">{c.phoneConnected ? 'Connected' : 'Not reachable'}</Fact>
            <Fact label="Device now">
              <Box component="span" sx={{ color: device?.online ? 'success.main' : 'error.main' }}>
                {device ? (device.online ? 'Online' : 'Offline') : '—'}
              </Box>
            </Fact>
          </Box>
        </Section>

        <Section title="Timeline" extra={<Chip size="small" label="SMS simulated" variant="outlined" />}>
          <Box sx={{ maxHeight: 280, overflowY: 'auto', pr: 1 }}>
            <TimelineItem
              a={{
                channel: 'DEVICE',
                status: 'DETECTED',
                message: `Crash detected on the vehicle device (${c.severity})`,
                operator: null,
                createdAt: c.timestamp,
              }}
            />
            {c.actions.map((a, i) => (
              <TimelineItem key={i} a={a} />
            ))}
          </Box>
        </Section>
      </Box>
    </Stack>
  );
}
