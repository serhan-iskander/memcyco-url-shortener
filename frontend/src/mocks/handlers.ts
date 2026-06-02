/**
 * MSW request handlers covering every endpoint in `backend/API.md`.
 * The store is in-memory and reset by `resetServer()` between tests so each
 * spec gets a clean slate.
 */
import { http, HttpResponse } from 'msw';
import {
  STRATEGY_FIXTURES,
  makeAnalytics,
  makeShortLink,
  resetFixtureIds,
} from './fixtures';
import type {
  CreateShortLinkRequest,
  LinkStatus,
  ProblemDetail,
  ShortLinkListResponse,
  ShortLinkResponse,
  UpdateShortLinkRequest,
} from '../types';

const API = '/api';

/** Mutable store — exported so tests can pre-seed or inspect it. */
export const store: {
  shortLinks: Map<number, ShortLinkResponse>;
  nextId: number;
} = {
  shortLinks: new Map(),
  nextId: 1,
};

export function resetStore() {
  store.shortLinks.clear();
  store.nextId = 1;
  resetFixtureIds();
  // Seed two default links so the list page has something predictable.
  seed(
    makeShortLink({
      id: 1,
      shortCode: 'abc1234',
      originalUrl: 'https://example.com/one',
      status: 'ACTIVE',
      clickCount: 5,
      tags: ['marketing'],
    }),
  );
  seed(
    makeShortLink({
      id: 2,
      shortCode: 'def5678',
      originalUrl: 'https://example.com/two',
      status: 'EXPIRED',
      clickCount: 12,
      tags: ['ops'],
    }),
  );
  store.nextId = 3;
}

export function seed(link: ShortLinkResponse) {
  store.shortLinks.set(link.id, link);
  if (link.id >= store.nextId) store.nextId = link.id + 1;
}

function problem(status: number, title: string, detail: string, errors?: ProblemDetail['errors']) {
  const body: ProblemDetail = {
    type: `https://memcyco.dev/errors/${title.toLowerCase().replace(/\s+/g, '-')}`,
    title,
    status,
    detail,
    errors,
  };
  return HttpResponse.json(body, { status, headers: { 'Content-Type': 'application/problem+json' } });
}

// A single transparent 1x1 PNG (base64 → bytes) used for the QR endpoint.
const ONE_PIXEL_PNG = Uint8Array.from(
  atob(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=',
  ),
  (c) => c.charCodeAt(0),
);

