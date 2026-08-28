# Sakar Robot Management Platform

**This is the single project workspace for the Sakar Robotics Robot Management Platform. All new source code, infrastructure, tests, and documentation for this platform must live under this root (`D:\Sakar Robotics Projects\sakar robotics web`).**

## CURRENT PHASE

**PHASE 1 — BACKEND FOUNDATION COMPLETE.**

The Sakar Cloud backend's Phase 1 foundation (Java 21 / Spring Boot 4, modular monolith) has been implemented, tested, and merged. This covers authentication, RBAC, organization/site hierarchy, the vendor-neutral robot registry and capability model, the Robot Adapter abstraction, a functional Keenon Cloud integration adapter, the full Part 13 database schema, audit logging, and OpenAPI documentation. It does **not** cover robot communication (MQTT/`SakarC40Agent`), command dispatch, task/cleaning orchestration, telemetry ingestion, alerting, analytics, or any physical robot validation — see **Implementation Status**, **Robot Integration Status**, and **Known Limitations** below before assuming otherwise.

## Project Status

| Field | Value |
|---|---|
| Current phase | Phase 1 — Backend Foundation Complete |
| Overall status | Backend foundation implemented and merged to `dev`; web, mobile, robot-agent integration, and physical validation not started |
| Last updated | 2026-08-29 |
| Latest branch | `dev` (Phase 1 work developed on `backend/phase-1-foundation`, merged via PR #4) |
| Latest commit | `5b4a3a0` — Merge pull request #4 from `sakar-udayshastrakar/backend/phase-1-foundation` (merges `50f6377` — "feat: implement phase 1 backend foundation") |
| Test status | **32 / 32 automated backend tests passing** (`./mvnw clean test`) |
| Build status | **Maven build and package successful** (`./mvnw clean test` and `./mvnw package` both verified against the current `dev` tree) |
| Secrets scan | Clean — no client secrets, access tokens, passwords, API keys, or private keys found in source |

This table reflects the actual repository state as of the commands above being run against `dev`, not a copy-forward of an earlier report — re-run `git branch --show-current`, `git log -1 --oneline`, and `./mvnw clean test` before trusting it if time has passed.

## Project Purpose

Sakar Robotics is building a platform so that Sakar — not the robot vendor (Keenon) — is the primary system of record for robot data, telemetry, history, authentication, authorization, commands, lock/unlock, users, configuration, analytics, logs, alerts, and fleet management, starting with the first product, **Sakar CleanBot 5000 Plus**, built on the Keenon C40 / C40 S hardware platform. The full rationale, architecture, and security model are specified in `docs/requirements/SAKAR_ROBOT_PLATFORM_MASTER_REQUIREMENTS.md` (the authoritative requirements reference this README is checked against). See [`docs/architecture/SAKAR_ROBOT_PLATFORM_NAMING_AND_MODEL_STRATEGY.md`](docs/architecture/SAKAR_ROBOT_PLATFORM_NAMING_AND_MODEL_STRATEGY.md) for the full product/platform/agent naming strategy.

## Robot Integration Status

**Current (implemented and tested):**
```
Sakar Backend  →  Keenon Robot Adapter  →  Keenon Cloud (https://cloud.robotkeenon.com)  →  Robot
```
The `KeenonRobotAdapter` (`backend/src/main/java/com/sakarrobotics/cloud/integration/keenon/`) implements the live-tested read/control operations (status, battery, telemetry, areas, temporary task, pause, recharge) against Keenon's Open Platform, gated by the robot capability model so unsupported operations return `UNSUPPORTED_CAPABILITY` rather than being attempted. This path is **KEENON-CLOUD DEPENDENT** by design and is explicitly not the target architecture — see Master Requirements Part 10/40.

**Target (not yet implemented):**
```
Web / Mobile  →  Sakar Cloud  →  MQTT  →  SakarC40Agent  →  Peanut SDK  →  Robot
```
- `SakarC40Agent` integration: **NOT IMPLEMENTED.** The `SakarRobotAdapter` exists as a stub only — every method throws `FEATURE_NOT_YET_IMPLEMENTED`. No MQTT telemetry, command, or heartbeat channel to the agent exists yet.
- Physical C40 validation: **NOT PERFORMED.** No physical robot has been connected to, or controlled by, any code in this repository.
- Remote lock/unlock: **NOT PHYSICALLY CERTIFIED.** Software scaffolding only (permission model, `robot_locks` schema) — the ten physical validation conditions in Master Requirements Part 11/38 have not been run. Do not represent lock/unlock as production-ready anywhere derived from this README.
- Keenon Cloud independence: **NOT ACHIEVED.** The current integration requires Keenon Cloud; the core control loop does not yet function without it.

## Project Structure

```
sakar robotics web/
├── docs/                     Approved documentation (copies; see docs/README.md for the full index)
│   ├── requirements/         Master requirements, companion requirements/roadmap docs, live API evidence
│   ├── security/             Standalone security requirements + risk register
│   ├── architecture/         System architecture + database specification
│   └── api/                  REST API specification
├── backend/                  Sakar Cloud backend (Java 21 + Spring Boot 4) — PHASE 1 FOUNDATION IMPLEMENTED
├── web/                      Sakar web application (React + TypeScript) — not yet implemented
├── mobile/                   Sakar mobile application (Flutter) — not yet implemented
├── robot/
│   └── SakarC40Agent/        Robot-resident Android agent — existing project, unmodified by Phase 1
├── database/                 Placeholder — the actual schema/migrations live in backend/src/main/resources/db/migration (Flyway)
├── infrastructure/           Docker/Nginx/deployment config — placeholder; backend/docker-compose.yml is the current dev environment
├── security/                 Security tooling/policy-as-code (distinct from docs/security/) — not yet implemented
├── tests/                    Placeholder — the actual automated tests live in backend/src/test/java (32 tests, see Testing Status)
└── README.md                 This file
```

## Applications

| Application | Location | Status |
|---|---|---|
| Sakar Cloud (backend) | `backend/` | **Phase 1 foundation implemented** — see Implementation Status |
| Web application | `web/` | Not yet implemented |
| Mobile application | `mobile/` | Not yet implemented |
| Robot Android tablet agent — Sakar Robot Agent (currently `SakarC40Agent`) | `robot/SakarC40Agent/` | Existing project, unmodified; not yet extended with telemetry-forwarding/command-reception, and not yet integrated with the Phase 1 backend |

**Core architecture rule:** the web and mobile applications communicate with Sakar Cloud only. Neither ever connects to a robot directly. The backend enforces this today for every implemented endpoint (Part 16); it cannot yet be exercised end-to-end because no web/mobile client or robot-agent connection exists yet.

## Implementation Status

Backend module status as implemented in `backend/src/main/java/com/sakarrobotics/cloud/`. **Foundation-only** means the schema/entity and, where noted, the read path exist, but full business workflows (dispatch, orchestration, ingestion) do not.

| Module | Status | Notes |
|---|---|---|
| Authentication (JWT, refresh-token rotation, login) | ✅ Complete | `auth/` — BCrypt, JWT access tokens, rotating/revocable refresh tokens, Redis-backed rate limiting, account lockout |
| RBAC | ✅ Complete | `iam/` — Part 19 role/permission matrix seeded via Flyway, `@PreAuthorize` enforced on every endpoint |
| Organization hierarchy | ✅ Complete | `org/` — Sakar root → distributor → sub-distributor → client → direct client, materialized-path tenant scoping |
| Sites | ✅ Complete | `org/Site*` — CRUD + organization-scoped authorization |
| Robot Registry | ✅ Complete | `robot/registry/` — register/activate/deactivate, tenant-scoped listing |
| Robot Models | ✅ Complete | `robot/registry/RobotModel*`, `RobotManufacturer*` |
| Robot Capabilities | ✅ Complete | `robot/registry/RobotCapability*` — per-model capability flags, `UNSUPPORTED_CAPABILITY` enforcement |
| Robot Adapter (abstraction) | ✅ Complete | `robot/adapter/` — interface + registry; `KeenonRobotAdapter` functional, `SakarRobotAdapter` intentionally a stub |
| Keenon Integration Foundation | ✅ Complete (read/control path) | `integration/keenon/` — OAuth token caching, area-id sync mapping, webhook intake with idempotency. **KEENON-CLOUD DEPENDENT**, not a substitute for the local path |
| PostgreSQL | ✅ Complete (schema) | Full Part 13 schema via Flyway; production data-volume/performance not yet exercised |
| Flyway | ✅ Complete | 10 versioned migrations, `V1`–`V10` |
| Redis | ✅ Complete (foundation) | Login rate limiting and Keenon OAuth token caching implemented; not yet used for caching/pub-sub beyond that |
| Audit | ✅ Complete (foundation) | `audit/` — append-only `audit_logs`, recorded on login/logout; DB-role-level tamper hardening (`REVOKE UPDATE/DELETE`) not yet applied |
| OpenAPI / Swagger | ✅ Complete | `config/OpenApiConfig.java`, served at `/swagger-ui.html` |
| WebSocket Foundation | ✅ Complete (foundation) | `websocket/` — STOMP auth on CONNECT, org-scoped SUBSCRIBE authorization; no controller publishes real events yet (no telemetry/task pipeline to publish from) |
| Docker (dev environment) | ✅ Complete | `backend/docker-compose.yml` — Postgres, Redis, Mosquitto, backend; **not** a production topology |
| MQTT (robot communication) | 🔄 In Progress (connection scaffold only) | `mqtt/` — client lifecycle + config exist, disabled by default; no telemetry/command traffic implemented |
| Telemetry ingestion | ⏳ Planned | `telemetry/` entities/repositories exist; no ingestion pipeline |
| SRELS (events/errors/app logs) | ⏳ Planned | `srels/` entities/repositories exist; nothing writes to them yet |
| Command signing/dispatch | ⏳ Planned | `command/` entities + lifecycle enum exist; no signing, no MQTT dispatch |
| Robot lock/unlock (software) | ⏳ Planned | `lock/` schema only; no endpoint exists to trigger it |
| Task / cleaning orchestration | ⏳ Planned | `task/`, `cleaning/` entities exist; no orchestration logic |
| Alerting | ⏳ Planned | `alert/` entity/repository only |
| Analytics | ⏳ Planned | No code exists yet |
| SakarC40Agent integration | ⏳ Planned | Not started — see Robot Integration Status |
| Physical C40 validation | ⚠️ Requires Physical Test | Not performed; gated by Master Requirements Part 38 |
| Remote lock/unlock certification | ⚠️ Requires Physical Test | Not performed; gated by Master Requirements Part 11/38 |

## Database Status

The PostgreSQL + Flyway foundation is implemented: 10 migrations (`backend/src/main/resources/db/migration/V1__core_and_iam.sql` through `V10__seed_reference_data.sql`) create the full schema and seed the RBAC matrix plus Keenon C40 S / Sakar CleanBot 5000 Plus reference data. Current tables:

`organizations`, `sites`, `roles`, `permissions`, `role_permissions`, `users`, `refresh_tokens`, `robot_manufacturers`, `robot_models`, `robot_capabilities`, `robots`, `robot_credentials`, `robot_status`, `robot_telemetry`, `robot_events`, `robot_errors`, `robot_alerts`, `application_logs`, `robot_commands`, `command_results`, `robot_locks`, `robot_tasks`, `task_events`, `cleaning_sessions`, `charging_sessions`, `maps`, `map_points`, `notifications`, `audit_logs`, `keenon_area_mappings`, `vendor_webhook_events`.

**Schema existing does not mean the corresponding workflow is complete.** Tables such as `robot_telemetry`, `robot_events`, `robot_commands`, `robot_tasks`, `cleaning_sessions`, and `robot_locks` have no application code populating them yet — see Implementation Status above for which modules are foundation-only.

## Testing Status

**32 / 32 automated backend tests passing** (`cd backend && ./mvnw clean test`). Coverage includes: login/lockout/rate-limiting, refresh-token rotation and reuse detection, RBAC enforcement, tenant isolation (IDOR/BOLA — cross-org robot access returns `404`, not `403`), privilege-escalation boundaries on organization creation, organization-hierarchy path logic, robot capability gating, Keenon adapter response mapping (including the `610000`-accepted-vs-rejected distinction), webhook idempotency, audit-log writes, and JWT expiry/tamper handling.

Build/package: `./mvnw clean test` and `./mvnw package -DskipTests` both succeed against the current `dev` tree, producing `backend/target/sakar-cloud-backend-0.0.1-SNAPSHOT.jar`.

No coverage percentage is reported here because none has been measured (no coverage tool is wired into the build) — do not infer one.

## Security Status

Implemented and verified by the test suite above:

| Control | Status |
|---|---|
| Password hashing (BCrypt) | ✅ Complete |
| JWT access tokens | ✅ Complete |
| Refresh-token handling (rotation, reuse detection, revocation) | ✅ Complete |
| RBAC (method-level `@PreAuthorize`) | ✅ Complete |
| Tenant authorization (organization-hierarchy scoping) | ✅ Complete |
| IDOR/BOLA protection | ✅ Complete for robot lookups (tested); not yet exercised for every future resource type |
| Audit foundation | ✅ Complete (foundation) — append-only writes; DB-role `REVOKE UPDATE/DELETE` hardening not applied |
| Login rate limiting | ✅ Complete (Redis-backed) |
| Account lockout | ✅ Complete |
| Secret protection | ✅ Complete for what exists — Keenon credentials env-var-only, never in source; secrets-manager integration for production deployment not yet built |
| WebSocket authorization | ✅ Complete (foundation) — CONNECT auth + org-scoped SUBSCRIBE; no real event traffic to further validate against yet |

Explicitly incomplete: command signing/replay protection (no commands are dispatched yet), Android/agent-side security (Part 21 — `SakarC40Agent` untouched), MQTT broker security (TLS, per-robot credentials, topic ACLs — scaffold only, not enforced against a real broker), DB-role-level audit tamper hardening, and every physical-robot-dependent control in Part 11/38.

## Security

`docs/security/SAKAR_SECURITY_REQUIREMENTS.md` remains the authoritative control specification. The table above documents what Phase 1 actually implements against it — this repository is no longer purely "specified, not implemented" for the backend, but most of Parts 16–29 still describe target state beyond what Phase 1 covers (network/MQTT/WebSocket hardening against a real deployment, Android/agent security, backup/DR, monitoring, security testing). The platform's highest-risk feature — remote motor lock/unlock — remains **not production-ready**; the software scaffolding described above changes nothing about the physical validation requirement (Part 11/38).

## Documentation

All approved specifications live under `docs/` — see `docs/README.md` for the full index. `docs/requirements/SAKAR_ROBOT_PLATFORM_MASTER_REQUIREMENTS.md` is the authoritative requirements reference this README is checked against; where this README and that document diverge, the master requirements document governs and this README should be corrected.

## External Peanut SDK

The Keenon **Peanut SDK** (`peanut-sdk-v1.3.0`, including the vendor's sample app and the decompiled technical study material) is an **external, read-only vendor dependency** that lives at `D:\Sakar Robotics Projects\peanut-sdk-v1.3.0`, outside this project workspace. It has not been moved, copied, or modified, and Phase 1 backend work did not touch it. `robot/SakarC40Agent/sdk/libs/peanut-sdk-release.aar` is the pre-existing vendored binary that `SakarC40Agent` already depends on, unmodified, and excluded from Git tracking (see `.gitignore`).

## Roadmap

**Next development step: Story 3 — Implement SakarC40Agent Communication Layer.**

This means building the MQTT-based telemetry/command channel between Sakar Cloud and `SakarC40Agent` (Master Requirements roadmap Phase 2), replacing the `SakarRobotAdapter` stub with a working local-path adapter, and wiring `robot_status`/`robot_telemetry` ingestion. Command signing/dispatch, task/cleaning orchestration, alerting, and analytics follow per the master roadmap; physical C40 validation and remote lock/unlock certification remain separately gated (Part 38) and are not unblocked by this story.

## Team Ownership

**Uday:**
- Robot platform
- Robot adapter
- Keenon integration
- Robot tasks/cleaning
- Robot commands

**Riyaz:**
- Backend core
- Authentication
- RBAC
- Organization
- Site
- API security

**Rohit:**
- Database
- Telemetry
- SRELS
- Audit
- Testing
- Infrastructure

## Development Rules

- **`README.md` must be updated whenever a major feature/story/phase changes project status.** A stale README is treated as a defect — verify actual repository state (`git branch --show-current`, `git log -1 --oneline`, `git status --short`, the test suite) before editing it, rather than copying forward prior claims.
- Do not mark a capability `✅ Complete` unless the code implementing it exists, compiles, and is covered by a passing test. Use `🔄 In Progress`, `⏳ Planned`, or `⚠️ Requires Physical Test`/`Requires Vendor Support` otherwise.
- Never claim physical robot control, production MQTT, production lock/unlock, complete `SakarC40Agent` integration, or Keenon Cloud independence unless actually implemented and verified — see Master Requirements Part 40's "API accepted ≠ robot executed ≠ verified" rule, which applies equally to claims made in this README.

## Legacy documents at the project-collection level

`D:\Sakar Robotics Projects\` (one level above this workspace) also contains `Sakar_Robotics_SRS.docx`/`.pdf`, a pre-existing document that predates this workspace. It was left in place, untouched, and is not part of this workspace's `docs/` index.

---

**Current authorized scope:** backend Phase 1 is complete; the next authorized step is Story 3 (`SakarC40Agent` communication layer / MQTT), per the roadmap above. Physical C40 connection, physical robot control, and remote lock/unlock certification remain out of scope until the separate physical-validation go-ahead described in Master Requirements Part 33/38 is given.
