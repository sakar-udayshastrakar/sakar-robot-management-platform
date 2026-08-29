# Sakar Robot Management Platform

**This is the single project workspace for the Sakar Robotics Robot Management Platform. All new source code, infrastructure, tests, and documentation for this platform must live under this root (`D:\Sakar Robotics Projects\sakar robotics web`).**

## CURRENT PHASE

**PHASE 4 — WEB APPLICATION IMPLEMENTED (uncommitted — pending review/approval).**

The Sakar Web Application (`web/`, React + TypeScript) now exists and talks to the real Sakar Cloud Backend for every feature the backend actually supports: authentication, organizations, sites, robots (registry, activate/deactivate, live status probe, MQTT credential provisioning), and audit logs. Telemetry, events, errors, alerts, tasks, application logs, users, and roles have no backend REST endpoint yet (their tables/entities exist, but nothing exposes them over REST) — those pages render clearly-labeled simulated data instead of fabricating a real feed. See `docs/architecture/SAKAR_WEB_APPLICATION_ARCHITECTURE.md` and `docs/requirements/SAKAR_PHASE_4_WEB_IMPLEMENTATION_REPORT.md` for the full account, including live browser verification against a real running instance of the backend.

Separately, Phase 3's own MQTT pipeline was validated against a real (locally-hosted) MQTT broker after this README was last written — see `docs/requirements/SAKAR_PHASE_3_LIVE_MQTT_VALIDATION_REPORT.md`: MQTT connection/auth/TLS/ACL, heartbeat, telemetry, dedup, replay, cross-tenant isolation, rate limiting, and reconnect all passed against a real broker with the real, unmodified backend and agent code — still **using a test agent, not a physical CleanBot**, and still uncommitted.

## Project Status

