# Sakar Robot Management Platform

**This is the single project workspace for the Sakar Robotics Robot Management Platform. All new source code, infrastructure, tests, and documentation for this platform must live under this root (`D:\Sakar Robotics Projects\sakar robotics web`).**

## CURRENT PHASE

**PHASE 6 — FLEET OPERATIONS BACKEND AND FRONTEND INTEGRATION COMPLETE (Users/Roles, Tasks, Alerts, Cleaning history, Remote Commands non-lock) (branch `feature/phase6-fleet-operations`, uncommitted — pending review/approval).**

The backend has real, tested REST endpoints for user/role administration, robot task orchestration, alert generation/acknowledgement, cleaning-session history, and non-lock remote commands — see Implementation Status below for exactly which modules moved from foundation-only to implemented, and `docs/requirements/SAKAR_PLATFORM_KEENON_PARITY_REQUIREMENTS.md` for three **proposed, unapproved, not-implemented** product-expansion modules (Open Platform, OTA/Deployment, IoT/Elevator) scoped separately per an explicit decision not to build them from Keenon Cloud reference screenshots alone.

The Sakar Web Application (`web/`, React + TypeScript) now consumes all six of those Phase 6 endpoints for real: Users, Roles, Tasks (including per-robot lifecycle control), Alerts (acknowledge/resolve), Cleaning history, and non-lock robot Commands (a new Robot Detail tab), on top of the pre-existing real wiring for authentication, organizations, sites, robots, and audit logs. `web/src/mocks/simulated.ts` had its `generateAlerts`/`generateTasks`/`generateUsers`/`generateRoles` generators removed entirely (superseded by real API calls) — Telemetry, Events, Errors, Application Logs, and the Robot Detail Timeline tab remain simulated, clearly bannered, because no backend REST endpoint exists for any of them yet. **Full live-browser verification against a real running backend was not performed this pass** (no Docker/Postgres/Redis available in this environment) — verification here is TypeScript compilation, a full production build, the existing 21 frontend tests, 7 new mocked-API render/interaction tests against the real DTO shapes (Users/Alerts), and a careful manual cross-check of every new frontend type/API call against the actual backend controller/DTO source. A prior UI/UX pass restyled the whole application as a professional enterprise fleet-management console with no change to backend logic, RBAC, or authentication. See `docs/architecture/SAKAR_WEB_APPLICATION_ARCHITECTURE.md`, `docs/architecture/SAKAR_WEB_UI_DESIGN_SYSTEM.md`, `docs/requirements/SAKAR_PHASE_4_WEB_IMPLEMENTATION_REPORT.md`, and `docs/requirements/SAKAR_WEB_UI_UX_REQUIREMENTS.md` for the full prior account, including an earlier live browser verification against a real running instance of the backend.

Separately, Phase 3's own MQTT pipeline was validated against a real (locally-hosted) MQTT broker after this README was last written — see `docs/requirements/SAKAR_PHASE_3_LIVE_MQTT_VALIDATION_REPORT.md`: MQTT connection/auth/TLS/ACL, heartbeat, telemetry, dedup, replay, cross-tenant isolation, rate limiting, and reconnect all passed against a real broker with the real, unmodified backend and agent code — still **using a test agent, not a physical CleanBot**, and still uncommitted.

## Project Status

