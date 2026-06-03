# memcyco: URL Shortener with Analytics

Full-stack URL shortener with per-link click analytics. Java 21 + Spring Boot 3.3 backend, React 18 + TypeScript + MUI frontend, PostgreSQL + Redis. Built as the Memcyco home assessment by **Serhan Iskander** (`ser_ask@yahoo.com`).

---

## Quick start

```bash
# Bring up everything (postgres + redis + backend + frontend):
docker compose up -d --build

# Open the UI:
#   http://localhost:5173
#
# Swagger / OpenAPI:
#   http://localhost:8080/swagger-ui.html
#
# Health:
#   http://localhost:8080/actuator/health
#
# Tear down (keeps data volume):
docker compose down
#
# Tear down + wipe the postgres volume:
docker compose down -v
```

Redirect a short link by hitting the backend directly:
```
http://localhost:8080/{shortCode}      → 302 to the original URL
```

The frontend dev server proxies `/api/*` to backend `:8080` but does **not** proxy bare `/{shortCode}` paths; they go directly to the backend.

---

## What's implemented

### Core requirements (assessment spec)

| | |
|---|---|
| React UI with CRUD + analytics view | ✅ |
| `GET /{shortCode}` 302 redirect | ✅ |
| Async click tracking (timestamp, referer, user-agent, IP), non-blocking | ✅ |
| Redis caching on the redirect path | ✅ |
| 4 short-code strategies (read-only): `RANDOM_BASE62`, `HASH_TRUNC`, `SEQUENTIAL`, `CUSTOM_ALIAS` | ✅ |
| Auto codes + custom aliases (unique among live links) | ✅ |
| Expiration → `410 Gone` | ✅ |
| `max_clicks` gating → `410 Gone` | ✅ |
| Missing code → `404 Not Found` | ✅ |
| Tags + filtering | ✅ |
| Unit + integration tests, ≥70% coverage on hot packages | ✅ |
| Docker / docker-compose for full stack | ✅ |

### Bonus

| | |
|---|---|
| Parameter schema per strategy + UI dynamic form + backend validation | ✅ |
| QR code generation (`/api/short-links/{id}/qr`): ZXing PNG | ✅ |
| Rate limiting on `/{shortCode}`: Bucket4j + Redis token bucket, fails open | ✅ (on by default, 60/min/IP) |
| Geo enrichment of click data: MaxMind GeoLite2 conditional bean | ✅ (off by default; mmdb volume) |
| RFC 7807 `application/problem+json` errors | ✅ |
| OpenAPI / Swagger UI | ✅ |
| Soft delete with reclaimable codes (partial unique index) | ✅ |

---

## Architecture

### Redirect hot path (the critical one)

```
GET /{shortCode}
  ↓
RateLimitInterceptor (per-IP, fails open if Redis is down)
  ↓
RedirectController
  ↓
RedirectService.resolve()
  │
  ├─ ShortLinkCache.get("shortlink:"+code)        ← single Redis GET
  │     ├─ HIT (real value)         → continue to status gate
  │     ├─ HIT (NOT_FOUND sentinel) → throw → 404
  │     └─ MISS:
  │           repo.findByShortCode(code)           ← only filters deleted_at IS NULL
  │             ├─ none returned    → cache.putMiss() → throw → 404
  │             └─ row returned     → cache.put() → continue to status gate
  │
  ▼ status gate (same logic on both paths, NOT done in SQL):
       ├─ atomic Redis INCR shortlink:count:CODE → newCount
       ├─ status = derive(active, expiresAt, base + newCount, maxClicks)
       └─ if status != ACTIVE → throw → 410
  ↓
ClickTracker.track(ClickEvent)                    ← non-blocking, bounded queue
  ↓
return 302, Location: <originalUrl>               ← ZERO blocking I/O on hot path

─── concurrently (separate scheduled job) ──────────────────────────────────
ClickBatchWriter @Scheduled(500ms)
  ↓
batched JDBC INSERT into clicks (JSONB metadata)
  + bulk UPDATE short_links.click_count += N
```

Cache hit: **one Redis GET + one Redis INCR + one HTTP response**. Zero blocking I/O for the user. Verified by an integration test that asserts the second redirect issues zero `short_links` prepared statements (via Hibernate `Statistics`).

