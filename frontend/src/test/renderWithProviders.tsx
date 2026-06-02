/**
 * `renderWithProviders` wraps a UI tree in the full provider stack used by the
 * real app — React Query, MUI theme, date-pickers, snackbar, and a router
 * pointed at an in-memory entry. Returns the RTL result plus a fresh
 * `user-event` instance for ergonomic interaction.
 */
import { ReactElement, ReactNode } from 'react';
import { render, RenderOptions } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ThemeProvider, CssBaseline } from '@mui/material';
import { LocalizationProvider } from '@mui/x-date-pickers';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import { theme } from '../theme';
import { SnackbarProvider } from '../snackbar/SnackbarProvider';

export interface RenderOptionsEx extends Omit<RenderOptions, 'wrapper'> {
  /** Initial URL the MemoryRouter starts at. Defaults to `/`. */
  route?: string;
  /** If provided, mounts `ui` only under this path so `useParams()` works. */
  path?: string;
  /** Pre-built QueryClient to share between tests. */
  queryClient?: QueryClient;
}

export function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0, staleTime: 0 },
      mutations: { retry: false },
    },
  });
}

interface WrapperProps {
  children: ReactNode;
  route: string;
  path?: string;
  ui: ReactElement;
  queryClient: QueryClient;
}

function Providers({ children, route, path, ui, queryClient }: WrapperProps) {
  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <LocalizationProvider dateAdapter={AdapterDayjs}>
          <SnackbarProvider>
            <MemoryRouter initialEntries={[route]}>
              {path ? (
                <Routes>
                  <Route path={path} element={ui} />
                  <Route path="*" element={<div data-testid="other-route">{children}</div>} />
                </Routes>
              ) : (
                <>{ui}</>
              )}
            </MemoryRouter>
          </SnackbarProvider>
        </LocalizationProvider>
      </ThemeProvider>
    </QueryClientProvider>
  );
}

export function renderWithProviders(ui: ReactElement, opts: RenderOptionsEx = {}) {
  const { route = '/', path, queryClient = createTestQueryClient(), ...rest } = opts;
  const user = userEvent.setup();
  const result = render(
    <Providers route={route} path={path} ui={ui} queryClient={queryClient}>
      <span />
    </Providers>,
    rest,
  );
  return { ...result, user, queryClient };
}

export { userEvent };