| Field | Value |
|---|---|
| Current phase | Phase 6 — Fleet operations backend AND frontend integration (Users/Roles, Tasks, Alerts, Cleaning, Remote Commands non-lock) implemented, uncommitted |
| Overall status | Backend + Agent MQTT pipeline implemented, live-broker-validated, test-verified; Phase 6 approved-scope backend work implemented and test-verified; Web Application now consumes every Phase 6 endpoint for real (Users/Roles/Tasks/Alerts/Cleaning/Commands), plus the pre-existing real auth/org/site/robot/audit wiring; three product-expansion modules (Open Platform/OTA/IoT-Elevator) remain requirements-only, not implemented; not yet committed/merged; mobile, physical robot validation, and live-browser verification of this frontend pass not performed (no local Postgres/Redis/Docker available) |
| Last updated | 2026-08-30 |
| Latest branch | `feature/phase6-fleet-operations` (created off `dev`; working tree modified, not committed — see `git status`/`git diff` before trusting "current" claims) |
| Latest commit | `5b4a3a0` — Merge pull request #4 from `sakar-udayshastrakar/backend/phase-1-foundation` (Phase 1; Phase 3 and Phase 6 work sit on top, uncommitted) |
| Test status | **93 / 93 automated backend tests passing** (`cd backend && ./mvnw clean test`) — 71 pre-existing (32 foundation + 27 Phase 3 + 12 Phase 3 Security Hardening) + 22 Phase 6 backend tests (Users/Roles, Tasks, Alerts, Cleaning, Commands). **28 / 28 automated frontend tests passing** (`cd web && npm run test -- --run`) — 21 pre-existing + 7 new (real Users/Alerts API contract render + interaction tests). **18 / 18 automated Robot Agent (`:api` module) tests passing** (`cd robot/SakarC40Agent && ./gradlew :api:test`) |
| Build status | Backend: **Maven build and package successful**. Frontend: **`npm run build` (tsc -b && vite build) successful**; `npm run lint` (oxlint) clean (only pre-existing warnings, none in files touched this pass). Robot Agent: **`./gradlew assembleDebug test` BUILD SUCCESSFUL** (debug APK assembles with the new `:api` module wired in) |
| Secrets scan | Clean — no client secrets, access tokens, passwords, API keys, or private keys found in source; new `secrets.properties.example` keys (Phase 3) are blank placeholders only |

This table reflects the actual repository state as of the commands above being run, not a copy-forward of an earlier report — re-run `git branch --show-current`, `git status --short`, `./mvnw clean test`, and `./gradlew :api:test` before trusting it if time has passed.

## Project Purpose

Sakar Robotics is building a platform so that Sakar — not the robot vendor (Keenon) — is the primary system of record for robot data, telemetry, history, authentication, authorization, commands, lock/unlock, users, configuration, analytics, logs, alerts, and fleet management, starting with the first product, **Sakar CleanBot 5000 Plus**, built on the Keenon C40 / C40 S hardware platform. The full rationale, architecture, and security model are specified in `docs/requirements/SAKAR_ROBOT_PLATFORM_MASTER_REQUIREMENTS.md` (the authoritative requirements reference this README is checked against). See [`docs/architecture/SAKAR_ROBOT_PLATFORM_NAMING_AND_MODEL_STRATEGY.md`](docs/architecture/SAKAR_ROBOT_PLATFORM_NAMING_AND_MODEL_STRATEGY.md) for the full product/platform/agent naming strategy.

## Robot Integration Status

**Path 1 — current, live-tested, Keenon-dependent:**
```
Sakar Backend  →  Keenon Robot Adapter  →  Keenon Cloud (https://cloud.robotkeenon.com)  →  Robot
```
The `KeenonRobotAdapter` (`backend/src/main/java/com/sakarrobotics/cloud/integration/keenon/`) implements the live-tested read/control operations (status, battery, telemetry, areas, temporary task, pause, recharge) against Keenon's Open Platform, gated by the robot capability model so unsupported operations return `UNSUPPORTED_CAPABILITY` rather than being attempted. This path is **KEENON-CLOUD DEPENDENT** by design and is explicitly not the target architecture — see Master Requirements Part 10/40.

**Path 2 — target architecture, MQTT telemetry now implemented in software (Phase 3):**
```
SakarC40Agent  →  MQTT  →  Sakar Cloud  →  PostgreSQL  →  WebSocket
```
Presence/heartbeat/telemetry/events/errors flow agent → cloud → database over MQTT, with idempotent ingestion, robot/tenant identity verification, and real-time WebSocket publish — see `docs/architecture/SAKAR_MQTT_ARCHITECTURE.md`. **This is a software pipeline verified by automated tests only — no MQTT broker or physical robot was run against it in this session.** The `SakarRobotAdapter` (REST/adapter-based control path, distinct from this MQTT telemetry path) is still a stub — every method throws `FEATURE_NOT_YET_IMPLEMENTED`; command dispatch and lock/unlock are not implemented on either path.

