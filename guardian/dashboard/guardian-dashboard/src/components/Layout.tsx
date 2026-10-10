import {
  Alert,
  Avatar,
  Box,
  Button,
  Chip,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Snackbar,
  Stack,
  Tooltip,
  Typography,
  useMediaQuery,
} from '@mui/material';
import RouterOutlined from '@mui/icons-material/RouterOutlined';
import InfoOutlined from '@mui/icons-material/InfoOutlined';
import ShieldOutlined from '@mui/icons-material/ShieldOutlined';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { formatPercent } from '../format';
import { useRealtime } from '../services/realtime';
import { neu, surfaces } from '../theme';

const NAV = [
  { to: '/', label: 'Live network', icon: <RouterOutlined />, end: true },
  { to: '/ops', label: 'Responder ops', icon: <ShieldOutlined /> },
  { to: '/about', label: 'How it works', icon: <InfoOutlined /> },
];

const SIDEBAR = 256;
const RAIL = 76;

export function Layout() {
  const { connected, alert, dismissAlert } = useRealtime();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const compact = useMediaQuery('(max-width:1199px)');

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <Box
        component="nav"
        sx={{
          width: compact ? RAIL : SIDEBAR,
          flexShrink: 0,
          bgcolor: surfaces.paper,
          boxShadow: neu.raised,
          zIndex: 1,
          position: 'sticky',
          top: 0,
          height: '100vh',
          overflowY: 'auto',
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        <Stack
          direction="row"
          spacing={1.5}
          sx={{ alignItems: 'center', justifyContent: compact ? 'center' : 'flex-start', px: compact ? 1 : 2.5, py: 2.5 }}
        >
          <Avatar
            variant="rounded"
            sx={{ bgcolor: surfaces.paper, width: 40, height: 40, borderRadius: 2, boxShadow: neu.raisedSm }}
          >
            <ShieldOutlined fontSize="small" sx={{ color: 'primary.main' }} />
          </Avatar>
          {!compact && (
            <Box sx={{ minWidth: 0 }}>
              <Typography
                sx={{
                  fontWeight: 900,
                  letterSpacing: 3,
                  lineHeight: 1.1,
                  color: 'text.primary',
                }}
              >
                GUARDIAN
              </Typography>
              <Typography variant="caption" color="text.secondary" sx={{ letterSpacing: 0.4 }}>
                Emergency response center
              </Typography>
            </Box>
          )}
        </Stack>

        {!compact && (
          <Box sx={{ px: 2.5, pb: 1.5 }}>
            <Chip
              size="small"
              label={connected ? 'LIVE' : 'OFFLINE'}
              sx={{
                fontWeight: 800,
                letterSpacing: 1,
                color: connected ? 'success.main' : 'warning.main',
                bgcolor: 'transparent',
              }}
            />
          </Box>
        )}

        <List sx={{ px: compact ? 1 : 1.5, flex: 1 }}>
          {NAV.map((n) => (
            <Tooltip key={n.to} title={compact ? n.label : ''} placement="right">
              <ListItemButton
                component={NavLink}
                to={n.to}
                end={n.end}
                aria-label={n.label}
                sx={{
                  position: 'relative',
                  borderRadius: 2.5,
                  mb: 0.5,
                  py: 1.1,
                  justifyContent: compact ? 'center' : 'flex-start',
                  color: 'text.secondary',
                  transition: 'background-color .18s ease, color .18s ease',
                  '&:hover': { bgcolor: 'transparent', boxShadow: neu.raisedSm, color: 'text.primary' },
                  '&.active': {
                    bgcolor: 'transparent',
                    boxShadow: neu.insetSm,
                    color: 'primary.main',
                  },
                  '&.active .MuiListItemIcon-root': { color: 'primary.main' },
                }}
              >
                <ListItemIcon sx={{ minWidth: compact ? 0 : 38, color: 'inherit' }}>{n.icon}</ListItemIcon>
                {!compact && <ListItemText primary={n.label} slotProps={{ primary: { sx: { fontWeight: 600 } } }} />}
              </ListItemButton>
            </Tooltip>
          ))}
        </List>

        <Box sx={{ p: compact ? 1 : 2, borderTop: 1, borderColor: 'divider' }}>
          <Tooltip title={compact ? (connected ? 'Live feed connected' : 'Reconnecting…') : ''} placement="right">
            <Stack
              direction="row"
              spacing={1.2}
              sx={{ alignItems: 'center', justifyContent: compact ? 'center' : 'flex-start' }}
            >
              <Box
                sx={{
                  width: 10,
                  height: 10,
                  borderRadius: '50%',
                  bgcolor: connected ? 'success.main' : 'warning.main',
                  animation: connected ? 'guardianPulse 2s infinite' : 'none',
                }}
              />
              {!compact && (
                <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 600 }}>
                  {connected ? 'Live feed connected' : 'Reconnecting…'}
                </Typography>
              )}
            </Stack>
          </Tooltip>
          {!compact && (
            <Typography variant="caption" color="text.disabled" sx={{ display: 'block', mt: 1 }}>
              Demo monitor · read-only
            </Typography>
          )}
          <Stack direction={compact ? 'column' : 'row'} spacing={1.5} sx={{ mt: 1, alignItems: compact ? 'center' : 'flex-start' }}>
            <Typography component={NavLink} to="/privacy" variant="caption" color="text.secondary" sx={{ textDecoration: 'none', color: 'text.secondary', '&:hover': { color: 'primary.main' } }}>
              Privacy
            </Typography>
            <Typography component={NavLink} to="/terms" variant="caption" color="text.secondary" sx={{ textDecoration: 'none', color: 'text.secondary', '&:hover': { color: 'primary.main' } }}>
              Terms
            </Typography>
          </Stack>
        </Box>
      </Box>

      <Box component="main" sx={{ flex: 1, minWidth: 0, overflowY: 'auto', overflowX: 'hidden', p: { xs: 2, md: 3 } }}>
        <Outlet />
      </Box>

      <Snackbar
        open={!!alert && pathname !== '/showcase'}
        autoHideDuration={10000}
        onClose={(_, reason) => reason !== 'clickaway' && dismissAlert()}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      >
        <Alert
          severity="error"
          variant="filled"
          onClose={dismissAlert}
          action={
            alert && (
              <Button
                color="inherit"
                size="small"
                variant="outlined"
                onClick={() => {
                  navigate(`/?incident=${encodeURIComponent(alert.eventId)}`);
                  dismissAlert();
                }}
              >
                Open incident
              </Button>
            )
          }
          sx={{ fontSize: 16, alignItems: 'center', borderRadius: 2.5 }}
        >
          {alert && (
            <>
              <b>NEW {alert.severity} CRASH</b> · {alert.vehicleId ?? alert.deviceId} · {formatPercent(alert.confidence)} ·
              phone {alert.phoneConnected ? 'connected' : 'NOT connected'}
            </>
          )}
        </Alert>
      </Snackbar>
    </Box>
  );
}
