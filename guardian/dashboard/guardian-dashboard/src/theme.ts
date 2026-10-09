import { alpha, createTheme } from '@mui/material/styles';

// Command-center dark palette. Deep navy base, cyan "online/tech" accent, vivid emergency rose.
const bg = '#06090F';
const paper = '#0E1420';
const raised = '#151D2C';
const line = '#233044';

const primary = '#38BDF8';
const violet = '#A78BFA';
const danger = '#F43F5E';
const amber = '#F59E0B';
const green = '#22C55E';

export const theme = createTheme({
  palette: {
    mode: 'dark',
    primary: { main: primary },
    secondary: { main: violet },
    error: { main: danger },
    warning: { main: amber },
    success: { main: green },
    info: { main: primary },
    background: { default: bg, paper },
    divider: line,
    text: { primary: '#F1F5F9', secondary: '#94A3B8', disabled: '#5B6B82' },
  },
  typography: {
    fontFamily: 'Inter, "Segoe UI", Roboto, system-ui, sans-serif',
    h3: { fontWeight: 900, letterSpacing: -1 },
    h4: { fontWeight: 800, letterSpacing: -0.5 },
    h5: { fontWeight: 800, letterSpacing: -0.3 },
    h6: { fontWeight: 700 },
    overline: { fontWeight: 800, letterSpacing: 1.4, fontSize: 11 },
    button: { textTransform: 'none', fontWeight: 700 },
  },
  shape: { borderRadius: 14 },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        'html, body, #root': { height: '100%', overflow: 'hidden' },
        body: {
          overflowWrap: 'anywhere',
          backgroundColor: bg,
          backgroundImage: `radial-gradient(1200px 600px at 100% -10%, ${alpha(primary, 0.08)}, transparent 60%), radial-gradient(900px 520px at -10% 110%, ${alpha(violet, 0.07)}, transparent 55%)`,
          backgroundAttachment: 'fixed',
        },
        '*': { scrollbarWidth: 'thin', scrollbarColor: `${line} transparent` },
        '*::-webkit-scrollbar': { width: 8, height: 8 },
        '*::-webkit-scrollbar-thumb': { background: line, borderRadius: 8 },
        '*::-webkit-scrollbar-track': { background: 'transparent' },
        '@keyframes guardianPulse': {
          '0%': { boxShadow: `0 0 0 0 ${alpha(green, 0.5)}` },
          '70%': { boxShadow: `0 0 0 7px ${alpha(green, 0)}` },
          '100%': { boxShadow: `0 0 0 0 ${alpha(green, 0)}` },
        },
        '@keyframes guardianAlertPulse': {
          '0%': { boxShadow: `0 0 0 0 ${alpha(danger, 0.5)}` },
          '70%': { boxShadow: `0 0 0 7px ${alpha(danger, 0)}` },
          '100%': { boxShadow: `0 0 0 0 ${alpha(danger, 0)}` },
        },
      },
    },
    MuiCard: {
      defaultProps: { variant: 'outlined' },
      styleOverrides: {
        root: {
          backgroundImage: `linear-gradient(180deg, ${alpha('#FFFFFF', 0.025)}, transparent)`,
          borderColor: line,
          backgroundColor: paper,
          borderRadius: 16,
          transition: 'border-color .2s ease, box-shadow .2s ease, transform .2s ease',
        },
      },
    },
    MuiPaper: { styleOverrides: { root: { backgroundImage: 'none' } } },
    MuiChip: {
      styleOverrides: {
        root: { fontWeight: 700, borderRadius: 8 },
        sizeSmall: { height: 22 },
        outlined: { borderWidth: 1 },
      },
    },
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: { root: { borderRadius: 10, paddingInline: 16 } },
      variants: [
        {
          props: { variant: 'contained', color: 'primary' },
          style: {
            backgroundImage: `linear-gradient(135deg, ${primary}, #0EA5E9)`,
            color: '#001321',
            '&:hover': { backgroundImage: `linear-gradient(135deg, #7DD3FC, ${primary})` },
          },
        },
        {
          props: { variant: 'contained', color: 'error' },
          style: {
            backgroundImage: `linear-gradient(135deg, ${danger}, #E11D48)`,
            '&:hover': { backgroundImage: `linear-gradient(135deg, #FB7185, ${danger})` },
          },
        },
      ],
    },
    MuiTableCell: {
      styleOverrides: {
        root: { borderColor: line },
        head: { color: '#94A3B8', fontWeight: 800, textTransform: 'uppercase', fontSize: 11, letterSpacing: 0.8 },
      },
    },
    MuiTooltip: {
      styleOverrides: { tooltip: { fontSize: 13, background: alpha('#0B1220', 0.96), border: `1px solid ${line}` } },
    },
    MuiLinearProgress: { styleOverrides: { root: { borderRadius: 6, backgroundColor: raised, height: 6 } } },
  },
});

export const surfaces = { bg, paper, line, raised };
export const brandGradient = `linear-gradient(135deg, ${primary}, #818CF8)`;
export const dangerGradient = `linear-gradient(135deg, ${danger}, #E11D48)`;
