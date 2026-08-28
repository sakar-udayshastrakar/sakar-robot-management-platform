# backend/

**Status: PHASE 1 — FOUNDATION IMPLEMENTED.** Java 21 / Spring Boot 4 modular monolith for the Sakar Cloud backend. See the root `FINAL_REPORT` in the PR/commit description (or ask the session that built this) for the full phase-1 status; the summary below is the durable reference.

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
- Docker Compose dev environment (Postgres, Redis, Mosquitto, backend).

## What's explicitly NOT implemented yet (see the phase-1 final report for the full list)

Command dispatch/signing, task/cleaning orchestration, telemetry ingestion, alerting, analytics business logic, Sakar Robot Agent integration, and anything requiring physical C40 access (remote lock/unlock remains software-scaffolding only, per Part 11/38).
