import type { ReactNode } from 'react';
import { Box, Card, Stack, Typography } from '@mui/material';

const TONES = {
  default: { color: 'primary.main', glow: 'rgba(56,189,248,.16)' },
  danger: { color: 'error.main', glow: 'rgba(244,63,94,.18)' },
  success: { color: 'success.main', glow: 'rgba(34,197,94,.16)' },
  warning: { color: 'warning.main', glow: 'rgba(245,158,11,.16)' },
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
  const { color, glow } = TONES[tone];
  return (
    <Card
      sx={{
        p: 2,
        position: 'relative',
        overflow: 'hidden',
        '&:hover': { borderColor: color, transform: 'translateY(-2px)' },
        '&::after': {
          content: '""',
          position: 'absolute',
          inset: 0,
          borderRadius: 'inherit',
          background: `radial-gradient(120px 60px at 100% 0%, ${glow}, transparent 70%)`,
          pointerEvents: 'none',
        },
      }}
    >
      <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
        <Box
          sx={{
            display: 'flex',
            color,
            flexShrink: 0,
            p: 0.75,
            borderRadius: 2,
            bgcolor: glow,
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
