import { Box, Card, CardContent, Chip, Stack, Typography } from '@mui/material';
import SmartphoneOutlined from '@mui/icons-material/SmartphoneOutlined';
import RouterOutlined from '@mui/icons-material/RouterOutlined';
import CheckCircle from '@mui/icons-material/CheckCircle';
import RadioButtonUnchecked from '@mui/icons-material/RadioButtonUnchecked';
import PhotoCameraOutlined from '@mui/icons-material/PhotoCameraOutlined';
import GraphicEqOutlined from '@mui/icons-material/GraphicEqOutlined';
import PsychologyOutlined from '@mui/icons-material/PsychologyOutlined';
import LocationOnOutlined from '@mui/icons-material/LocationOnOutlined';
import RecordVoiceOverOutlined from '@mui/icons-material/RecordVoiceOverOutlined';
import type { ReactNode } from 'react';
import type { BlackBox } from '../services/device';
import { useStep } from '../hooks/useStep';

function Node({ icon, label }: Readonly<{ icon: ReactNode; label: string }>) {
  return (
    <Stack spacing={0.5} sx={{ alignItems: 'center', minWidth: 72 }}>
      <Box
        sx={{
          width: 44,
          height: 44,
          borderRadius: '50%',
          border: 2,
          borderColor: 'primary.main',
          color: 'primary.main',
          display: 'grid',
          placeItems: 'center',
        }}
      >
        {icon}
      </Box>
      <Typography variant="caption" sx={{ fontWeight: 700 }}>
        {label}
      </Typography>
    </Stack>
  );
}

/** A continuously-recording source: pulses while monitoring is live. */
function ContinuousRow({ icon, label, detail, live }: Readonly<{ icon: ReactNode; label: string; detail: string; live: boolean }>) {
  return (
    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
      <Box sx={{ color: live ? 'success.main' : 'text.disabled', display: 'flex' }}>{icon}</Box>
      <Box sx={{ flex: 1 }}>
        <Typography variant="body2" sx={{ fontWeight: 600 }}>{label}</Typography>
        <Typography variant="caption" color="text.secondary">{detail}</Typography>
      </Box>
      {live ? (
        <Box
          sx={{
            width: 10,
            height: 10,
            borderRadius: '50%',
            bgcolor: 'success.main',
            animation: 'pulse 1.1s ease-in-out infinite',
            '@keyframes pulse': {
              '0%,100%': { opacity: 1, transform: 'scale(1)' },
              '50%': { opacity: 0.35, transform: 'scale(0.7)' },
            },
          }}
        />
      ) : (
        <RadioButtonUnchecked fontSize="small" sx={{ color: 'text.disabled' }} />
      )}
    </Stack>
  );
}

/** A crash-only artifact: stays dim until an impact is detected, then lights up. */
function ImpactRow({ ok, icon, label }: Readonly<{ ok: boolean; icon: ReactNode; label: string }>) {
  return (
    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
      <Box sx={{ color: ok ? 'success.main' : 'text.disabled', display: 'flex' }}>{icon}</Box>
      <Typography variant="body2" sx={{ flex: 1, color: ok ? 'text.primary' : 'text.secondary' }}>{label}</Typography>
      {ok ? (
        <CheckCircle fontSize="small" sx={{ color: 'success.main' }} />
      ) : (
        <RadioButtonUnchecked fontSize="small" sx={{ color: 'text.disabled' }} />
      )}
    </Stack>
  );
}

/** Real captured photo served by the phone; hides itself if the phone is unreachable. */
function MediaPhoto({ src, label }: Readonly<{ src: string; label: string }>) {
  return (
    <Box sx={{ flex: 1, minWidth: 0 }}>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{label}</Typography>
      <Box
        component="img"
        src={src}
        alt={label}
        onError={(e) => { (e.currentTarget as HTMLImageElement).style.visibility = 'hidden'; }}
        onLoad={(e) => { (e.currentTarget as HTMLImageElement).style.visibility = 'visible'; }}
        sx={{ width: '100%', maxHeight: 180, objectFit: 'cover', borderRadius: 1, bgcolor: 'action.hover' }}
      />
    </Box>
  );
}