**Status check intentionally runs in-memory, not in SQL.** `repo.findByShortCode()` returns any live (non-soft-deleted) row regardless of expired/exhausted/inactive, because status is **time-derived**. An `ACTIVE` link becomes `EXPIRED` when the clock crosses `expiresAt` without the row ever changing. Storing raw fields in the cache and computing status at read time means cache entries auto-correct as time passes. Only `deleted_at IS NULL` is filtered at the SQL layer (via Hibernate `@SQLRestriction`), because deletion is a permanent invariant rather than a time-derived one.

### Data model (key shapes)

```sql
short_links (
  id, short_code, original_url, strategy,
  expires_at, max_clicks, click_count, tags TEXT[], parameters JSONB,
  active, deleted_at, created_at, updated_at
)
-- Soft delete: partial unique index lets a deleted short_code be reclaimed.
CREATE UNIQUE INDEX short_links_code_live_uq
    ON short_links (short_code) WHERE deleted_at IS NULL;

clicks (
  id, short_link_id, clicked_at,
  data JSONB         -- { referer, userAgent, ip, country, city, ... }
)
-- GIN + expression indexes on data->>'referer' and data->>'userAgent' for analytics.
```

**Why JSONB for click metadata**: the captured fields grow over time (geo adds `country`/`city`; later we may parse UA into `device`/`browser`/`os`). JSONB avoids a migration per field. The two columns used by every analytics query (`short_link_id`, `clicked_at`) remain first-class.

### Strategy pattern

`ShortCodeStrategy` interface; four impls discovered as Spring beans. The registry exposes them read-only at `GET /api/strategies` with their `parameterSchema`, which the UI consumes to render dynamic form fields. Adding a strategy = new bean + no edits anywhere else (the service uses `strategy.prepareParams(...)` to lift convenience top-level fields like `alias`, kept open/closed).

### Async click tracking

`ClickTracker` pushes onto a bounded `LinkedBlockingQueue` (default 10 000). Queue full → drop + `memcyco_clicks_dropped_total` Micrometer counter increment. Never blocks the redirect. `ClickBatchWriter` drains the queue every 500 ms or 100 events, doing one batched insert into `clicks` plus one grouped `UPDATE short_links.click_count`. On JVM shutdown a `@PreDestroy` hook drains the rest.

### Cache invalidation

`ShortLinkChanged` event (in `shortlink/event/`) is published from the service on update or soft-delete. `ShortLinkCacheInvalidationListener` runs on `TransactionPhase.AFTER_COMMIT` and drops the Redis entry. TTL is the backstop; explicit invalidation is the primary mechanism. `ShortLinkCache.put()` also DELs the per-window click counter so the count never double-counts across cache windows.

---

## Strategies

| Name | Description | Required params |
|---|---|---|
| `RANDOM_BASE62` | Cryptographically random N-char base62 code (default 7). Retries on collision. | none (optional `length` 4 to 16) |
| `HASH_TRUNC` | SHA-256 of URL (+ optional `salt`), first N base62 chars. Lengthens on collision. | none (optional `salt`) |
| `SEQUENTIAL` | Reads from a dedicated Postgres sequence, base62-encodes the id. ⚠ **Security caveat**: codes leak creation order and total volume (an adversary can enumerate). Don't use it for links whose existence should stay private. | none |
| `CUSTOM_ALIAS` | User-supplied alias. Regex `^[a-zA-Z0-9_-]{3,32}$`. No collision retry; duplicates return 409. | `alias` (required) |

---

## API surface

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/short-links` | Create |
| `GET` | `/api/short-links` | List w/ `page`, `size`, `tag`, `status` filters |
| `GET` | `/api/short-links/{id}` | Detail |
| `PUT` | `/api/short-links/{id}` | Update (`shortCode` immutable) |
| `DELETE` | `/api/short-links/{id}` | Soft delete |
| `GET` | `/api/short-links/{id}/analytics` | `?bucket=hour\|day` |
| `GET` | `/api/short-links/{id}/qr` | PNG |
| `GET` | `/api/strategies` | Read-only strategy list + schemas |
| `GET` | `/{shortCode}` | 302 / 404 / 410 / 429 |

Full request/response shapes: `backend/API.md`. Live interactive docs: `http://localhost:8080/swagger-ui.html`.

