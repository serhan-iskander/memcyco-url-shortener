/**
 * Node-side MSW server used by Vitest. Lifecycle is wired in
 * `src/test/setup.ts`.
 */
import { setupServer } from 'msw/node';
import { handlers, resetStore } from './handlers';

export const server = setupServer(...handlers);

/**
 * Reset both the request handlers and the in-memory store. Call from
 * `afterEach` if you don't want state to bleed between tests; the global
 * setup already wires this up.
 */
export function resetServer() {
  server.resetHandlers(...handlers);
  resetStore();
}
