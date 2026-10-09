import { useState, type ReactNode } from 'react';
import { Box, Chip, Collapse, IconButton, Link, Stack, Tooltip, Typography } from '@mui/material';
import LocalPoliceOutlined from '@mui/icons-material/LocalPoliceOutlined';
import LocalHospitalOutlined from '@mui/icons-material/LocalHospitalOutlined';
import FamilyRestroomOutlined from '@mui/icons-material/FamilyRestroomOutlined';
import SmsOutlined from '@mui/icons-material/SmsOutlined';
import { formatClock, recipientLabel } from '../format';
import type { Notification, RecipientType } from '../types';

const ICONS: Record<RecipientType, ReactNode> = {
  POLICE: <LocalPoliceOutlined />,
  HOSPITAL: <LocalHospitalOutlined />,
  EMERGENCY_CONTACT: <FamilyRestroomOutlined />,
};

function stage(n: Notification): { label: string; color: string } {
  if (n.openedAt) return { label: `Seen ${formatClock(n.openedAt)}`, color: '#22c55e' };
  return { label: 'Alerted', color: '#94a3b8' };
}

function Row({ n, compact }: { n: Notification; compact?: boolean }) {
  const [showSms, setShowSms] = useState(false);
  const s = stage(n);
  return (
    <Box sx={{ px: 1.5, py: 1.25, borderRadius: 2, bgcolor: 'action.hover' }}>
      <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
        <Box sx={{ color: s.color, display: 'flex' }}>{ICONS[n.recipientType]}</Box>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography sx={{ fontWeight: 700, lineHeight: 1.3 }}>{recipientLabel[n.recipientType]}</Typography>
          <Typography variant="caption" color="text.secondary" noWrap component="div">
            {n.recipientName} · {n.recipientPhone ?? 'no number'}
          </Typography>
        </Box>
        <Chip size="small" label={s.label} sx={{ bgcolor: `${s.color}22`, color: s.color, fontWeight: 700 }} />
        {!compact && (
          <Tooltip title={showSms ? 'Hide message' : 'Show the SMS they received'}>
            <IconButton size="small" onClick={() => setShowSms((v) => !v)}>
              <SmsOutlined fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
      </Stack>
      <Link
        href={`/alert/${n.token}`}
        target="_blank"
        rel="noopener"
        variant="caption"
        underline="hover"
        sx={{ display: 'inline-block', mt: 0.5, ml: 4.5 }}
      >
        Open their view ↗
      </Link>
      <Collapse in={showSms}>
        <Box
          component="pre"
          sx={{ m: 0, mt: 1, p: 1.5, borderRadius: 1, bgcolor: 'background.default', fontSize: 12, whiteSpace: 'pre-wrap' }}
        >
          {n.message}
        </Box>
      </Collapse>
    </Box>
  );
}

/** Responders alerted automatically by the backend, with their delivery and response status. */
export function NotifiedResponders({ notifications, compact }: { notifications: Notification[]; compact?: boolean }) {
  if (notifications.length === 0) {
    return (
      <Typography color="text.secondary" variant="body2">
        Nobody alerted (not an emergency).
      </Typography>
    );
  }
  return (
    <Stack spacing={1}>
      {notifications.map((n) => (
        <Row key={n.token} n={n} compact={compact} />
      ))}
    </Stack>
  );
}
