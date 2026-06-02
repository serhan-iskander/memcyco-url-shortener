/**
 * Sanity test for the test infrastructure itself. Verifies that jest-dom is
 * registered, MSW intercepts requests, and jsdom polyfills are in place.
 * This file does NOT import anything from `src/` other than mocks, so it can
 * run before Agent B's components land.
 */
import { describe, expect, it } from 'vitest';
import { STRATEGY_FIXTURES } from '../mocks/fixtures';

describe('test infrastructure', () => {
  it('exposes jest-dom matchers', () => {
    const el = document.createElement('div');
    el.textContent = 'hello';
    document.body.appendChild(el);
    expect(el).toBeInTheDocument();
    expect(el).toHaveTextContent('hello');
    el.remove();
  });

  it('polyfills ResizeObserver', () => {
    expect(typeof ResizeObserver).toBe('function');
    const ro = new ResizeObserver(() => {});
    expect(ro.observe).toBeDefined();
  });

  it('polyfills matchMedia', () => {
    const mq = window.matchMedia('(min-width: 600px)');
    expect(mq).toHaveProperty('matches', false);
  });

  it('polyfills URL.createObjectURL', () => {
    const url = URL.createObjectURL(new Blob([new Uint8Array([0])], { type: 'image/png' }));
    expect(url).toMatch(/^blob:/);
  });

  it('MSW intercepts /api/strategies', async () => {
    const res = await fetch('/api/strategies');
    expect(res.ok).toBe(true);
    const body = await res.json();
    expect(body).toHaveLength(STRATEGY_FIXTURES.length);
    expect(body[0].name).toBe('RANDOM_BASE62');
  });

  it('MSW intercepts /api/short-links list with seed data', async () => {
    const res = await fetch('/api/short-links');
    const body = await res.json();
    expect(body.items.length).toBeGreaterThanOrEqual(2);
    expect(body.total).toBeGreaterThanOrEqual(2);
  });

  it('MSW returns 409 ProblemDetail for alias=taken', async () => {
    const res = await fetch('/api/short-links', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        originalUrl: 'https://example.com',
        strategy: 'CUSTOM_ALIAS',
        alias: 'taken',
      }),
    });
    expect(res.status).toBe(409);
    const body = await res.json();
    expect(body.title).toBe('Duplicate alias');
    expect(body.errors[0].field).toBe('alias');
  });

  it('MSW QR endpoint returns image/png bytes', async () => {
    // Pre-seeded id 1.
    const res = await fetch('/api/short-links/1/qr');
    expect(res.status).toBe(200);
    expect(res.headers.get('content-type')).toBe('image/png');
    const buf = await res.arrayBuffer();
    expect(buf.byteLength).toBeGreaterThan(0);
  });
});
