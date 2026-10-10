import type { ReactNode } from 'react';
import { Box, Card, Stack, Typography } from '@mui/material';
import { neu } from '../theme';

const TONES = {
  default: { color: 'primary.main' },
  danger: { color: 'error.main' },
  success: { color: 'success.main' },
  warning: { color: 'warning.main' },
} as const;

export function KpiCard({
  label,
  value,
  hint,
  icon,
  tone = 'default',
}: {
  label: string;
  value: ReactNode;
  hint?: string;
  icon: ReactNode;
  tone?: 'default' | 'danger' | 'success' | 'warning';
}) {
  const { color } = TONES[tone];
  return (
    <Card sx={{ p: 2 }}>
      <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
        <Box
          sx={{
            display: 'flex',
            color,
            flexShrink: 0,
            p: 0.75,
            borderRadius: 2,
            boxShadow: neu.insetSm,
          }}
        >
          {icon}
        </Box>
        <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 700 }}>
          {label}
        </Typography>
      </Stack>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'baseline', mt: 1.5 }}>
        <Typography
          variant="h5"
          sx={{
            color: tone === 'default' ? 'text.primary' : color,
            lineHeight: 1,
            fontSize: 32,
            fontWeight: 900,
            fontVariantNumeric: 'tabular-nums',
          }}
        >
          {value}
        </Typography>
        {hint && (
          <Typography variant="caption" color="text.secondary">
            {hint}
          </Typography>
        )}
      </Stack>
    </Card>
  );
}
