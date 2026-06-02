/**
 * Global Vitest setup: jest-dom matchers, MSW lifecycle, jsdom polyfills.
 * Wired through `vite.config.ts` → `test.setupFiles`.
 */
import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach, beforeAll, vi } from 'vitest';
import { resetServer, server } from '../mocks/server';
import { resetStore } from '../mocks/handlers';

// Default backend base URL for tests — keeps `buildShortUrl()` deterministic.
vi.stubEnv('VITE_BACKEND_BASE_URL', 'http://localhost:8080');

// ── jsdom polyfills MUI / Recharts expect ─────────────────────────────────
class ResizeObserverPolyfill {
  observe() {}
  unobserve() {}
  disconnect() {}
}
if (typeof globalThis.ResizeObserver === 'undefined') {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (globalThis as any).ResizeObserver = ResizeObserverPolyfill;
}

if (typeof window !== 'undefined' && typeof window.matchMedia !== 'function') {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }),
  });
}

// `URL.createObjectURL` / `revokeObjectURL` — used by QrDialog to render the
// fetched PNG blob into an <img src>. jsdom doesn't provide them.
if (typeof URL.createObjectURL !== 'function') {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (URL as any).createObjectURL = (_blob: Blob) => 'blob:mock://qr/0';
}
if (typeof URL.revokeObjectURL !== 'function') {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (URL as any).revokeObjectURL = (_url: string) => {};
}

// navigator.clipboard — jsdom provides one in modern versions but the writeText
// method may not be a spyable function. Replace it for predictable assertions.
if (typeof navigator !== 'undefined') {
  Object.defineProperty(navigator, 'clipboard', {
    configurable: true,
    value: {
      writeText: vi.fn(async () => undefined),
      readText: vi.fn(async () => ''),
    },
  });
}

// ── MSW lifecycle ─────────────────────────────────────────────────────────
beforeAll(() => {
  server.listen({ onUnhandledRequest: 'error' });
});

afterEach(() => {
  resetServer();
  // Re-seed default store data after handler/store reset so each test starts
  // from the same baseline (two pre-existing links).
  resetStore();
});

afterAll(() => {
  server.close();
});

// Seed the store immediately so the very first test sees the same baseline
// the rest enjoy.
resetStore();
