import { Chip } from '@mui/material';
import { severityColor, statusHex, statusLabel } from '../format';
import type { CrashStatus, Severity } from '../types';

export function SeverityBadge({ severity, size = 'small' }: { severity: Severity; size?: 'small' | 'medium' }) {
  return <Chip label={severity} color={severityColor[severity]} size={size} sx={{ fontWeight: 800 }} />;
}

export function StatusBadge({ status }: { status: CrashStatus }) {
  const c = statusHex[status];
  return (
    <Chip
      label={statusLabel[status]}
      size="small"
      sx={{ color: c, borderColor: c, bgcolor: `${c}1a`, border: 1, fontWeight: 700 }}
    />
  );
}

/** Green/red label used for Phone / Device / Cellular / GPS states. */
export function ConnectionChip({ label, ok, value }: { label: string; ok: boolean; value: string }) {
  return (
    <Chip
      size="small"
      color={ok ? 'success' : 'error'}
      variant={ok ? 'outlined' : 'filled'}
      label={`${label}: ${value}`}
      sx={{ fontWeight: 600 }}
    />
  );
}