/** Visualises the always-on black box: continuous capture streaming, impact artifacts on crash. */
export function TransferVisual({ bb, live, collision }: Readonly<{ bb?: BlackBox; live: boolean; collision?: boolean }>) {
  // A real crash record (vs the "MONITORING" live snapshot the phone streams continuously).
  const record = !!bb && (bb.severity ?? '').toUpperCase() !== 'MONITORING' && bb.eventId !== 'LIVE';
  const crash = record || !!collision;
  // Collision message received: items load one by one; real data lights an item immediately.
  const step = useStep(crash, 4, 1500);
  const frames = bb?.frames ?? 0;
  const media = bb?.media ? bb.media.replace(/\/$/, '') : '';
  // Cache-bust per frame/event so the newest photo loads.
  const bust = `${frames}-${bb?.eventId ?? ''}`;

  const impact = [
    { ok: (bb?.audioSec ?? 0) > 0 || step >= 1, icon: <GraphicEqOutlined fontSize="small" />, label: `After‑impact audio (${bb?.audioSec ?? 0}s)` },
    { ok: !!bb?.transcript || step >= 2, icon: <RecordVoiceOverOutlined fontSize="small" />, label: 'Cabin audio transcript (whisper)' },
    { ok: (record && !!bb?.assessment) || step >= 3, icon: <PsychologyOutlined fontSize="small" />, label: 'AI assessment' },
    { ok: (record && !!bb?.location) || step >= 4, icon: <LocationOnOutlined fontSize="small" />, label: 'GPS location' },
  ];
  const done = impact.filter((i) => i.ok).length;

  return (
    <Card>
      <CardContent>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
          <Typography variant="overline" color="text.secondary">Live transfer · phone → black box</Typography>
          <Chip
            size="small"
            color={crash ? 'error' : 'success'}
            variant={crash ? 'filled' : 'outlined'}
            label={crash ? 'Impact · full record' : live ? 'Monitoring · recording' : 'Idle'}
          />
        </Stack>

        <Stack direction="row" sx={{ alignItems: 'center', my: 2 }}>
          <Node icon={<SmartphoneOutlined />} label="Phone" />
          <Box sx={{ flex: 1, position: 'relative', height: 4, mx: 2, bgcolor: 'divider', borderRadius: 2, overflow: 'hidden' }}>
            {live && (
              <Box
                sx={{
                  position: 'absolute',
                  top: 0,
                  height: '100%',
                  width: 48,
                  borderRadius: 2,
                  background: crash
                    ? 'linear-gradient(90deg, transparent, #ef4444, transparent)'
                    : 'linear-gradient(90deg, transparent, #22c55e, transparent)',
                  animation: `flow ${crash ? 0.9 : 1.4}s linear infinite`,
                  '@keyframes flow': {
                    from: { left: '-48px' },
                    to: { left: '100%' },
                  },
                }}
              />
            )}
          </Box>
          <Node icon={<RouterOutlined />} label="Black box" />
        </Stack>

        <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, display: 'block', mb: 0.75 }}>
          CONTINUOUS CAPTURE · always recording
        </Typography>
        <Stack spacing={0.75}>
          <ContinuousRow live={live} icon={<PhotoCameraOutlined fontSize="small" />} label="Cabin camera" detail="photo every ~10s" />
          <ContinuousRow live={live} icon={<PhotoCameraOutlined fontSize="small" />} label="Road camera" detail="photo every ~10s" />
          <ContinuousRow live={live} icon={<GraphicEqOutlined fontSize="small" />} label="In‑cabin audio" detail="rolling buffer (keeps the seconds before impact)" />
        </Stack>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.75 }}>
          {frames} frames buffered · streaming to the device’s black box.
        </Typography>

        {media && (bb?.images ?? 0) > 0 && (
          <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
            <MediaPhoto src={`${media}/cabin.jpg?v=${bust}`} label="Cabin (latest)" />
            <MediaPhoto src={`${media}/road.jpg?v=${bust}`} label="Road (latest)" />
          </Stack>
        )}

        <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, display: 'block', mt: 2, mb: 0.75 }}>
          ON IMPACT · captured &amp; transferred when a crash is detected
        </Typography>
        <Stack spacing={0.75}>
          {impact.map((i) => (
            <ImpactRow key={i.label} ok={i.ok} icon={i.icon} label={i.label} />
          ))}
        </Stack>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
          {crash ? `${done}/${impact.length} impact artifacts transferred.` : 'Waiting for impact — these load only when a crash is detected.'}
        </Typography>

        {crash && media && (bb?.audioSec ?? 0) > 0 && (
          <Box sx={{ mt: 1.5 }}>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', fontWeight: 700 }}>
              After‑impact audio (real clip from the phone)
            </Typography>
            <audio controls preload="none" src={`${media}/after.m4a?v=${bb?.eventId ?? ''}`} style={{ width: '100%' }} />
          </Box>
        )}

        {bb?.transcript && (
          <Box sx={{ mt: 1.5, p: 1.25, bgcolor: 'action.hover', borderRadius: 1 }}>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', fontWeight: 700 }}>
              In‑cabin transcript
            </Typography>
            <Typography variant="body2" sx={{ fontStyle: 'italic' }}>“{bb.transcript}”</Typography>
          </Box>
        )}
      </CardContent>
    </Card>
  );
}
