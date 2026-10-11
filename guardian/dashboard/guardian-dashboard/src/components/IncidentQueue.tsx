import { Box, Card, Chip, Stack, Typography } from '@mui/material';
import PhoneDisabledOutlined from '@mui/icons-material/PhoneDisabledOutlined';
import { formatClock, formatDuration, severityHex } from '../format';
import { useNow } from '../hooks/useNow';
import type { CrashEvent } from '../types';
import { StatusBadge } from './SeverityBadge';

export function IncidentQueue({
  incidents,
  selectedId,
  onSelect,
}: {
  incidents: CrashEvent[];
  selectedId?: string;
  onSelect: (id: string) => void;
}) {
  const now = useNow();
  return (
    <Stack spacing={1}>
      {incidents.map((c) => {
        const selected = c.eventId === selectedId;
        const waiting = c.status === 'EMERGENCY_ACTIVE';
        return (
          <Card
            key={c.eventId}
            onClick={() => onSelect(c.eventId)}
            sx={{
              cursor: 'pointer',
              borderLeft: 4,
              borderLeftColor: severityHex[c.severity],
              borderColor: selected ? 'primary.main' : 'divider',
              bgcolor: selected ? 'action.selected' : 'background.paper',
              transition: 'all .15s',
              '&:hover': { borderColor: 'primary.main' },
              animation: waiting ? 'pulse 2s infinite' : undefined,
              '@keyframes pulse': {
                '0%,100%': { boxShadow: '0 0 0 0 rgba(239,68,68,0)' },
                '50%': { boxShadow: '0 0 0 4px rgba(239,68,68,0.25)' },
              },
            }}
          >
            <Box sx={{ p: 1.5 }}>
              <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
                <Typography sx={{ fontWeight: 800, color: severityHex[c.severity] }}>
                  {c.severity}
                </Typography>
                <Typography variant="body2" sx={{ fontVariantNumeric: 'tabular-nums', fontWeight: 700 }}>
                  {formatDuration(now - Date.parse(c.timestamp))}
                </Typography>
              </Stack>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.25 }}>
                <Box component="span" sx={{ color: 'text.primary', fontWeight: 700 }}>
                  {c.vehicleId ?? '—'}
                </Box>{' '}
                · {formatClock(c.timestamp)}
              </Typography>
              <Stack direction="row" spacing={1} sx={{ mt: 1, alignItems: 'center', flexWrap: 'wrap', rowGap: 0.5 }}>
                <StatusBadge status={c.status} />
                {!c.phoneConnected && (
                  <Chip
                    size="small"
                    icon={<PhoneDisabledOutlined />}
                    label="No phone"
                    sx={{ bgcolor: 'rgba(82,97,122,.14)', color: 'secondary.main' }}
                  />
                )}
              </Stack>
            </Box>
          </Card>
        );
      })}
    </Stack>
  );
}
