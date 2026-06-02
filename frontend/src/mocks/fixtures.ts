/**
 * Deterministic test fixtures. Factories accept partial overrides so individual
 * tests can tweak only the fields they care about.
 */
import type {
  AnalyticsResponse,
  ShortLinkResponse,
  StrategyDescriptor,
} from '../types';

let nextId = 1;

export function makeShortLink(overrides: Partial<ShortLinkResponse> = {}): ShortLinkResponse {
  const id = overrides.id ?? nextId++;
  const shortCode = overrides.shortCode ?? `code${id}`;
  return {
    id,
    shortCode,
    shortUrl: `http://localhost:8080/${shortCode}`,
    originalUrl: 'https://example.com/destination',
    strategy: 'RANDOM_BASE62',
    expiresAt: null,
    maxClicks: null,
    clickCount: 0,
    tags: [],
    parameters: {},
    status: 'ACTIVE',
    createdAt: '2026-06-01T10:00:00Z',
    updatedAt: '2026-06-01T10:00:00Z',
    ...overrides,
  };
}

export function makeStrategy(overrides: Partial<StrategyDescriptor> = {}): StrategyDescriptor {
  return {
    name: 'RANDOM_BASE62',
    displayName: 'Random Base62',
    description: 'Generates a random N-character base62 code.',
    parameterSchema: [],
    ...overrides,
  };
}

export const STRATEGY_FIXTURES: StrategyDescriptor[] = [
  makeStrategy({
    name: 'RANDOM_BASE62',
    displayName: 'Random Base62',
    description: 'Generates a random N-character base62 code.',
    parameterSchema: [
      { name: 'length', type: 'number', required: false, default: 7, min: 4, max: 16 },
    ],
  }),
  makeStrategy({
    name: 'HASH_TRUNC',
    displayName: 'Hash Truncation',
    description: 'SHA-256 of URL truncated to base62 prefix.',
    parameterSchema: [
      { name: 'salt', type: 'string', required: false, default: '' },
    ],
  }),
  makeStrategy({
    name: 'SEQUENTIAL',
    displayName: 'Sequential',
    description: 'Monotonic sequence base62-encoded.',
    parameterSchema: [],
  }),
  makeStrategy({
    name: 'CUSTOM_ALIAS',
    displayName: 'Custom Alias',
    description: 'User-provided alias.',
    parameterSchema: [
      { name: 'alias', type: 'string', required: true },
    ],
  }),
];

export function makeAnalytics(overrides: Partial<AnalyticsResponse> = {}): AnalyticsResponse {
  return {
    shortLinkId: 42,
    totalClicks: 137,
    last24hClicks: 12,
    uniqueReferers: 3,
    series: [
      { bucket: '2026-06-02T00:00:00Z', count: 5 },
      { bucket: '2026-06-02T01:00:00Z', count: 7 },
      { bucket: '2026-06-02T02:00:00Z', count: 4 },
    ],
    topReferers: [
      { value: 'twitter.com', count: 60 },
      { value: 'reddit.com', count: 30 },
      { value: 'news.ycombinator.com', count: 10 },
    ],
    topUserAgents: [
      { value: 'Mozilla/5.0 (Windows)', count: 70 },
      { value: 'Mozilla/5.0 (Macintosh)', count: 20 },
      { value: 'curl/8.0', count: 10 },
    ],
    ...overrides,
  };
}

/**
 * Reset the auto-increment used by `makeShortLink` so test runs are stable
 * regardless of execution order.
 */
export function resetFixtureIds() {
  nextId = 1;
}
