import { Alert, Snackbar } from '@mui/material';
import { createContext, useCallback, useContext, useMemo, useState, ReactNode } from 'react';

type Severity = 'success' | 'info' | 'warning' | 'error';

interface SnackbarMessage {
  id: number;
  text: string;
  severity: Severity;
}

interface SnackbarApi {
  notify: (text: string, severity?: Severity) => void;
  success: (text: string) => void;
  error: (text: string) => void;
  info: (text: string) => void;
  warning: (text: string) => void;
}

const SnackbarContext = createContext<SnackbarApi | null>(null);

let nextId = 1;

export function SnackbarProvider({ children }: { children: ReactNode }) {
  const [current, setCurrent] = useState<SnackbarMessage | null>(null);

  const notify = useCallback((text: string, severity: Severity = 'info') => {
    setCurrent({ id: nextId++, text, severity });
  }, []);

  const api = useMemo<SnackbarApi>(
    () => ({
      notify,
      success: (t) => notify(t, 'success'),
      error: (t) => notify(t, 'error'),
      info: (t) => notify(t, 'info'),
      warning: (t) => notify(t, 'warning'),
    }),
    [notify],
  );

  const handleClose = (_: unknown, reason?: string) => {
    if (reason === 'clickaway') return;
    setCurrent(null);
  };

  return (
    <SnackbarContext.Provider value={api}>
      {children}
      <Snackbar
        key={current?.id}
        open={Boolean(current)}
        autoHideDuration={4000}
        onClose={handleClose}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      >
        {current ? (
          <Alert
            onClose={() => setCurrent(null)}
            severity={current.severity}
            variant="filled"
            sx={{ width: '100%' }}
          >
            {current.text}
          </Alert>
        ) : undefined}
      </Snackbar>
    </SnackbarContext.Provider>
  );
}

export function useSnackbar(): SnackbarApi {
  const ctx = useContext(SnackbarContext);
  if (!ctx) {
    throw new Error('useSnackbar must be used within <SnackbarProvider>');
  }
  return ctx;
}
