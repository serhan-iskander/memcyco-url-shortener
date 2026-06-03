# memcyco Docker

The stack is self-contained. One command brings up the whole system on a private
bridge network, with no external networks or pre-existing containers required.

```bash
docker compose up -d --build
```

Open:
- frontend: http://localhost:5173
- backend: http://localhost:8080 (Swagger at `/swagger-ui.html`)

## Services

| Service    | Source                                              |
|------------|-----------------------------------------------------|
| `postgres` | Owned by this project. Publishes host port 5432.    |
| `redis`    | Owned by this project. Internal only (not published).|
| `backend`  | Built from `backend/Dockerfile`.                    |
| `frontend` | Built from `frontend/Dockerfile`, served by nginx.  |

Startup order is gated by healthchecks: the backend waits for `postgres` and
`redis` to report healthy, and the frontend waits for the backend.

## Backend wiring

Set on the `backend` service in `docker-compose.yml`:
- `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/memcyco`
- `SPRING_DATA_REDIS_HOST=redis`
- `SPRING_DATA_REDIS_PORT=6379`

## Optional: reuse a host Redis

To point the backend at a Redis you already run on the host instead of the
bundled one, override the host without editing compose:

```bash
SPRING_DATA_REDIS_HOST=host.docker.internal docker compose up -d --build
```

```powershell
$env:SPRING_DATA_REDIS_HOST = "host.docker.internal"; docker compose up -d --build
```

The bundled `redis` service still starts but goes unused; that is harmless. The
`backend` service declares `host.docker.internal:host-gateway` so the override
resolves on Linux too.

## Local backend dev (Spring Boot from your IDE)

When the backend runs on the host rather than in compose, point at the
host-published Postgres and a local Redis:
- `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/memcyco`
- `SPRING_DATA_REDIS_HOST=localhost`

Redis is not published to the host by the stack, so for IDE runs start a
throwaway one: `docker run --rm -p 6379:6379 redis:7-alpine`.

## Teardown

```bash
# Stop containers, keep the postgres volume:
docker compose down

# Stop containers AND drop the memcyco_pgdata volume:
docker compose down -v
```

## Bonus: enabling geo enrichment

The backend ships with a `MaxMindGeoEnricher` that annotates each click row's
JSONB `data` column with `country` / `city` derived from the visitor IP. It is
disabled by default. Turning it on takes two steps.

### 1. Get the MMDB file

MaxMind publishes the free GeoLite2-City database. Sign up for a free account
at https://www.maxmind.com/en/geolite2/signup, download
**GeoLite2-City.mmdb** (about 70 MB), and place it somewhere readable from the
backend container.

### 2. Mount the file and flip the flag

Add a volume and two env vars to the `backend` service in `docker-compose.yml`:

```yaml
  backend:
    # ... existing config ...
    environment:
      # ... existing env ...
      APP_GEO_ENABLED: "true"
      APP_GEO_DB_PATH: "/data/GeoLite2-City.mmdb"
    volumes:
      - ./docker/geo/GeoLite2-City.mmdb:/data/GeoLite2-City.mmdb:ro
```

Then redeploy:

```bash
docker compose up -d --build backend
```

Verify a redirect produces a click row with country/city populated (replace
`<code>` with one of your short codes):

```bash
curl http://localhost:8080/<code>
docker exec memcyco-postgres psql -U memcyco -d memcyco \
  -c "SELECT data FROM clicks ORDER BY id DESC LIMIT 1;"
```

The lookup happens synchronously on the redirect hot path but uses an in-memory
mmdb (microseconds), well within budget. See the load-bearing Javadoc on
`GeoEnricher`.
