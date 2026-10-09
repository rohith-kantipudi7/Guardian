import { alpha, createTheme } from '@mui/material/styles';

const bg = '#0a0f1a';
const paper = '#111827';
const line = '#1f2a3d';

export const theme = createTheme({
  palette: {
    mode: 'dark',
    primary: { main: '#38bdf8' },
    secondary: { main: '#a78bfa' },
    error: { main: '#ef4444' },
    warning: { main: '#f59e0b' },
    success: { main: '#22c55e' },
    info: { main: '#38bdf8' },
    background: { default: bg, paper },
    divider: line,
    text: { primary: '#e5e7eb', secondary: '#94a3b8' },
  },
  typography: {
    fontFamily: 'Inter, "Segoe UI", Roboto, system-ui, sans-serif',
    h4: { fontWeight: 800, letterSpacing: -0.5 },
    h5: { fontWeight: 800, letterSpacing: -0.3 },
    h6: { fontWeight: 700 },
    overline: { fontWeight: 700, letterSpacing: 1.2 },
    button: { textTransform: 'none', fontWeight: 600 },
  },
  shape: { borderRadius: 12 },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        'html, body, #root': { height: '100%', overflow: 'hidden' },
        body: { overflowWrap: 'anywhere' },
        '*': { scrollbarWidth: 'thin', scrollbarColor: `${line} transparent` },
        '*::-webkit-scrollbar': { width: 8, height: 8 },
        '*::-webkit-scrollbar-thumb': { background: line, borderRadius: 8 },
        '*::-webkit-scrollbar-track': { background: 'transparent' },
      },
    },
    MuiCard: {
      defaultProps: { variant: 'outlined' },
      styleOverrides: { root: { backgroundImage: 'none', borderColor: line, background: paper } },
    },
    MuiPaper: { styleOverrides: { root: { backgroundImage: 'none' } } },
    MuiChip: { styleOverrides: { root: { fontWeight: 600 } } },
    MuiButton: { defaultProps: { disableElevation: true } },
    MuiTableCell: { styleOverrides: { root: { borderColor: line }, head: { color: '#94a3b8', fontWeight: 700 } } },
    MuiTooltip: { styleOverrides: { tooltip: { fontSize: 13, background: alpha('#0b1220', 0.95) } } },
  },
});

export const surfaces = { bg, paper, line, raised: '#162033' };