- Physical C40 validation: **NOT PERFORMED.** No physical robot has been connected to, or controlled by, any code in this repository.
- Remote lock/unlock: **NOT PHYSICALLY CERTIFIED, NOT IMPLEMENTED IN SOFTWARE EITHER.** No lock/unlock code exists in the backend, MQTT layer, or agent — the ten physical validation conditions in Master Requirements Part 11/38 remain entirely unaddressed. Do not represent lock/unlock as production-ready anywhere derived from this README.
- Keenon Cloud independence: **NOT ACHIEVED.** Path 1 still requires Keenon Cloud. Path 2 removes that dependency for telemetry, but has not been proven against a real robot.

## Project Structure

```
sakar robotics web/
├── docs/                     Approved documentation (copies; see docs/README.md for the full index)
│   ├── requirements/         Master requirements, companion requirements/roadmap docs, live API evidence
│   ├── security/             Standalone security requirements + risk register
│   ├── architecture/         System architecture + database specification
│   └── api/                  REST API specification
├── backend/                  Sakar Cloud backend (Java 21 + Spring Boot 4) — PHASE 1 FOUNDATION IMPLEMENTED, Phase 3 MQTT, live-broker-validated
├── web/                      Sakar web application (React + TypeScript) — PHASE 4 IMPLEMENTED against the real backend
├── mobile/                   Sakar mobile application (Flutter) — not yet implemented
├── robot/
│   └── SakarC40Agent/        Robot-resident Android agent — existing project, unmodified by Phase 1
├── database/                 Placeholder — the actual schema/migrations live in backend/src/main/resources/db/migration (Flyway)
├── infrastructure/           Docker/Nginx/deployment config — placeholder; backend/docker-compose.yml is the current dev environment
├── security/                 Security tooling/policy-as-code (distinct from docs/security/) — not yet implemented
├── tests/                    Placeholder — the actual automated tests live in backend/src/test/java (93 tests, see Testing Status)
└── README.md                 This file
```

## Applications

