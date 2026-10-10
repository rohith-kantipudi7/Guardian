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
import DashboardOutlined from '@mui/icons-material/DashboardOutlined';
import ListAltOutlined from '@mui/icons-material/ListAltOutlined';
import RouterOutlined from '@mui/icons-material/RouterOutlined';
import DirectionsCarOutlined from '@mui/icons-material/DirectionsCarOutlined';
import InfoOutlined from '@mui/icons-material/InfoOutlined';
import ShieldOutlined from '@mui/icons-material/ShieldOutlined';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { formatPercent } from '../format';
import { useRealtime } from '../services/realtime';
import { brandGradient, surfaces } from '../theme';

const NAV = [
  { to: '/', label: 'Live device', icon: <RouterOutlined />, end: true },
  { to: '/ops', label: 'Responder ops', icon: <DashboardOutlined /> },
  { to: '/incidents', label: 'Incident log', icon: <ListAltOutlined /> },
  { to: '/devices', label: 'Devices', icon: <RouterOutlined /> },
  { to: '/vehicles', label: 'Vehicles', icon: <DirectionsCarOutlined /> },
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
          borderRight: 1,
          borderColor: 'divider',
          backgroundImage: `linear-gradient(180deg, ${surfaces.raised}, ${surfaces.paper} 42%)`,
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
            sx={{
              backgroundImage: brandGradient,
              width: 40,
              height: 40,
              boxShadow: '0 6px 20px rgba(56,189,248,.35)',
            }}
          >
            <ShieldOutlined fontSize="small" sx={{ color: '#06121f' }} />
          </Avatar>
          {!compact && (
            <Box sx={{ minWidth: 0 }}>
              <Typography
                sx={{
                  fontWeight: 900,
                  letterSpacing: 3,
                  lineHeight: 1.1,
                  backgroundImage: brandGradient,
                  backgroundClip: 'text',
                  WebkitBackgroundClip: 'text',
                  color: 'transparent',
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
                bgcolor: connected ? 'rgba(34,197,94,.14)' : 'rgba(245,158,11,.14)',
                border: 1,
                borderColor: connected ? 'rgba(34,197,94,.4)' : 'rgba(245,158,11,.4)',
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
                  '&:hover': { bgcolor: 'rgba(148,163,184,.08)', color: 'text.primary' },
                  '&.active': {
                    bgcolor: 'rgba(56,189,248,.12)',
                    color: 'primary.main',
                    '&::before': {
                      content: '""',
                      position: 'absolute',
                      left: 4,
                      top: 10,
                      bottom: 10,
                      width: 3,
                      borderRadius: 3,
                      backgroundImage: brandGradient,
                    },
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
