import axios, { AxiosError, AxiosInstance } from 'axios';
import { ApiError, ProblemDetail } from '../types';

/**
 * Axios instance preconfigured with /api baseURL.
 * The response interceptor normalizes RFC 7807 ProblemDetail bodies into
 * the typed `ApiError` so React Query mutations always receive a consistent
 * error shape with field-level details.
 */
export const apiClient: AxiosInstance = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ProblemDetail | string>) => {
    if (error.response) {
      const { status, data } = error.response;
      const problem: ProblemDetail =
        typeof data === 'object' && data !== null
          ? { ...(data as ProblemDetail), status }
          : {
              status,
              title: error.response.statusText || `HTTP ${status}`,
              detail: typeof data === 'string' ? data : undefined,
            };
      return Promise.reject(new ApiError(problem));
    }
    return Promise.reject(
      new ApiError({
        status: 0,
        title: 'Network error',
        detail: error.message,
      }),
    );
  },
);

/**
 * Browser-visible backend base URL. Used to render shortened URLs that the
 * user can copy and share. In dev defaults to localhost:8080; in production
 * builds it can be supplied via `VITE_BACKEND_BASE_URL`.
 */
export function getBackendBaseUrl(): string {
  const fromEnv = import.meta.env.VITE_BACKEND_BASE_URL as string | undefined;
  if (fromEnv && fromEnv.length > 0) return fromEnv.replace(/\/$/, '');
  if (typeof window !== 'undefined') {
    // When served from the same origin as the backend (e.g. nginx-proxied),
    // we render shortened URLs at the current origin.
    return window.location.origin;
  }
  return 'http://localhost:8080';
}

export function buildShortUrl(shortCode: string): string {
  return `${getBackendBaseUrl()}/${shortCode}`;
}
