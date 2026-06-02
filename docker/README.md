# memcyco — Docker

This stack is designed to coexist with other Docker projects already running
on the host, in particular a pre-existing `noca-docker` Compose stack that
ships a Redis container.

## What this project manages vs. reuses

| Service     | Source                                         |
|-------------|------------------------------------------------|
| `postgres`  | **Owned** by this project (always started).    |
| `redis`     | **Reused** from the host's `noca-docker` stack by default. Project-owned only under the `full` profile. |
| `backend`   | **Owned**, built from `backend/Dockerfile`.    |
| `frontend`  | **Owned**, built from `frontend/Dockerfile`.   |

## Run modes

### Default — reuse host Redis (recommended on this workstation)

```powershell
docker compose up -d
```

Starts `postgres`, `backend`, `frontend`. The backend attaches to the
external `noca-docker_default` network and resolves Redis via its DNS
alias `redis` on that network. **Requires** the `noca-docker` stack
(specifically `noca-docker-redis-1`) to be running.

Backend wiring in this mode:
- `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/memcyco`
- `SPRING_DATA_REDIS_HOST=redis`
- `SPRING_DATA_REDIS_PORT=6379`

### Full — everything in this compose

```powershell
docker compose --profile full up -d
```

Adds a project-owned `memcyco-redis` container on the project's internal
network. Use this on hosts that do not have the `noca-docker` stack.

Backend wiring is identical (`SPRING_DATA_REDIS_HOST=redis`) — the alias
resolves to the project-local Redis instead.

### Override Redis target without editing compose

```powershell
$env:SPRING_DATA_REDIS_HOST = "host.docker.internal"
docker compose up -d
```

Useful if Redis runs natively on the host on port 6379, or in a third
container that publishes 6379 to the host.

## Local backend dev (running Spring Boot from your IDE)

When the backend runs on the host (not in compose), point at the
host-published ports:
- `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/memcyco`
- `SPRING_DATA_REDIS_HOST=localhost` (only works in `--profile full` mode,
  because the noca-docker Redis does not publish 6379 to the host)

If only the noca-docker Redis is available and you need host access,
either start the `full` profile alongside (skip your backend service in
compose) or run a one-shot `docker run -p 6379:6379 redis:7-alpine`.

## Teardown

```powershell
# Stop containers, keep the postgres volume:
docker compose down

# Stop containers AND drop the memcyco_pgdata volume:
docker compose down -v

# Including the optional Redis service:
docker compose --profile full down -v
```

This will **not** touch the `noca-docker-redis-1` container or its network
— that lifecycle belongs to the noca-docker project.

## Host probe summary (captured at scaffold time)

- `noca-docker-redis-1` running (`redis:7-alpine`), exposed only on the
  `noca-docker_default` Docker network — **not** published to host:6379.
- `noca-docker-kafka-1` running on host port 29092 (irrelevant to us).
- Host port 5432: free → `memcyco-postgres` claims it.
- Host port 6379: free on host but our backend talks to the noca Redis
  via the internal Docker network, not the host.
