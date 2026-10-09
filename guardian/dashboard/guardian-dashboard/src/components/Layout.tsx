import {
  Alert,
  Avatar,
  Box,
  Button,
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
import DashboardOutlined from '@mui/icons-material/DashboardOutlined';
import SlideshowOutlined from '@mui/icons-material/SlideshowOutlined';
import ListAltOutlined from '@mui/icons-material/ListAltOutlined';
import RouterOutlined from '@mui/icons-material/RouterOutlined';
import DirectionsCarOutlined from '@mui/icons-material/DirectionsCarOutlined';
import InfoOutlined from '@mui/icons-material/InfoOutlined';
import ShieldOutlined from '@mui/icons-material/ShieldOutlined';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { formatPercent } from '../format';
import { useRealtime } from '../services/realtime';
import { surfaces } from '../theme';

const NAV = [
  { to: '/', label: 'Live monitor', icon: <DashboardOutlined />, end: true },
  { to: '/showcase', label: 'Showcase', icon: <SlideshowOutlined /> },
  { to: '/incidents', label: 'Incident log', icon: <ListAltOutlined /> },
  { to: '/devices', label: 'Devices', icon: <RouterOutlined /> },
  { to: '/vehicles', label: 'Vehicles', icon: <DirectionsCarOutlined /> },
  { to: '/about', label: 'How it works', icon: <InfoOutlined /> },
];

const SIDEBAR = 248;
const RAIL = 72;

export function Layout() {
  const { connected, alert, dismissAlert } = useRealtime();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const compact = useMediaQuery('(max-width:1199px)');

  return (
    <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden', bgcolor: 'background.default' }}>
      <Box
        component="nav"
        sx={{
          width: compact ? RAIL : SIDEBAR,
          flexShrink: 0,
          borderRight: 1,
          borderColor: 'divider',
          bgcolor: surfaces.paper,
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
          <Avatar sx={{ bgcolor: 'error.main', width: 36, height: 36 }}>
            <ShieldOutlined fontSize="small" />
          </Avatar>
          {!compact && (
            <Box>
              <Typography sx={{ fontWeight: 900, letterSpacing: 2, lineHeight: 1.1 }}>GUARDIAN</Typography>
              <Typography variant="caption" color="text.secondary">
                Crash response monitor
              </Typography>
            </Box>
          )}
        </Stack>

        <List sx={{ px: compact ? 1 : 1.5, flex: 1 }}>
          {NAV.map((n) => (
            <Tooltip key={n.to} title={compact ? n.label : ''} placement="right">
              <ListItemButton
                component={NavLink}
                to={n.to}
                end={n.end}
                aria-label={n.label}
                sx={{
                  borderRadius: 2,
                  mb: 0.5,
                  justifyContent: compact ? 'center' : 'flex-start',
                  color: 'text.secondary',
                  '&.active': { bgcolor: surfaces.raised, color: 'primary.main' },
                  '&.active .MuiListItemIcon-root': { color: 'primary.main' },
                }}
              >
                <ListItemIcon sx={{ minWidth: compact ? 0 : 38, color: 'inherit' }}>{n.icon}</ListItemIcon>
                {!compact && <ListItemText primary={n.label} />}
              </ListItemButton>
            </Tooltip>
          ))}
        </List>

        <Box sx={{ p: compact ? 1 : 2, borderTop: 1, borderColor: 'divider' }}>
          <Tooltip title={compact ? (connected ? 'Live feed connected' : 'Reconnecting…') : ''} placement="right">
            <Stack
              direction="row"
              spacing={1}
              sx={{ alignItems: 'center', justifyContent: compact ? 'center' : 'flex-start' }}
            >
              <Box
                sx={{
                  width: 10,
                  height: 10,
                  borderRadius: '50%',
                  bgcolor: connected ? 'success.main' : 'warning.main',
                  boxShadow: connected ? '0 0 8px #22c55e' : 'none',
                }}
              />
              {!compact && (
                <Typography variant="body2" color="text.secondary">
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
          sx={{ fontSize: 16, alignItems: 'center' }}
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
