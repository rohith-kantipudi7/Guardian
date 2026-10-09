import type { ReactNode } from 'react';
import { Box, Button, Stack, Typography } from '@mui/material';
import ShieldOutlined from '@mui/icons-material/ShieldOutlined';
import BluetoothConnectedOutlined from '@mui/icons-material/BluetoothConnectedOutlined';
import PhonelinkEraseOutlined from '@mui/icons-material/PhonelinkEraseOutlined';
import { formatPercent } from '../format';
import type { CrashEvent } from '../types';

function Screen({ children, bg }: { children: ReactNode; bg: string }) {
  return (
    <Box
      sx={{
        width: 250,
        height: 500,
        mx: 'auto',
        borderRadius: '36px',
        border: '10px solid #1e293b',
        boxShadow: '0 20px 50px rgba(0,0,0,.5), inset 0 0 0 1px #334155',
        bgcolor: bg,
        overflow: 'hidden',
        position: 'relative',
        display: 'flex',
        flexDirection: 'column',
        transition: 'background-color .3s',
      }}
    >
      <Box sx={{ width: 90, height: 20, bgcolor: '#1e293b', borderRadius: '0 0 12px 12px', mx: 'auto' }} />
      {children}
    </Box>
  );
}

/**
 * What the driver's Guardian app shows. Mirrors the real Android app screens; the BLE link is
 * simulated by the device's phone-connected state.
 */
export function PhoneMockup({
  phoneConnected,
  alert,
  battery,
  onImSafe,
  busy,
}: {
  phoneConnected: boolean;
  alert: CrashEvent | null;
  battery?: number;
  onImSafe: () => void;
  busy?: boolean;
}) {
  if (!phoneConnected) {
    return (
      <Screen bg="#020617">
        <Stack sx={{ flex: 1, alignItems: 'center', justifyContent: 'center', p: 3, textAlign: 'center' }} spacing={2}>
          <PhonelinkEraseOutlined sx={{ fontSize: 56, color: '#475569' }} />
          <Typography sx={{ color: '#64748b', fontWeight: 700 }}>Phone destroyed / switched off</Typography>
          <Typography variant="body2" sx={{ color: '#475569' }}>
            No Bluetooth link. The vehicle device still reports crashes on its own.
          </Typography>
        </Stack>
      </Screen>
    );
  }

  if (alert) {
    return (
      <Screen bg="#450a0a">
        <Stack sx={{ flex: 1, p: 2.5, textAlign: 'center' }} spacing={1.5}>
          <Typography sx={{ color: '#fca5a5', fontWeight: 900, fontSize: 24, mt: 2 }}>CRASH DETECTED</Typography>
          <Box>
            <Typography variant="caption" sx={{ color: '#fecaca' }}>
              Severity
            </Typography>
            <Typography sx={{ color: '#fff', fontWeight: 800, fontSize: 22 }}>{alert.severity}</Typography>
          </Box>
          <Box>
            <Typography variant="caption" sx={{ color: '#fecaca' }}>
              Confidence
            </Typography>
            <Typography sx={{ color: '#fff', fontWeight: 800, fontSize: 20 }}>{formatPercent(alert.confidence)}</Typography>
          </Box>
          <Box>
            <Typography variant="caption" sx={{ color: '#fecaca' }}>
              Emergency response
            </Typography>
            <Typography sx={{ color: '#fff', fontWeight: 700 }}>
              {alert.status === 'ACKNOWLEDGED'
                ? `Alert seen by ${alert.acknowledgedBy ?? 'responders'}`
                : 'Police, ambulance & family alerted'}
            </Typography>
          </Box>
          <Box sx={{ flex: 1 }} />
          <Button
            variant="contained"
            size="large"
            disabled={busy}
            onClick={onImSafe}
            sx={{ bgcolor: '#16a34a', color: '#fff', fontWeight: 900, fontSize: 20, py: 1.5, '&:hover': { bgcolor: '#15803d' } }}
          >
            I'M SAFE
          </Button>
          <Typography variant="caption" sx={{ color: '#fca5a5' }}>
            Tapping cancels the incident at the response center
          </Typography>
        </Stack>
      </Screen>
    );
  }

  return (
    <Screen bg="#0b1220">
      <Stack sx={{ flex: 1, p: 2.5 }} spacing={2}>
        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography sx={{ color: '#e2e8f0', fontWeight: 900, letterSpacing: 2 }}>GUARDIAN</Typography>
          <BluetoothConnectedOutlined sx={{ color: '#38bdf8' }} fontSize="small" />
        </Stack>
        <Box sx={{ textAlign: 'center', py: 2 }}>
          <Box
            sx={{
              width: 120,
              height: 120,
              mx: 'auto',
              borderRadius: '50%',
              border: '6px solid #22c55e',
              display: 'grid',
              placeItems: 'center',
              boxShadow: '0 0 30px rgba(34,197,94,.35)',
            }}
          >
            <ShieldOutlined sx={{ fontSize: 52, color: '#22c55e' }} />
          </Box>
          <Typography sx={{ color: '#22c55e', fontWeight: 900, mt: 1.5 }}>PROTECTION ACTIVE</Typography>
        </Box>
        {[
          ['Device', 'GD-001 · Online'],
          ['Bluetooth', 'Connected'],
          ['GPS', 'Available'],
          ['Battery', `${battery ?? 92}%`],
        ].map(([k, v]) => (
          <Stack key={k} direction="row" sx={{ justifyContent: 'space-between' }}>
            <Typography variant="body2" sx={{ color: '#94a3b8' }}>
              {k}
            </Typography>
            <Typography variant="body2" sx={{ color: '#e2e8f0', fontWeight: 700 }}>
              {v}
            </Typography>
          </Stack>
        ))}
      </Stack>
    </Screen>
  );
}
