import { alpha, createTheme } from '@mui/material/styles';

// Neumorphic light theme: one soft base colour, depth comes from paired light/dark shadows.
const bg = '#E4E9F0';
const paper = '#E4E9F0';
const raised = '#E4E9F0';
const line = '#CBD3DF';
const shadowDark = '#B8C1D0';
const shadowLight = '#FFFFFF';

const primary = '#2563EB';
const slate = '#52617A';
const danger = '#D92D4A';
const amber = '#B7791F';
const green = '#1E8E4E';

export const neu = {
  raised: `6px 6px 14px ${shadowDark}, -6px -6px 14px ${shadowLight}`,
  raisedSm: `3px 3px 8px ${shadowDark}, -3px -3px 8px ${shadowLight}`,
  inset: `inset 3px 3px 7px ${shadowDark}, inset -3px -3px 7px ${shadowLight}`,
  insetSm: `inset 2px 2px 4px ${shadowDark}, inset -2px -2px 4px ${shadowLight}`,
};

export const theme = createTheme({
  palette: {
    mode: 'light',
    primary: { main: primary },
    secondary: { main: slate },
    error: { main: danger },
    warning: { main: amber },
    success: { main: green },
    info: { main: primary },
    background: { default: bg, paper },
    divider: line,
    text: { primary: '#1F2937', secondary: '#4B5870', disabled: '#8793A8' },
  },
  typography: {
    fontFamily: 'Inter, "Segoe UI", Roboto, system-ui, sans-serif',
    h3: { fontWeight: 800, letterSpacing: -1 },
    h4: { fontWeight: 800, letterSpacing: -0.5 },
    h5: { fontWeight: 800, letterSpacing: -0.3 },
    h6: { fontWeight: 700 },
    overline: { fontWeight: 700, letterSpacing: 1.2, fontSize: 11 },
    button: { textTransform: 'none', fontWeight: 700 },
  },
  shape: { borderRadius: 14 },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        'html, body, #root': { height: '100%', overflow: 'hidden' },
        body: { overflowWrap: 'anywhere', backgroundColor: bg },
        '*': { scrollbarWidth: 'thin', scrollbarColor: `${shadowDark} transparent` },
        '*::-webkit-scrollbar': { width: 8, height: 8 },
        '*::-webkit-scrollbar-thumb': { background: shadowDark, borderRadius: 4 },
        '*::-webkit-scrollbar-track': { background: 'transparent' },
        '@keyframes guardianPulse': {
          '0%': { boxShadow: `0 0 0 0 ${alpha(green, 0.45)}` },
          '70%': { boxShadow: `0 0 0 7px ${alpha(green, 0)}` },
          '100%': { boxShadow: `0 0 0 0 ${alpha(green, 0)}` },
        },
        '@keyframes guardianAlertPulse': {
          '0%': { boxShadow: `0 0 0 0 ${alpha(danger, 0.45)}` },
          '70%': { boxShadow: `0 0 0 7px ${alpha(danger, 0)}` },
          '100%': { boxShadow: `0 0 0 0 ${alpha(danger, 0)}` },
        },
      },
    },
    MuiCard: {
      defaultProps: { variant: 'elevation', elevation: 0 },
      styleOverrides: {
        root: {
          backgroundColor: paper,
          backgroundImage: 'none',
          border: 'none',
          borderRadius: 16,
          boxShadow: neu.raised,
        },
      },
    },
    MuiPaper: {
      styleOverrides: { root: { backgroundImage: 'none', backgroundColor: paper } },
    },
    MuiChip: {
      styleOverrides: {
        root: { fontWeight: 700, borderRadius: 8, boxShadow: neu.insetSm },
        sizeSmall: { height: 22 },
        outlined: { borderWidth: 0 },
      },
    },
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: {
          borderRadius: 10,
          paddingInline: 16,
          backgroundColor: bg,
          color: '#1F2937',
          boxShadow: neu.raisedSm,
          border: 'none',
          '&:hover': { backgroundColor: bg, boxShadow: neu.raised },
          '&:active': { boxShadow: neu.insetSm },
        },
      },
      variants: [
        { props: { variant: 'contained', color: 'primary' }, style: { color: primary } },
        { props: { variant: 'contained', color: 'error' }, style: { color: danger } },
        { props: { variant: 'contained', color: 'success' }, style: { color: green } },
      ],
    },
    MuiTableCell: {
      styleOverrides: {
        root: { borderColor: line },
        head: { color: '#4B5870', fontWeight: 800, textTransform: 'uppercase', fontSize: 11, letterSpacing: 0.8 },
      },
    },
    MuiAlert: { styleOverrides: { root: { borderRadius: 12 } } },
    MuiOutlinedInput: {
      styleOverrides: {
        root: { borderRadius: 12, boxShadow: neu.inset, '& fieldset': { border: 'none' } },
      },
    },
    MuiToggleButton: {
      styleOverrides: {
        root: { border: 'none', borderRadius: 10, boxShadow: neu.raisedSm, '&.Mui-selected': { boxShadow: neu.insetSm, backgroundColor: bg } },
      },
    },
    MuiTableRow: { styleOverrides: { root: { '&.MuiTableRow-hover:hover': { backgroundColor: alpha(shadowDark, 0.25) } } } },
    MuiDivider: { styleOverrides: { root: { borderColor: alpha(shadowDark, 0.6) } } },
    MuiAvatar: { styleOverrides: { root: { boxShadow: neu.raisedSm } } },
    MuiTooltip: {
      styleOverrides: { tooltip: { fontSize: 13, backgroundColor: '#1F2937', color: '#F8FAFC' } },
    },
    MuiLinearProgress: {
      styleOverrides: { root: { borderRadius: 6, backgroundColor: raised, height: 8, boxShadow: neu.insetSm } },
    },
  },
});

export const surfaces = { bg, paper, line, raised };