| Application | Location | Status |
|---|---|---|
| Sakar Cloud (backend) | `backend/` | **Phase 1 foundation implemented** — see Implementation Status |
| Web application | `web/` | **Phase 4 + Phase 6 implemented** — React + TypeScript admin UI against the real backend for auth/organizations/sites/robots/audit, and (Phase 6) Users/Roles/Tasks/Alerts/Cleaning history/non-lock Commands; simulated data (clearly labeled) only for telemetry/events/errors/application logs/timeline, which still have no backend REST endpoint |
| Mobile application | `mobile/` | Not yet implemented |
| Robot Android tablet agent — Sakar Robot Agent (currently `SakarC40Agent`) | `robot/SakarC40Agent/` | Existing diagnostic app **now extended (Phase 3) with an MQTT telemetry/heartbeat link to the Sakar Cloud backend** (`api/` module); command-reception is still not implemented; not yet run against a real broker or physical robot |

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
| WebSocket Foundation | ✅ Complete (now publishing) | `websocket/` — STOMP auth on CONNECT, org-scoped SUBSCRIBE authorization, **plus (Phase 3) `RobotRealtimePublisher` actually publishing status/telemetry updates** |
| Docker (dev environment) | ✅ Complete | `backend/docker-compose.yml` — Postgres, Redis, Mosquitto, backend; **not** a production topology |
| MQTT (robot communication) | ✅ Complete (software, Phase 3) | `mqtt/` — full subscribe/ingest/ack pipeline (`MqttSubscriptionManager`, `MqttInboundListener`, `MqttInboundMessageService`); disabled by default (`SAKAR_MQTT_ENABLED`); **not run against a real broker or physical robot in this session** — broker-side auth/ACL/TLS remain a production requirement, see `docs/architecture/SAKAR_MQTT_ARCHITECTURE.md` §7 |
| Telemetry ingestion | ✅ Complete (software, Phase 3) | `telemetry/TelemetryIngestionService`/`HeartbeatService`/`RobotStatusService` — `robot_telemetry`/`robot_status` now have a real write path, driven by inbound MQTT |
| SRELS (events/errors/app logs) | 🔄 Partially Implemented (Phase 3) | `RobotEventIngestionService`/`RobotErrorIngestionService` write `robot_events`/`robot_errors` from MQTT; `application_logs` now receives MQTT lifecycle/ingestion events (`MqttLifecycleLogger`); no other module writes SRELS yet |
| User / Role administration API | ✅ Complete (Phase 6) | `iam/UserController`, `iam/RoleController`, `iam/UserService` — create/list/get/suspend/activate/change-role, gated by `USER_MANAGE`, org-hierarchy scoped; role list is read-only fixed reference data (`V9__seed_rbac.sql`) |
| Task orchestration | ✅ Complete (Phase 6) | `task/RobotTaskController`/`RobotTaskService` — create/start/pause/resume/stop/cancel with an explicit lifecycle-transition table and full event history; Sakar-side bookkeeping only, does not dispatch to a robot (see Remote Commands below) |
| Alerting | ✅ Complete (Phase 6), two rule types only | `alert/` — real, evidence-driven low-battery (from live telemetry ingest) and offline (from a real `@Scheduled` sweep of `robot_status.last_seen_at`) rules only; idempotent (never duplicates an open alert); every other Master-Requirements alert type (critical error, task failure, emergency, comms loss, lock/unlock) has no generator yet and is not fabricated |
| Cleaning history (read path) | ✅ Complete (Phase 6) | `cleaning/CleaningController` — read-only; the table has exactly one writer, the task-completion hook in `RobotTaskService`, so a row always corresponds to a real completed `CLEANING`-type task, never a fabricated cleaning run |
| Remote Commands (non-lock) | ✅ Complete (Phase 6), delivery/execution unconfirmed | `command/RobotCommandController`/`RobotCommandService` — `START_TASK`/`STOP_TASK`/`PAUSE_TASK`/`RESUME_TASK`/`RETURN_TO_DOCK` only; **`LOCK`/`UNLOCK` are rejected by construction, no code path can issue either**; makes a real best-effort MQTT publish attempt but `SakarC40Agent` has no command-consuming code yet, so `dispatched=true` never implies the robot received or executed the command — the response says so explicitly (`dispatchNote`) |
| Command signing/dispatch (production-grade) | ⏳ Planned | Nonce + expiry exist on `RobotCommand`; no cryptographic signature, no agent-side consumer — the MQTT publish itself is real (Phase 6), but this is still short of the full Phase 6/7 roadmap spec |
| Robot lock/unlock (software) | ⏳ Planned | `lock/` schema only; no endpoint exists to trigger it, and `RobotCommandController` explicitly cannot be used for it (see Remote Commands row above) — still excluded per Master Requirements Part 11/38 |
| Analytics | ⏳ Planned | No code exists yet |
| Open Platform (proposed product expansion) | 📋 Requirements only, NOT implemented | `docs/requirements/SAKAR_PLATFORM_KEENON_PARITY_REQUIREMENTS.md` Module A — no entity, controller, or migration exists |
| OTA / Deployment (proposed product expansion) | 📋 Requirements only, NOT implemented | Same document, Module B — explicitly does not assume Keenon firmware push is possible |
| IoT / Elevator Integration (proposed product expansion) | 📋 Requirements only, NOT implemented | Same document, Module C — elevator protocol is an open question, not assumed |
| SakarC40Agent integration | 🔄 Partially Implemented (Phase 3) | MQTT telemetry/heartbeat/events/errors link now exists (`:api` module) and is wired into `SakarC40Application`; not yet run against a real broker or physical robot — see Robot Integration Status |
| Robot MQTT credential issuance | ✅ Complete (Phase 3) | `RobotCredentialService`, `POST /api/v1/robots/{id}/mqtt-credentials` — `robot_credentials` now populated; broker does not yet enforce it |
| Physical C40 validation | ⚠️ Requires Physical Test | Not performed; gated by Master Requirements Part 38 |
| Remote lock/unlock certification | ⚠️ Requires Physical Test | Not performed; not even software-implemented yet; gated by Master Requirements Part 11/38 |

## Database Status

The PostgreSQL + Flyway foundation is implemented: 11 migrations (`backend/src/main/resources/db/migration/V1__core_and_iam.sql` through `V11__mqtt_inbound_messages.sql`) create the full schema and seed the RBAC matrix plus Keenon C40 S / Sakar CleanBot 5000 Plus reference data. Current tables:

`organizations`, `sites`, `roles`, `permissions`, `role_permissions`, `users`, `refresh_tokens`, `robot_manufacturers`, `robot_models`, `robot_capabilities`, `robots`, `robot_credentials`, `robot_status`, `robot_telemetry`, `robot_events`, `robot_errors`, `robot_alerts`, `application_logs`, `robot_commands`, `command_results`, `robot_locks`, `robot_tasks`, `task_events`, `cleaning_sessions`, `charging_sessions`, `maps`, `map_points`, `notifications`, `audit_logs`, `keenon_area_mappings`, `vendor_webhook_events`, `mqtt_inbound_messages`.