---

## Tests

```bash
# Backend (host JDK 21 required)
cd backend && ./mvnw.cmd -B "-Dtest=!*IT" test
#   48 / 48 unit tests pass.
#
# Backend integration tests (Testcontainers needs Docker daemon reachable)
cd backend && ./mvnw.cmd -B verify
#   Note: on Windows 11 + Docker Desktop 4.69, Testcontainers can't reach the
#   daemon via the default named-pipe path. Workaround: enable TCP 2375 in
#   Docker Desktop, or use the live-stack smoke (`docker compose up`).
#
# Frontend (Vitest + RTL + MSW)
cd frontend && npm install && npm run test:coverage
#   62 / 62 tests pass.
#   Coverage: components 87.89% / 77.88%, pages 90.43% / 72.51% (target ≥70%).
```

### Test design notes
- **Unit tests** are pure (Mockito): strategies, `RedirectService` branches (including INCR-then-gate regression for the max_clicks TOCTOU bug and the cache-window counter-reset regression), `ClickTracker` overflow, `ClickBatchWriter` batching, `GlobalExceptionHandler` mapping, `ParameterSchemaValidator`.
- **Integration tests** (`*IT.java`) hit a real Postgres + Redis via Testcontainers. The critical one is `RedirectControllerIT`: create link, hit `/{code}`, assert 302 + Location, poll until click row appears in DB, plus a cache-skip-DB assertion via Hibernate `Statistics`.
- **Frontend** uses MSW to mock the API; tests cover the create flow with dynamic strategy params, 409 inline errors, the soft-delete confirm dialog, and the analytics page with chart + breakdown tables.

---

## Repository layout

```
memcyco/
├── README.md                       (this file)
├── docker-compose.yml              (postgres, redis, backend, frontend; self-contained, one command)
├── .env.example                    (override surface for compose + JVM)
├── .gitignore
├── backend/                        (Spring Boot 3.3 + Java 21 + Maven wrapper)
│   ├── API.md                      (frozen API contract: DTOs, endpoints, validation)
│   ├── Dockerfile                  (multi-stage: maven → jre-alpine)
│   ├── pom.xml
│   └── src/{main,test}/{java,resources}
│       ├── main/resources/db/migration/V1__init.sql
│       └── main/java/com/memcyco/shortener/
│           ├── shortlink/{api,service,repo,cache,domain,dto,event}
│           ├── strategy/
│           ├── tracking/
│           ├── common/{error,ratelimit}
│           └── config/
├── frontend/                       (Vite + React 18 + TS + MUI v5 + Recharts + React Query)
│   ├── Dockerfile                  (node-build → nginx)
│   ├── nginx.conf                  (SPA fallback + /api proxy)
│   ├── package.json
│   └── src/
│       ├── api/                    (axios client + endpoint wrappers)
│       ├── components/             (DynamicParamFields, ShortLinksTable, ClicksChart, ...)
│       ├── pages/                  (ShortLinksPage, ShortLinkFormPage, AnalyticsPage)
│       ├── hooks/                  (React Query wrappers)
│       ├── mocks/                  (MSW for tests)
│       ├── test/                   (RTL setup + providers wrapper)
│       └── types/                  (mirror of backend DTOs)
└── docker/
    └── README.md                   (compose profile notes, host-reuse vs all-in-compose, geo MMDB instructions)
```

---

## Design decisions

