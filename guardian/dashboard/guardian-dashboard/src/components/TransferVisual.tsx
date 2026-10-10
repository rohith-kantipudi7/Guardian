import { Box, Card, CardContent, Stack, Typography } from '@mui/material';
import SmartphoneOutlined from '@mui/icons-material/SmartphoneOutlined';
import RouterOutlined from '@mui/icons-material/RouterOutlined';
import CheckCircle from '@mui/icons-material/CheckCircle';
import RadioButtonUnchecked from '@mui/icons-material/RadioButtonUnchecked';
import PhotoCameraOutlined from '@mui/icons-material/PhotoCameraOutlined';
import GraphicEqOutlined from '@mui/icons-material/GraphicEqOutlined';
import PsychologyOutlined from '@mui/icons-material/PsychologyOutlined';
import LocationOnOutlined from '@mui/icons-material/LocationOnOutlined';
import type { ReactNode } from 'react';
import type { BlackBox } from '../services/device';

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

/** Visualises the real captured media flowing phone → black box, item by item. */
export function TransferVisual({ bb, live }: Readonly<{ bb?: BlackBox; live: boolean }>) {
  const items = [
    { ok: (bb?.images ?? 0) >= 1, icon: <PhotoCameraOutlined fontSize="small" />, label: 'Cabin photo' },
    { ok: (bb?.images ?? 0) >= 2, icon: <PhotoCameraOutlined fontSize="small" />, label: 'Road photo' },
    { ok: (bb?.preAudioSec ?? 0) > 0, icon: <GraphicEqOutlined fontSize="small" />, label: `Before‑impact audio (${bb?.preAudioSec ?? 0}s)` },
    { ok: (bb?.audioSec ?? 0) > 0, icon: <GraphicEqOutlined fontSize="small" />, label: `After‑impact audio (${bb?.audioSec ?? 0}s)` },
    { ok: !!bb?.assessment, icon: <PsychologyOutlined fontSize="small" />, label: 'AI assessment' },
    { ok: !!bb?.location, icon: <LocationOnOutlined fontSize="small" />, label: 'GPS location' },
  ];
  const done = items.filter((i) => i.ok).length;

  return (
    <Card>
      <CardContent>
        <Typography variant="overline" color="text.secondary">
          Live transfer · phone → black box
        </Typography>

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
                  background: 'linear-gradient(90deg, transparent, #22c55e, transparent)',
                  animation: 'flow 1.4s linear infinite',
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

        <Stack spacing={0.75}>
          {items.map((i) => (
            <Stack key={i.label} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <Box sx={{ color: i.ok ? 'success.main' : 'text.disabled', display: 'flex' }}>{i.icon}</Box>
              <Typography variant="body2" sx={{ flex: 1, color: i.ok ? 'text.primary' : 'text.secondary' }}>
                {i.label}
              </Typography>
              {i.ok ? (
                <CheckCircle fontSize="small" sx={{ color: 'success.main' }} />
              ) : (
                <RadioButtonUnchecked fontSize="small" sx={{ color: 'text.disabled' }} />
              )}
            </Stack>
          ))}
        </Stack>

        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1.5 }}>
          {done}/{items.length} artifacts transferred to the black box. Media is captured on the phone and streamed to
          the device; the device holds the record + metadata.
        </Typography>
      </CardContent>
    </Card>
  );
}