**Schema existing does not mean the corresponding workflow is complete.** As of Phase 3, `robot_status`, `robot_telemetry`, `robot_events`, `robot_errors`, `robot_credentials`, and `mqtt_inbound_messages` have real application code populating them (via the MQTT ingestion pipeline). As of Phase 6, `robot_commands`, `robot_tasks`, `task_events`, `cleaning_sessions`, `robot_alerts`, and `users`/`roles` (write paths, not just seed data) also have real application code populating them. `robot_locks` still has no application code populating it at all — see Implementation Status above.

## Testing Status

**93 / 93 automated backend tests passing** (`cd backend && ./mvnw clean test`) — 71 pre-existing (32 foundation + 27 Phase 3 + 12 Phase 3 Security Hardening) + 22 new Phase 6 tests. Pre-existing coverage includes login/lockout/rate-limiting, refresh-token rotation and reuse detection, RBAC enforcement, tenant isolation, organization-hierarchy logic, robot capability gating, Keenon adapter response mapping, webhook idempotency, audit-log writes, JWT expiry/tamper handling, MQTT topic build/parse, full ingestion-pipeline acceptance/rejection paths, heartbeat/telemetry/event/error/presence ingestion, ACK generation, robot MQTT credential provisioning/rotation, negative-sequence/oversized-payload/rate-limit rejection, credential revocation/audit-trail verification, and WebSocket tenant isolation. New Phase 6 coverage: user creation/org-scoping/duplicate-email/suspend-activate (`UserControllerTest`), role list access (`RoleControllerTest`), task capability-gating/full lifecycle/invalid-transition/tenant-isolation including the cleaning-session side effect (`RobotTaskControllerTest`), alert idempotency/severity/battery-recovery/offline-resolve (`AlertGenerationServiceTest`) and acknowledge/resolve/tenant-scoped listing (`AlertControllerTest`), cleaning-history tenant isolation (`CleaningControllerTest`), and command non-lock-only enforcement/capability-gating/honest not-dispatched reporting (`RobotCommandControllerTest`). See `docs/security/SAKAR_PHASE_3_SECURITY_HARDENING_REPORT.md` for the Phase 3 finding-by-finding account.

**28 / 28 automated frontend tests passing** (`cd web && npm run test -- --run`) — 21 pre-existing (login, protected routes, JWT decode, robots list) + 7 new this pass: `UsersPage.test.tsx` (real GET /users rendering, error state, POST /users create-flow with the real request shape, POST /users/{id}/suspend) and `AlertsPage.test.tsx` (real GET /alerts rendering using the real OPEN/ACKNOWLEDGED/RESOLVED status vocabulary — not the old simulated ACTIVE vocabulary, error state, POST /alerts/{id}/acknowledge). `npm run build` (`tsc -b && vite build`) succeeds; `npm run lint` (oxlint) is clean (only pre-existing warnings in files untouched this pass). **Not performed:** a live browser session against a real running backend (no Docker/Postgres/Redis available in this environment) — see the Current Phase note above.

**18 / 18 automated Robot Agent tests passing** (`cd robot/SakarC40Agent && ./gradlew :api:test`) — topic building, Gson↔Jackson wire-format compatibility, the bounded offline queue, safe no-broker behavior, both schedulers, and (Security Hardening) TLS (`ssl://`) configuration safety. `./gradlew assembleDebug test` (whole agent project) also succeeds.

Build/package: `./mvnw clean test` and `./mvnw package -DskipTests` both succeed, producing `backend/target/sakar-cloud-backend-0.0.1-SNAPSHOT.jar`.