export const handlers = [
  // ──────────────────────────────────────────────────────────────────────
  // Strategies
  // ──────────────────────────────────────────────────────────────────────
  http.get(`${API}/strategies`, () => HttpResponse.json(STRATEGY_FIXTURES)),

  // ──────────────────────────────────────────────────────────────────────
  // Short links — list with filters/pagination
  // ──────────────────────────────────────────────────────────────────────
  http.get(`${API}/short-links`, ({ request }) => {
    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page') ?? '0');
    const size = Number(url.searchParams.get('size') ?? '20');
    const tag = url.searchParams.get('tag');
    const status = url.searchParams.get('status') as LinkStatus | null;

    let items = Array.from(store.shortLinks.values());
    if (tag) items = items.filter((l) => l.tags.includes(tag));
    if (status) items = items.filter((l) => l.status === status);

    const total = items.length;
    const start = page * size;
    const slice = items.slice(start, start + size);
    const body: ShortLinkListResponse = { items: slice, page, size, total };
    return HttpResponse.json(body);
  }),

  // ──────────────────────────────────────────────────────────────────────
  // Create
  // ──────────────────────────────────────────────────────────────────────
  http.post(`${API}/short-links`, async ({ request }) => {
    const body = (await request.json()) as CreateShortLinkRequest;

    if (!body.originalUrl || !body.originalUrl.startsWith('http')) {
      return problem(400, 'Validation failed', 'Invalid request body', [
        { field: 'originalUrl', message: 'Must be a valid http(s) URL' },
      ]);
    }
    if (body.strategy === 'CUSTOM_ALIAS') {
      const alias = body.alias ?? (body.parameters?.alias as string | undefined);
      if (alias === 'taken') {
        return problem(409, 'Duplicate alias', `Short code '${alias}' is already in use.`, [
          { field: 'alias', message: 'Already in use' },
        ]);
      }
    }

    const id = store.nextId++;
    const shortCode =
      body.strategy === 'CUSTOM_ALIAS'
        ? (body.alias ?? (body.parameters?.alias as string | undefined) ?? `alias${id}`)
        : `gen${id}`;
    const created: ShortLinkResponse = {
      id,
      shortCode,
      shortUrl: `http://localhost:8080/${shortCode}`,
      originalUrl: body.originalUrl,
      strategy: body.strategy,
      expiresAt: body.expiresAt ?? null,
      maxClicks: body.maxClicks ?? null,
      clickCount: 0,
      tags: body.tags ?? [],
      parameters: body.parameters ?? {},
      status: 'ACTIVE',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    store.shortLinks.set(id, created);
    return HttpResponse.json(created, { status: 201 });
  }),

  // ──────────────────────────────────────────────────────────────────────
  // Get by id
  // ──────────────────────────────────────────────────────────────────────
  http.get(`${API}/short-links/:id`, ({ params }) => {
    const id = Number(params.id);
    const link = store.shortLinks.get(id);
    if (!link) return problem(404, 'Not found', `No link with id ${id}`);
    return HttpResponse.json(link);
  }),

  // ──────────────────────────────────────────────────────────────────────
  // Update
  // ──────────────────────────────────────────────────────────────────────
  http.put(`${API}/short-links/:id`, async ({ request, params }) => {
    const id = Number(params.id);
    const existing = store.shortLinks.get(id);
    if (!existing) return problem(404, 'Not found', `No link with id ${id}`);

    const patch = (await request.json()) as UpdateShortLinkRequest;
    const merged: ShortLinkResponse = {
      ...existing,
      ...(patch.originalUrl !== undefined && { originalUrl: patch.originalUrl }),
      ...(patch.expiresAt !== undefined && { expiresAt: patch.expiresAt }),
      ...(patch.maxClicks !== undefined && { maxClicks: patch.maxClicks }),
      ...(patch.tags !== undefined && { tags: patch.tags }),
      updatedAt: new Date().toISOString(),
    };
    store.shortLinks.set(id, merged);
    return HttpResponse.json(merged);
  }),

  // ──────────────────────────────────────────────────────────────────────
  // Delete (soft)
  // ──────────────────────────────────────────────────────────────────────
  http.delete(`${API}/short-links/:id`, ({ params }) => {
    const id = Number(params.id);
    if (!store.shortLinks.has(id)) return problem(404, 'Not found', `No link with id ${id}`);
    store.shortLinks.delete(id);
    return new HttpResponse(null, { status: 204 });
  }),

  // ──────────────────────────────────────────────────────────────────────
  // Analytics
  // ──────────────────────────────────────────────────────────────────────
  http.get(`${API}/short-links/:id/analytics`, ({ params, request }) => {
    const id = Number(params.id);
    if (!store.shortLinks.has(id)) return problem(404, 'Not found', `No link with id ${id}`);
    const url = new URL(request.url);
    const bucket = (url.searchParams.get('bucket') ?? 'hour') as 'hour' | 'day';
    // Vary the series shape slightly by bucket so tests can detect refetches.
    const series =
      bucket === 'day'
        ? [
            { bucket: '2026-06-01T00:00:00Z', count: 16 },
            { bucket: '2026-06-02T00:00:00Z', count: 21 },
          ]
        : [
            { bucket: '2026-06-02T00:00:00Z', count: 5 },
            { bucket: '2026-06-02T01:00:00Z', count: 7 },
            { bucket: '2026-06-02T02:00:00Z', count: 4 },
          ];
    return HttpResponse.json(makeAnalytics({ shortLinkId: id, series }));
  }),

  // ──────────────────────────────────────────────────────────────────────
  // QR code — 1x1 transparent PNG bytes
  // ──────────────────────────────────────────────────────────────────────
  http.get(`${API}/short-links/:id/qr`, ({ params }) => {
    const id = Number(params.id);
    if (!store.shortLinks.has(id)) return problem(404, 'Not found', `No link with id ${id}`);
    return HttpResponse.arrayBuffer(ONE_PIXEL_PNG.buffer.slice(0), {
      status: 200,
      headers: { 'Content-Type': 'image/png' },
    });
  }),
];
