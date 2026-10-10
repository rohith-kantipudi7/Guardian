import type { ReactNode } from 'react';
import { Box, Card, Stack, Typography } from '@mui/material';
import CarCrashOutlined from '@mui/icons-material/CarCrashOutlined';
import { neu } from '../theme';

/** The primary alert surface: full width, large, shown only when the device reports a real collision. */
export function CollisionBanner({ detail, eventId }: Readonly<{ detail?: ReactNode; eventId?: string }>) {
  return (
    <Card sx={{ borderLeft: 10, borderColor: 'error.main', width: '100%' }} role="alert">
      <Stack
        direction={{ xs: 'column', md: 'row' }}
        spacing={3}
        sx={{ alignItems: 'center', px: { xs: 3, md: 5 }, py: { xs: 3, md: 4.5 } }}
      >
        <Box
          sx={{
            width: 88,
            height: 88,
            borderRadius: 4,
            display: 'grid',
            placeItems: 'center',
            flexShrink: 0,
            color: 'error.main',
            boxShadow: neu.inset,
            animation: 'guardianAlertPulse 2s infinite',
          }}
        >
          <CarCrashOutlined sx={{ fontSize: 52 }} />
        </Box>
        <Box sx={{ flex: 1, minWidth: 0, textAlign: { xs: 'center', md: 'left' } }}>
          <Typography
            component="h2"
            sx={{ color: 'error.main', fontWeight: 900, fontSize: { xs: 34, md: 56 }, lineHeight: 1.05, letterSpacing: -1 }}
          >
            COLLISION DETECTED
          </Typography>
          <Typography color="text.secondary" sx={{ mt: 1, fontSize: 17 }}>
            {detail}
            {eventId ? ` · ${eventId}` : ''}
          </Typography>
        </Box>
      </Stack>
    </Card>
  );
}
