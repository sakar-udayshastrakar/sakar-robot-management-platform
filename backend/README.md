# backend/

**Status: PHASE 1 — FOUNDATION IMPLEMENTED**, superseded in practice by Phase 3 (MQTT), Phase 6 (Users/Roles/Tasks/Alerts/Cleaning/Commands), and Phase 7 (Robot Agent Command Loop) — **this file was not kept current through those phases; the root `README.md`'s "Implementation Status" table is the authoritative, current source, not this banner.** Java 21 / Spring Boot 4 modular monolith for the Sakar Cloud backend. The summary below (Phase 1 scope) is still accurate as far as it goes, it is just incomplete.

Specification sources (do not deviate from these without an approved change to the master requirements):
- `docs/requirements/SAKAR_ROBOT_PLATFORM_MASTER_REQUIREMENTS.md` — Part 8 (Sakar Cloud), Part 13 (Database), Part 14 (APIs), Part 19 (RBAC), Part 20 (Robot Command Security), Part 40 (live Keenon API evidence), §6.A (Robot Capability Abstraction)
- `docs/architecture/SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md` — logical services, Robot Adapter Layer (§8), current-vs-target integration path (§7)
- `docs/architecture/SAKAR_ROBOT_PLATFORM_DATABASE.md` — schema this backend implements
- `docs/api/SAKAR_ROBOT_PLATFORM_API_SPEC.md` — REST endpoint contracts, vendor-neutrality rule
- `docs/security/SAKAR_SECURITY_REQUIREMENTS.md` — every control this backend must enforce before any Security Acceptance Gate (master document Part 35) can be signed off

## What's implemented (Phase 1)

- Spring Boot 4.1.0 / Java 21 modular monolith, package-per-module under `com.sakarrobotics.cloud`.
- Full Part 13 database schema via Flyway (`src/main/resources/db/migration`) — every table, including modules with entity/repository-only scope in this phase.
- Authentication: BCrypt, JWT access tokens, rotating/revocable refresh tokens, Redis-backed login rate limiting, account lockout.
- RBAC: the authoritative Part 19 role/permission matrix, method-level `@PreAuthorize` enforcement.
- Organization hierarchy (Sakar root → distributor → sub-distributor → client → direct client) with materialized-path tenant scoping (`TenantAccessGuard`), enforced on every robot/org/site lookup.
- Robot registry: manufacturer/model/capability/robot/credential entities, capability-gated API (`UNSUPPORTED_CAPABILITY`).
- Robot Adapter abstraction with a functional `KeenonRobotAdapter` (the current, live-tested, **KEENON-CLOUD DEPENDENT** integration) and a stub `SakarRobotAdapter` (local MQTT path, not yet wired).
- Audit logging, vendor-neutral error model, OpenAPI/Swagger, WebSocket auth scaffold, MQTT connection scaffold (disabled by default).
- Docker Compose dev environment (Redis, Mosquitto, backend) — PostgreSQL is native, see below.

## Local development database setup

PostgreSQL is **not** part of the Docker dev stack. This project's local database runs as
a **native Windows PostgreSQL installation**:

- Host: `localhost`
- Port: `5432`
- Database: `sakar_robot_platform`
- Role: `sakar` (local dev password only, never used in a real deployment)

Point the backend at it via environment variables (never hardcoded into `application.yml`,
which must stay portable across machines):

```bash
export SAKAR_DB_URL="jdbc:postgresql://localhost:5432/sakar_robot_platform"
export SAKAR_DB_USERNAME="sakar"
export SAKAR_DB_PASSWORD="sakar"
```

Flyway runs its normal migration set against this database on startup, same as any
environment. The fully-containerized `docker-compose.yml` `backend` service (an
alternative to running the jar directly on the host) reaches this same native database
from inside its container via `host.docker.internal:5432`.

**Docker PostgreSQL is no longer used for this project** — an earlier iteration of this
setup ran Postgres as a Docker container, but that has been fully migrated away from and
the container/volume removed, specifically to avoid running two separate PostgreSQL
instances on one machine. Do not reintroduce a Dockerized Postgres service without
updating this section.

**Production is a separate decision.** Nothing here implies the production database is
also native PostgreSQL running this same way — that's an infrastructure choice made
independently, not something to assume from this local dev setup.

## What's explicitly NOT implemented yet (see the phase-1 final report for the full list, and the root README's "Current Project Status" for what's changed since)

**Status note (documentation sync, 2026-09-22):** command dispatch, task/cleaning orchestration, telemetry ingestion, and alerting — listed below as of Phase 1 — are now implemented (Phases 3/6/7 onward; see root `README.md`). What remains genuinely not implemented: analytics business logic (frontend routes to a stub, no backend API), the Sakar Robot Agent's real on-robot actuation for most command types (`SakarRobotAdapter` is a 100% stub; the Android agent's own SDK-backed executors are gated behind `OperatingMode.HARDWARE_TEST`, never enabled in shipped code), and anything requiring physical C40 access — remote lock/unlock remains software-scaffolding only, per Master Requirements Part 11/38, with no physical validation performed to date.
