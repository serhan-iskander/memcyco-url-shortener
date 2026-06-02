import { createTheme } from '@mui/material/styles';

/**
 * A modern, clean MUI theme. Primary blue with a teal accent; high-contrast
 * surfaces. Stays on the system sans-serif stack (no Google Fonts since we
 * cannot edit index.html).
 */
export const theme = createTheme({
  palette: {
    mode: 'light',
    primary: {
      main: '#1f5fff',
      dark: '#1646c2',
      light: '#5a85ff',
      contrastText: '#ffffff',
    },
    secondary: {
      main: '#00b8a9',
      dark: '#008479',
      light: '#4be0d3',
      contrastText: '#ffffff',
    },
    background: {
      default: '#f4f6fb',
      paper: '#ffffff',
    },
    success: { main: '#2e7d32' },
    warning: { main: '#ed6c02' },
    error: { main: '#d32f2f' },
    info: { main: '#0288d1' },
    divider: 'rgba(15, 23, 42, 0.08)',
  },
  shape: { borderRadius: 10 },
  typography: {
    fontFamily:
      '"Inter", system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
    h4: { fontWeight: 600, letterSpacing: '-0.01em' },
    h5: { fontWeight: 600, letterSpacing: '-0.01em' },
    h6: { fontWeight: 600 },
    button: { textTransform: 'none', fontWeight: 600 },
  },
  components: {
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: { borderRadius: 8 },
      },
    },
    MuiPaper: {
      styleOverrides: {
        rounded: { borderRadius: 12 },
      },
    },
    MuiAppBar: {
      defaultProps: { color: 'default', elevation: 0 },
      styleOverrides: {
        root: {
          backgroundColor: '#ffffff',
          borderBottom: '1px solid rgba(15, 23, 42, 0.08)',
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: { borderRadius: 6, fontWeight: 500 },
      },
    },
  },
});