No coverage percentage is reported here because none has been measured (no coverage tool is wired into the build) — do not infer one. **None of the above tests exercise a running MQTT broker or a physical robot** — see `docs/requirements/SAKAR_PHASE_3_IMPLEMENTATION_REPORT.md` §9 "Known Limitations".

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
| Secret protection | ✅ Complete for what exists — Keenon credentials and (Phase 3) MQTT credentials env-var/git-ignored-file-only, never in source; secrets-manager integration for production deployment not yet built |
| WebSocket authorization | ✅ Complete, now with real traffic — CONNECT auth + org-scoped SUBSCRIBE (unchanged); `RobotRealtimePublisher` (Phase 3) is the first real event traffic exercising it |
| MQTT robot identity + tenant verification (software) | ✅ Complete (Phase 3) — `MqttInboundMessageService` cross-checks robot/org/site against the registered `Robot` row independent of transport-level auth |
| MQTT broker-side authentication/ACL/TLS | ❌ Not implemented — dev broker allows anonymous, plaintext connections; **production requirement**, see `docs/architecture/SAKAR_MQTT_ARCHITECTURE.md` §7 |
| MQTT client-side TLS configuration | ✅ Complete (Security Hardening) — `ssl://` supported end-to-end with real CA validation, optional private-CA truststore; never run against a live TLS broker |
| MQTT message idempotency/replay handling | ✅ Complete (Phase 3) — `mqtt_inbound_messages` unique constraint |
| MQTT payload size limit + per-robot rate limiting | ✅ Complete (Security Hardening) — 64 KiB cap enforced before parsing; Redis-backed 120 msg/60s per-robot limit |
| Robot MQTT credential revocation + audit logging | ✅ Complete (Security Hardening) — `DELETE /api/v1/robots/{id}/mqtt-credentials`; provision/rotate/revoke all audit-logged, secret never logged |
| WebSocket tenant-isolation test coverage | ✅ Complete (Security Hardening) — previously untested; new unit-test suite covers cross-org/own-org/unrecognized-destination/user-queue cases |

Explicitly incomplete: command signing/replay protection (no commands are dispatched yet), message signing independent of TLS (documented, not implemented — see the hardening report §3.5), Android/agent-side security beyond MQTT credential wiring (Part 21 — kiosk/device-owner mode, Keystore-backed credential storage, secure updates — all still untouched/undone, see hardening report §9), MQTT broker security (TLS termination, per-robot credential enforcement, topic ACLs — client-side wiring exists, not enforced against a real broker), DB-role-level audit tamper hardening, and every physical-robot-dependent control in Part 11/38.

## Security

`docs/security/SAKAR_SECURITY_REQUIREMENTS.md` remains the authoritative control specification. The table above documents what Phase 1 + Phase 3 actually implement against it — this repository is no longer purely "specified, not implemented" for the backend or the agent's communication layer, but most of Parts 16–29 still describe target state beyond what's implemented (MQTT broker hardening against a real deployment, Android/agent security beyond credential wiring, backup/DR, monitoring, security testing). The platform's highest-risk feature — remote motor lock/unlock — remains **not production-ready and not software-implemented at all**; Phase 3 changes nothing about the physical validation requirement (Part 11/38) and deliberately does not touch lock/unlock.

## Documentation

All approved specifications live under `docs/` — see `docs/README.md` for the full index. `docs/requirements/SAKAR_ROBOT_PLATFORM_MASTER_REQUIREMENTS.md` is the authoritative requirements reference this README is checked against; where this README and that document diverge, the master requirements document governs and this README should be corrected.

## Proposed Product Expansion (Keenon Parity) — NOT IMPLEMENTED

`docs/requirements/SAKAR_PLATFORM_KEENON_PARITY_REQUIREMENTS.md` scopes three product modules observed in Keenon Cloud (the vendor's own dashboard) that Sakar's platform has no equivalent of today: **Open Platform** (third-party API client/key management), **OTA / Deployment** (firmware/app version tracking and rollout), and **IoT / Elevator Integration** (non-robot device registration and elevator-call authorization). This is a **requirements/scope document only** — no entity, controller, migration, or UI for any of the three exists in this repository, and none should be inferred from the Keenon reference screenshots that motivated scoping them. If approved, they would become Roadmap Phases 11-13, sequenced after and independent of the approved Phase 0-10 work; see that document's §18 for the recommended order and §17 for explicit out-of-scope items (never copy Keenon branding, never invent a firmware-push or elevator protocol that hasn't been vendor-confirmed).

## External Peanut SDK

