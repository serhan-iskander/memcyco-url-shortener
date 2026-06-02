# API Contract — Frozen for Agent Team

All agents work to this contract. Endpoints are stable; DTOs are stable.

Base URL (dev): `http://localhost:8080`. Frontend dev server proxies `/api/*` → backend.

---

## DTOs

### `CreateShortLinkRequest`
```json
{
  "originalUrl": "https://example.com/very/long/url",
  "strategy": "RANDOM_BASE62",
  "alias": "myalias",
  "expiresAt": "2026-12-31T23:59:59Z",
  "maxClicks": 100,
  "tags": ["campaign-q4", "newsletter"],
  "parameters": { "length": 8 }
}
```
| Field | Type | Required | Notes |
|---|---|---|---|
| `originalUrl` | string (URL) | yes | Must be http(s). |
| `strategy` | string enum | yes | One of: `RANDOM_BASE62`, `HASH_TRUNC`, `SEQUENTIAL`, `CUSTOM_ALIAS`. |
| `alias` | string | required only when strategy = `CUSTOM_ALIAS` | 3–32 chars, `[a-zA-Z0-9_-]`. |
| `expiresAt` | ISO-8601 timestamp (UTC) | no | Must be future. |
| `maxClicks` | integer ≥ 1 | no | |
| `tags` | string[] | no | Defaults to `[]`. |
| `parameters` | object | no | Strategy-specific; validated against `parameterSchema`. |

### `UpdateShortLinkRequest`
Same shape as create **minus `strategy`, `alias`, `parameters.alias`** (short code is immutable after creation). All fields optional — server applies a PATCH-like merge.

### `ShortLinkResponse`
```json
{
  "id": 42,
  "shortCode": "aB3xK7q",
  "shortUrl": "http://localhost:8080/aB3xK7q",
  "originalUrl": "https://example.com/very/long/url",
  "strategy": "RANDOM_BASE62",
  "expiresAt": "2026-12-31T23:59:59Z",
  "maxClicks": 100,
  "clickCount": 17,
  "tags": ["campaign-q4"],
  "parameters": { "length": 8 },
  "status": "ACTIVE",
  "createdAt": "2026-06-02T12:00:00Z",
  "updatedAt": "2026-06-02T12:00:00Z"
}
```
`status` is **derived** server-side: `ACTIVE` | `EXPIRED` | `EXHAUSTED` | `INACTIVE`.

### `ShortLinkListResponse`
```json
{
  "items": [ShortLinkResponse, ...],
  "page": 0,
  "size": 20,
  "total": 137
}
```

### `AnalyticsResponse`
```json
{
  "shortLinkId": 42,
  "totalClicks": 137,
  "last24hClicks": 12,
  "uniqueReferers": 8,
  "series": [
    { "bucket": "2026-06-02T00:00:00Z", "count": 5 },
    { "bucket": "2026-06-02T01:00:00Z", "count": 7 }
  ],
  "topReferers": [{ "value": "twitter.com", "count": 42 }, ...],
  "topUserAgents": [{ "value": "Mozilla/5.0 ...", "count": 31 }, ...]
}
```

### `StrategyDescriptor`
```json
{
  "name": "RANDOM_BASE62",
  "displayName": "Random Base62",
  "description": "Generates a random N-character base62 code.",
  "parameterSchema": [
    { "name": "length", "type": "number", "required": false, "default": 7, "min": 4, "max": 16 }
  ]
}
```
Parameter `type`: `string` | `number` | `boolean` | `date`.

### `ProblemDetail` (RFC 7807)
```json
{
  "type": "https://memcyco.dev/errors/duplicate-alias",
  "title": "Duplicate alias",
  "status": 409,
  "detail": "Short code 'myalias' is already in use.",
  "instance": "/api/short-links",
  "errors": [
    { "field": "alias", "message": "Already in use" }
  ]
}
```

---

## Endpoints

| Method | Path | Status | Body |
|---|---|---|---|
| `POST` | `/api/short-links` | 201 / 400 / 409 | `CreateShortLinkRequest` → `ShortLinkResponse` |
| `GET` | `/api/short-links?page=0&size=20&tag=&status=` | 200 | `ShortLinkListResponse` |
| `GET` | `/api/short-links/{id}` | 200 / 404 | `ShortLinkResponse` |
| `PUT` | `/api/short-links/{id}` | 200 / 400 / 404 | `UpdateShortLinkRequest` → `ShortLinkResponse` |
| `DELETE` | `/api/short-links/{id}` | 204 / 404 | (no body) — soft delete |
| `GET` | `/api/short-links/{id}/analytics?bucket=hour\|day&from=&to=` | 200 / 404 | `AnalyticsResponse` |
| `GET` | `/api/short-links/{id}/qr?size=256` | 200 (PNG) / 404 | `image/png` (bonus) |
| `GET` | `/api/strategies` | 200 | `StrategyDescriptor[]` |
| `GET` | `/{shortCode}` | 302 / 404 / 410 / 429 | Redirect — Location header set |

### Redirect status semantics
- `302` — happy path. `Location` header is the original URL.
- `404` — short code does not exist (or is soft-deleted).
- `410 Gone` — link was found but is expired / click-exhausted / `active=false`.
- `429 Too Many Requests` — bonus rate limiter triggered.

### Pagination defaults
- `page` defaults to 0, `size` defaults to 20, max 100.
- `status` filter accepts `ACTIVE | EXPIRED | EXHAUSTED | INACTIVE`.
- `tag` filter is an exact match against any element of `tags[]`.

---

## Validation rules (backend + frontend mirror these)

- `originalUrl`: must parse as URL, scheme http or https, ≤ 2048 chars.
- `alias`: regex `^[a-zA-Z0-9_-]{3,32}$`, must be unique among live (non-deleted) links.
- `expiresAt`: must be strictly future on create. On update, may stay in the past.
- `maxClicks`: integer 1..1_000_000.
- `tags`: each tag ≤ 32 chars, regex `^[a-zA-Z0-9_-]+$`, ≤ 10 tags total.
- `parameters`: validated against the chosen strategy's `parameterSchema` (bonus).
