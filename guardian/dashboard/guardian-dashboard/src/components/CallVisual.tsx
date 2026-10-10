import { Box, Card, CardContent, Chip, Stack, Typography } from '@mui/material';
import SmartToyOutlined from '@mui/icons-material/SmartToyOutlined';
import PhoneInTalkOutlined from '@mui/icons-material/PhoneInTalkOutlined';
import LocalPoliceOutlined from '@mui/icons-material/LocalPoliceOutlined';
import LocalHospitalOutlined from '@mui/icons-material/LocalHospitalOutlined';
import FamilyRestroomOutlined from '@mui/icons-material/FamilyRestroomOutlined';
import type { ReactNode } from 'react';
import type { BlackBox } from '../services/device';
import { useStep } from '../hooks/useStep';

function CallTarget({ icon, label, number }: Readonly<{ icon: ReactNode; label: string; number: string }>) {
  return (
    <Stack direction="row" spacing={1.25} sx={{ alignItems: 'center', p: 1, borderRadius: 1, bgcolor: 'action.hover' }}>
      <Box sx={{ color: 'error.main', display: 'flex' }}>{icon}</Box>
      <Box sx={{ flex: 1 }}>
        <Typography variant="body2" sx={{ fontWeight: 700 }}>{label}</Typography>
        <Typography variant="caption" color="text.secondary">{number}</Typography>
      </Box>
      <PhoneInTalkOutlined fontSize="small" sx={{ color: 'error.main' }} />
      <Box
        sx={{
          width: 9,
          height: 9,
          borderRadius: '50%',
          bgcolor: 'error.main',
          animation: 'ring 0.9s ease-in-out infinite',
          '@keyframes ring': {
            '0%,100%': { opacity: 1, transform: 'scale(1)' },
            '50%': { opacity: 0.3, transform: 'scale(0.6)' },
          },
        }}
      />
    </Stack>
  );
}

/** Guardian AI emergency call — simulated as soon as the dashboard receives a collision message. */
export function CallVisual({ bb, collision }: Readonly<{ bb?: BlackBox; collision?: boolean }>) {
  const record = !!bb && (bb.severity ?? '').toUpperCase() !== 'MONITORING' && bb.eventId !== 'LIVE';
  const crash = record || !!collision;
  const step = useStep(crash, 3, 1800);
  if (!crash) return null; // no call unless a collision has been received

  const sev = record ? (bb?.severity ?? '').toUpperCase() : '';
  const loc = bb?.location && bb.location !== '' ? bb.location : null;
  const script =
    `“${sev ? `${sev} collision` : 'A collision'} has been detected${loc ? ` near ${loc}` : ''}. ` +
    'An occupant may be injured and unresponsive. Requesting immediate dispatch to the location.”';

  return (
    <Card sx={{ borderColor: 'error.main', borderWidth: 1, borderStyle: 'solid' }}>
      <CardContent>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            <SmartToyOutlined sx={{ color: 'error.main' }} />
            <Typography variant="overline" color="error.main" sx={{ fontWeight: 700 }}>
              Guardian AI · placing emergency call
            </Typography>
          </Stack>
          <Chip size="small" color="error" label="Live" />
        </Stack>

        <Box sx={{ mt: 1.5, p: 1.5, borderRadius: 1, bgcolor: 'action.selected' }}>
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, display: 'block' }}>
            AI voice to dispatcher
          </Typography>
          <Typography variant="body2" sx={{ fontStyle: 'italic', mt: 0.25 }}>{script}</Typography>
          <Stack direction="row" spacing={0.5} sx={{ alignItems: 'flex-end', height: 18, mt: 1 }}>
            {[0, 1, 2, 3, 4, 5, 6, 7].map((i) => (
              <Box
                key={i}
                sx={{
                  width: 3,
                  bgcolor: 'error.main',
                  borderRadius: 1,
                  animation: `wave 0.9s ease-in-out ${i * 0.08}s infinite`,
                  '@keyframes wave': {
                    '0%,100%': { height: 4 },
                    '50%': { height: 18 },
                  },
                }}
              />
            ))}
          </Stack>
        </Box>

        <Stack spacing={0.75} sx={{ mt: 1.5 }}>
          <CallTarget icon={<LocalPoliceOutlined fontSize="small" />} label="Police" number="112" />
          {step >= 2 && <CallTarget icon={<LocalHospitalOutlined fontSize="small" />} label="Ambulance" number="108" />}
          {step >= 3 && <CallTarget icon={<FamilyRestroomOutlined fontSize="small" />} label="Family" number="Emergency contact" />}
        </Stack>

        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1.5 }}>
          The AI reads the incident to each line. On a SIM‑equipped phone this auto‑dials (ACTION_CALL);
          without a SIM it is shown here as the call that would be placed.
        </Typography>
      </CardContent>
    </Card>
  );
}