The Keenon **Peanut SDK** (`peanut-sdk-v1.3.0`, including the vendor's sample app and the decompiled technical study material) is an **external, read-only vendor dependency** that lives at `D:\Sakar Robotics Projects\peanut-sdk-v1.3.0`, outside this project workspace. It has not been moved, copied, or modified, and Phase 1 backend work did not touch it. `robot/SakarC40Agent/sdk/libs/peanut-sdk-release.aar` is the pre-existing vendored binary that `SakarC40Agent` already depends on, unmodified, and excluded from Git tracking (see `.gitignore`).

## Roadmap

**Completed this pass (Phase 6 — Fleet Operations, approved scope):** Users/Roles administration REST API, task orchestration lifecycle, low-battery/offline alerting, cleaning-history read path, and non-lock remote commands, all built by reusing the existing `TenantAccessGuard`/`AuditService`/`SakarErrorCode`/`RobotCapabilityService` chokepoints rather than introducing parallel ones — see Implementation Status above and `docs/requirements/SAKAR_ROBOT_PLATFORM_ROADMAP.md`'s Phase 6 entry. 22 new backend tests added, 93/93 passing.

**Also completed this pass (Decision 2 — Keenon-parity scope-only work, explicitly NOT implemented):** `docs/requirements/SAKAR_PLATFORM_KEENON_PARITY_REQUIREMENTS.md` scopes three proposed product-expansion modules (Open Platform, OTA/Deployment, IoT/Elevator Integration) surfaced by Keenon Cloud reference screenshots, and the roadmap's "Proposed Product-Expansion Phases" section (11-13) records where they would sit **if** separately approved. No backend code for any of the three exists — see that document's status banner and §17 "Out-of-Scope Items."

**Also completed this pass (Phase 6 frontend integration):** the web application was rewired from `web/src/mocks/simulated.ts` onto the real Phase 6 endpoints — Users (`UsersPage`, including create/suspend/activate/role-change), Roles (`RolesPage`, real GET /roles), Tasks (`TasksPage` + a shared `RobotTasksPanel` reused on the Robot Detail page, including lifecycle start/pause/resume/stop/cancel), Alerts (`AlertsPage` + `RobotAlertsPanel`, including acknowledge/resolve), Cleaning history (new `CleaningPage` + `RobotCleaningPanel`), and a new Robot Detail "Commands" tab (`CommandsPanel`) for non-lock remote commands only — LOCK/UNLOCK remain unofferable by construction. `generateAlerts`/`generateTasks`/`generateUsers`/`generateRoles` were removed from the mocks file entirely; Telemetry/Events/Errors/Application Logs/Timeline remain simulated (no backend endpoint exists for any of them). The Dashboard's Locked/Faulted/Cleaning/Charging fleet metrics are now honestly marked Unavailable rather than fabricated; Low Battery is now real (derived from open `LOW_BATTERY` alerts). 7 new frontend tests added (28/28 passing); full live-browser verification against a running backend was not performed (no Docker/Postgres/Redis in this environment).

**Prior pass: Story 3 — Implement SakarC40Agent Communication Layer (software).** The MQTT-based telemetry channel between Sakar Cloud and `SakarC40Agent` exists and is automated-test-verified, with `robot_status`/`robot_telemetry`/`robot_events`/`robot_errors` ingestion wired. Still not done: replacing the `SakarRobotAdapter` stub with a working local-path *command* adapter, and any physical/network validation.

**Next development step:** live-browser verification of this frontend integration pass against a real running backend once Postgres/Redis/Docker are available; separately, Phase 0 physical validation (Master Requirements Part 38) and a live network test of the MQTT pipeline remain outstanding and continue to gate remote lock/unlock and production sign-off for any physical-action command.

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

**Current authorized scope:** backend Phase 1 is complete; Story 3 (`SakarC40Agent` MQTT communication layer) and Phase 6 (Users/Roles, Tasks, Alerts, Cleaning history, non-lock Remote Commands) are implemented in software and test-verified, per the roadmap above, but **uncommitted** on `feature/phase6-fleet-operations` — pending review/approval before any commit, merge, or push. Open Platform / OTA / IoT-Elevator Integration are requirements-only (see Proposed Product Expansion above) and remain explicitly not implemented pending separate review. Physical C40 connection, physical robot control, and remote lock/unlock certification remain out of scope until the separate physical-validation go-ahead described in Master Requirements Part 33/38 is given.
