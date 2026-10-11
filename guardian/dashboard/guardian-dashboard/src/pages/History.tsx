import { useState } from 'react';
import {
  Box,
  Card,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material';
import PhoneDisabledOutlined from '@mui/icons-material/PhoneDisabledOutlined';
import { useNavigate } from 'react-router-dom';
import { SeverityBadge, StatusBadge } from '../components/SeverityBadge';
import { formatDuration, formatTime } from '../format';
import { useCrashes } from '../services/queries';

type Filter = 'all' | 'active' | 'closed';

export function History() {
  const [filter, setFilter] = useState<Filter>('all');
  const crashes = useCrashes({ active: filter === 'all' ? undefined : filter === 'active', limit: 200 });
  const navigate = useNavigate();

  return (
    <Stack spacing={2}>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        sx={{ justifyContent: 'space-between', alignItems: { sm: 'flex-end' }, gap: 1 }}
      >
        <Box>
          <Typography variant="h4">Incident log</Typography>
          <Typography color="text.secondary">Every crash reported by Guardian devices, with who handled it.</Typography>
        </Box>
        <ToggleButtonGroup size="small" exclusive value={filter} onChange={(_, v) => v && setFilter(v)}>
          <ToggleButton value="all">All</ToggleButton>
          <ToggleButton value="active">Open</ToggleButton>
          <ToggleButton value="closed">Closed</ToggleButton>
        </ToggleButtonGroup>
      </Stack>
      <Card sx={{ overflowX: 'auto' }}>
        <Table sx={{ minWidth: 860 }}>
          <TableHead>
            <TableRow>
              <TableCell>Crash time</TableCell>
              <TableCell>Vehicle</TableCell>
              <TableCell>Severity</TableCell>
              <TableCell>Reported via</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Handled by</TableCell>
              <TableCell>Time to first responder</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {(crashes.data ?? []).map((c) => (
              <TableRow
                key={c.eventId}
                hover
                sx={{ cursor: 'pointer' }}
                onClick={() => navigate(`/incidents/${encodeURIComponent(c.eventId)}`)}
              >
                <TableCell>{formatTime(c.timestamp)}</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>{c.vehicleId ?? '—'}</TableCell>
                <TableCell>
                  <SeverityBadge severity={c.severity} />
                </TableCell>
                <TableCell>
                  {c.phoneConnected ? (
                    'Device LTE · phone linked'
                  ) : (
                    <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', color: 'secondary.main' }}>
                      <PhoneDisabledOutlined fontSize="small" />
                      <span>Device LTE · no phone</span>
                    </Stack>
                  )}
                </TableCell>
                <TableCell>
                  <StatusBadge status={c.status} />
                </TableCell>
                <TableCell>{c.closedBy ?? c.acknowledgedBy ?? '—'}</TableCell>
                <TableCell>
                  {c.acknowledgedAt ? formatDuration(Date.parse(c.acknowledgedAt) - Date.parse(c.createdAt)) : '—'}
                </TableCell>
              </TableRow>
            ))}
            {crashes.data?.length === 0 && (
              <TableRow>
                <TableCell colSpan={8}>
                  <Typography color="text.secondary">No incidents yet.</Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </Card>
    </Stack>
  );
}