- **Redis cache + StringRedisTemplate split**: the cached `CachedShortLink` value uses a typed Jackson serializer; the per-code click counter (INCRBY) uses `StringRedisTemplate` so reading back a raw numeric string doesn't crash the Jackson deserializer. (This was a real bug found during live smoke testing.)
- **Atomic INCR-then-gate for `max_clicks`**: `RedirectService.resolve()` does the Redis INCR first and gates on the post-increment value. Eliminates the TOCTOU race where two concurrent requests both read count-before-gate at N-1 and both passed.
- **Counter reset on `cache.put()`**: the per-cache-window counter is DELed whenever the entity is refreshed from the DB, so naturally expired entries don't double-count clicks that have already been flushed into the `click_count` column.
- **Status is derived, never stored**: `ACTIVE` / `EXPIRED` / `EXHAUSTED` / `INACTIVE` is computed from `(active, expires_at, click_count, max_clicks)` at read time. No cron job toggles rows.
- **Soft delete over hard delete**: clicks history is preserved. The partial unique index lets a deleted short code be reclaimed by a new link.
- **`click_count` is eventually consistent**: the column lags the `clicks` table by a small margin under traffic. Redis holds the near-real-time counter that gates `max_clicks`; overshoot tolerated, undershoot prevented.
- **JPA `ddl-auto: none`**: Flyway is the sole schema source of truth. Hibernate's `validate` was rejecting the `TEXT[]` array mapping.
- **Geo enrichment on the hot path**: `GeoEnricher.enrich()` is called synchronously inside `ClickTracker.track()`. The interface contract (load-bearing, documented in the Javadoc) requires implementations to be fast and in-memory. `NoopGeoEnricher` (default) is a no-op; `MaxMindGeoEnricher` does an in-memory mmdb lookup in microseconds.

## Assumptions

- **No authentication.** The spec doesn't mention it; single-tenant.
- **Single backend instance.** The in-process click queue is fine for the demo. A multi-replica deployment would swap `ClickBatchWriter` for Kafka or Redis Streams.
- **Trusting client-supplied URLs.** The backend validates URL syntax but doesn't fetch the destination or check it against safe-browsing lists. Production would add that.
- **Rate limiting on by default** (60 redirects/min/IP on `/{shortCode}`). Disable with `APP_RATE_LIMIT_ENABLED=false`. It fails open: if Redis is unreachable the limiter logs and lets traffic through (with a 30s cooldown), so it never costs redirect availability.
- **Geo enrichment off by default.** Flag with `APP_GEO_ENABLED=true`. Geo also needs a mounted MMDB; see `docker/README.md` for instructions.
- **Demo credentials in `docker-compose.yml`.** `POSTGRES_PASSWORD=memcyco` is hardcoded for one-command setup. See `.env.example` for the override surface; any real deployment should externalize the password and rotate it.
- **Analytics date range.** The backend supports `?from=&to=` on `/analytics`, but the UI only exposes the hour/day bucket toggle. Server-side filtering by an explicit window is a one-component-add away.

---

## AI tool usage

This codebase was built by **Serhan Iskander** with the assistance of **Claude Code** (Anthropic, model Opus 4.7 with 1M-token context).

Workflow:
1. I scoped the system and froze the API contract (`backend/API.md`) up front.
2. I dispatched a parallel team of five specialist sub-agents (backend impl, frontend impl, backend test writer, frontend test writer, infra/docker), each briefed with the same plan and contract, owning non-overlapping file paths.
3. They worked concurrently. The infra agent reused my host's existing Redis container (on a Docker network not published to host) by attaching the backend service to that external network, a useful design decision I would not have noticed alone.
4. After the agents returned, I ran a reconciliation pass to align test imports with the impl's actual symbol names, then exercised every assessment requirement live against the running stack (curl + browser via the Claude in Chrome extension).
5. I applied four SOLID/robustness fix-ups identified by a static review pass: removing a `if (CustomAliasStrategy.NAME...)` OCP violation by adding `ShortCodeStrategy.prepareParams()`; documenting the `GeoEnricher` fast-path LSP contract; extracting the cache-invalidation event from a nested class into its own DIP-clean type; replacing a hardcoded DB index name with a constant.
6. Live smoke surfaced two further bugs that were not caught by the test suite at the time: the cache-window counter double-count after TTL expiry, and a TOCTOU race on `max_clicks` between read-then-gate and the separate INCR. Both were fixed with regression tests added (`incrementBeforeGateRejectsOvershoot`, `incrementBeforeGateAllowsLastClick`, `cacheMissResetsCounterOnPut`, `expiredLinkDoesNotIncrement`).

All design decisions, the JSONB click-data model, the Redis cache architecture, and final code review were owned by me. The agents accelerated execution; the judgment was mine.

---

## License

Submitted as a take-home assessment to Memcyco. Not licensed for redistribution.
