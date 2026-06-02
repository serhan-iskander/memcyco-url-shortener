/**
 * Verifies the axios interceptor normalizes RFC 7807 `ProblemDetail` payloads
 * into a typed `ApiError` exposing `fieldErrors`.
 */
import { describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { apiClient, buildShortUrl, getBackendBaseUrl } from './client';
import { ApiError } from '../types';

describe('apiClient error normalization', () => {
  it('maps a 409 ProblemDetail to ApiError with fieldErrors', async () => {
    server.use(
      http.post('/api/short-links', () =>
        HttpResponse.json(
          {
            type: 'https://memcyco.dev/errors/duplicate-alias',
            title: 'Duplicate alias',
            status: 409,
            detail: "Short code 'taken' is already in use.",
            errors: [{ field: 'alias', message: 'Already in use' }],
          },
          { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    );

    try {
      await apiClient.post('/short-links', {
        originalUrl: 'https://x',
        strategy: 'CUSTOM_ALIAS',
        alias: 'taken',
      });
      throw new Error('expected request to reject');
    } catch (err) {
      expect(err).toBeInstanceOf(ApiError);
      const apiErr = err as ApiError;
      expect(apiErr.status).toBe(409);
      expect(apiErr.title).toBe('Duplicate alias');
      expect(apiErr.fieldErrors).toEqual([{ field: 'alias', message: 'Already in use' }]);
      expect(apiErr.fieldError('alias')).toBe('Already in use');
      expect(apiErr.fieldError('missing')).toBeUndefined();
    }
  });

  it('handles plain string error bodies', async () => {
    server.use(
      http.get('/api/short-links/999', () =>
        HttpResponse.text('not found', { status: 404 }),
      ),
    );
    await expect(apiClient.get('/short-links/999')).rejects.toBeInstanceOf(ApiError);
  });

  it('handles network errors by wrapping them in ApiError(status=0)', async () => {
    server.use(
      http.get('/api/strategies', () => HttpResponse.error()),
    );
    try {
      await apiClient.get('/strategies');
      throw new Error('expected request to reject');
    } catch (err) {
      expect(err).toBeInstanceOf(ApiError);
      expect((err as ApiError).status).toBe(0);
    }
  });
});

describe('buildShortUrl', () => {
  it('uses VITE_BACKEND_BASE_URL when set', () => {
    // setup.ts stubs the env to http://localhost:8080.
    expect(getBackendBaseUrl()).toBe('http://localhost:8080');
    expect(buildShortUrl('aB3xK7q')).toBe('http://localhost:8080/aB3xK7q');
  });
});
