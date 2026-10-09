import type { ReactNode } from 'react';
import { Box, Card, Stack, Typography } from '@mui/material';

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
  const color = { default: 'primary.main', danger: 'error.main', success: 'success.main', warning: 'warning.main' }[tone];
  return (
    <Card sx={{ p: 2 }}>
      <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
        <Box sx={{ display: 'flex', color, flexShrink: 0 }}>{icon}</Box>
        <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 600 }}>
          {label}
        </Typography>
      </Stack>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'baseline', mt: 1 }}>
        <Typography variant="h5" sx={{ color: tone === 'default' ? 'text.primary' : color, lineHeight: 1, fontSize: 28 }}>
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