| Field | Value |
|---|---|
| Current phase | Phase 4 — Web Application implemented (against the real backend), uncommitted |
| Overall status | Backend + Agent MQTT pipeline implemented, live-broker-validated, and test-verified; Web Application implemented against real backend endpoints (simulated data only where no endpoint exists); not yet committed/merged; mobile and physical robot validation not started |
| Last updated | 2026-08-30 |
| Latest branch | `dev` (working tree modified, not committed — see `git status`/`git diff` before trusting "current" claims) |
| Latest commit | `5b4a3a0` — Merge pull request #4 from `sakar-udayshastrakar/backend/phase-1-foundation` (Phase 1; Phase 3 work sits on top, uncommitted) |
| Test status | **71 / 71 automated backend tests passing** (`cd backend && ./mvnw clean test`) — 32 pre-existing + 27 Phase 3 + 12 Phase 3 Security Hardening. **18 / 18 automated Robot Agent (`:api` module) tests passing** (`cd robot/SakarC40Agent && ./gradlew :api:test`) |
| Build status | Backend: **Maven build and package successful**. Robot Agent: **`./gradlew assembleDebug test` BUILD SUCCESSFUL** (debug APK assembles with the new `:api` module wired in) |
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
├── tests/                    Placeholder — the actual automated tests live in backend/src/test/java (32 tests, see Testing Status)
└── README.md                 This file
```

## Applications

| Application | Location | Status |
|---|---|---|
| Sakar Cloud (backend) | `backend/` | **Phase 1 foundation implemented** — see Implementation Status |
| Web application | `web/` | **Phase 4 implemented** — React + TypeScript admin UI against the real backend for auth/organizations/sites/robots/audit; simulated data (clearly labeled) for telemetry/events/errors/alerts/tasks/logs/users/roles, which have no backend REST endpoint yet |
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
| Command signing/dispatch | ⏳ Planned | `command/` entities + lifecycle enum exist; no signing, no MQTT dispatch — explicitly out of Phase 3 scope |
| Robot lock/unlock (software) | ⏳ Planned | `lock/` schema only; no endpoint exists to trigger it — explicitly excluded from Phase 3 |
| Task / cleaning orchestration | ⏳ Planned | `task/`, `cleaning/` entities exist; no orchestration logic |
| Alerting | ⏳ Planned | `alert/` entity/repository only |
| Analytics | ⏳ Planned | No code exists yet |
| SakarC40Agent integration | 🔄 Partially Implemented (Phase 3) | MQTT telemetry/heartbeat/events/errors link now exists (`:api` module) and is wired into `SakarC40Application`; not yet run against a real broker or physical robot — see Robot Integration Status |
| Robot MQTT credential issuance | ✅ Complete (Phase 3) | `RobotCredentialService`, `POST /api/v1/robots/{id}/mqtt-credentials` — `robot_credentials` now populated; broker does not yet enforce it |
| Physical C40 validation | ⚠️ Requires Physical Test | Not performed; gated by Master Requirements Part 38 |
| Remote lock/unlock certification | ⚠️ Requires Physical Test | Not performed; not even software-implemented yet; gated by Master Requirements Part 11/38 |

## Database Status

The PostgreSQL + Flyway foundation is implemented: 11 migrations (`backend/src/main/resources/db/migration/V1__core_and_iam.sql` through `V11__mqtt_inbound_messages.sql`) create the full schema and seed the RBAC matrix plus Keenon C40 S / Sakar CleanBot 5000 Plus reference data. Current tables:

`organizations`, `sites`, `roles`, `permissions`, `role_permissions`, `users`, `refresh_tokens`, `robot_manufacturers`, `robot_models`, `robot_capabilities`, `robots`, `robot_credentials`, `robot_status`, `robot_telemetry`, `robot_events`, `robot_errors`, `robot_alerts`, `application_logs`, `robot_commands`, `command_results`, `robot_locks`, `robot_tasks`, `task_events`, `cleaning_sessions`, `charging_sessions`, `maps`, `map_points`, `notifications`, `audit_logs`, `keenon_area_mappings`, `vendor_webhook_events`, `mqtt_inbound_messages`.

**Schema existing does not mean the corresponding workflow is complete.** As of Phase 3, `robot_status`, `robot_telemetry`, `robot_events`, `robot_errors`, `robot_credentials`, and `mqtt_inbound_messages` now have real application code populating them (via the MQTT ingestion pipeline). `robot_commands`, `robot_tasks`, `cleaning_sessions`, and `robot_locks` still have no application code populating them — see Implementation Status above for which modules remain foundation-only.

## Testing Status

**71 / 71 automated backend tests passing** (`cd backend && ./mvnw clean test`) — 32 pre-existing (unmodified) + 27 Phase 3 + 12 Phase 3 Security Hardening. Coverage includes everything Phase 1 already covered (login/lockout/rate-limiting, refresh-token rotation and reuse detection, RBAC enforcement, tenant isolation, organization-hierarchy logic, robot capability gating, Keenon adapter response mapping, webhook idempotency, audit-log writes, JWT expiry/tamper handling); Phase 3: MQTT topic build/parse, full ingestion-pipeline acceptance/rejection paths, heartbeat/telemetry/event/error/presence ingestion, ACK generation, robot MQTT credential provisioning/rotation; Security Hardening: negative-sequence/oversized-payload/rate-limit rejection, credential revocation and audit-trail verification (including that the raw secret never appears in any log), and a new WebSocket tenant-isolation unit-test suite. See `docs/security/SAKAR_PHASE_3_SECURITY_HARDENING_REPORT.md` for the full finding-by-finding account.

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

## External Peanut SDK

The Keenon **Peanut SDK** (`peanut-sdk-v1.3.0`, including the vendor's sample app and the decompiled technical study material) is an **external, read-only vendor dependency** that lives at `D:\Sakar Robotics Projects\peanut-sdk-v1.3.0`, outside this project workspace. It has not been moved, copied, or modified, and Phase 1 backend work did not touch it. `robot/SakarC40Agent/sdk/libs/peanut-sdk-release.aar` is the pre-existing vendored binary that `SakarC40Agent` already depends on, unmodified, and excluded from Git tracking (see `.gitignore`).

## Roadmap

**Completed this pass: Story 3 — Implement SakarC40Agent Communication Layer (software).** The MQTT-based telemetry channel between Sakar Cloud and `SakarC40Agent` now exists and is automated-test-verified, with `robot_status`/`robot_telemetry`/`robot_events`/`robot_errors` ingestion wired. **Not yet done as part of this story:** replacing the `SakarRobotAdapter` stub with a working local-path *command* adapter (this phase implemented the telemetry/heartbeat direction only, not command dispatch), and any physical/network validation.

**Next development step: Phase 0 physical validation (Master Requirements Part 38) + a live network test of this MQTT pipeline** — running the agent against a real Mosquitto broker and, separately, a physical C40, since neither was done in this pass by explicit instruction. Command signing/dispatch, remote lock/unlock, task/cleaning orchestration, alerting, and analytics remain gated behind that validation and are not unblocked by Phase 3.

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

**Current authorized scope:** backend Phase 1 is complete; Story 3 (`SakarC40Agent` MQTT communication layer) is now implemented in software and test-verified, per the roadmap above, but **uncommitted** — pending review/approval before any commit or push. Physical C40 connection, physical robot control, and remote lock/unlock certification remain out of scope until the separate physical-validation go-ahead described in Master Requirements Part 33/38 is given.
