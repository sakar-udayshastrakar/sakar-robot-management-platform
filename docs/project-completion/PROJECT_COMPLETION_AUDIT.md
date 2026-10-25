# Sakar Robotics — Project Completion Audit

**Date:** 2026-10-25
**Scope:** `D:\Sakar Robotics Projects\sakar robotics web` — Android agent (`robot/SakarC40Agent`), Web app (`web/`), Backend (`backend/`), DB, docs, tests.
**Method:** Static inspection of the actual repository (robot-dev MCP for Android; code reads + two deep-dive passes for backend/web). No robot hardware was contacted; no commands were sent.

## 0. Headline finding (read first)

**This is a mature, disciplined, deliberately fail-closed system — not a broken or half-built one.** Across all three codebases the "incomplete" areas are overwhelmingly *intentional, documented, safety-gated, or hardware-blocked seams*, not accidental gaps:

- **Android** (`SakarC40Agent`, 14 Gradle modules, v0.1.0-poc): real Peanut SDK bridge, real MQTT link to the backend, clean domain/data/robot/sdk layering, a hard **safety gate** (`OperatingMode.DIAGNOSTIC_ONLY` by default; actuation only under `HARDWARE_TEST`, which nothing auto-enables), and a code-level honesty invariant (`RobotCapabilityResult` cannot tag a placeholder as REAL). Existing unit tests.
- **Backend** (Spring Boot 4.1, Java 21): 34 REST controllers (~110 endpoints, ~110 `@PreAuthorize`), 49 entities, 49 repos, 55 services, **26 Flyway migrations**, **82 test files**, real Keenon cloud integration, MQTT ingestion stack, WebSocket/STOMP, JWT + RBAC + tenant isolation. Nothing returns fabricated data.
- **Web** (React 19 + TS + Vite): full `sakar-*` design system, ~30 feature areas, every screen tagged `live`/`simulated`/`unavailable`, all 27 API modules map to real backend controllers.

**Consequence for "completion":** the task's own rules — and the codebase's design — forbid "finishing" the remaining gaps by enabling real robot actuation, implementing the robot-agent/ROS path against **unavailable hardware**, faking telemetry, or loosening security. After verification, the surface that is **both genuinely missing and safe to complete now** (no hardware, no product/security decision) is small. Those items are implemented this pass; everything else is documented below with an honest status and the reason it is gated.

> **Correction to a prior finding:** an earlier pass suspected `GET /robots/{id}/areas` omitted the Sakar area id needed to start a cleaning task. **Verified false** — `AreaInfo` includes `sakarAreaId`, `KeenonRobotAdapter.getAreas()` populates it (`mapping.getId()`), and `startTask()` consumes exactly those ids. The START_TASK path is wired end-to-end (web → backend → Keenon) and the web `RobotTasksPanel` already uses it.

## 1. Architecture & integration (actual, verified)

```
Web (React/Vite) ──REST /api/v1 (JWT)──┐
                                        ├──> Sakar Cloud Backend (Spring Boot) ──┬── Keenon adapter ──HTTP──> Keenon Cloud  (REAL, live-tested)
Android agent (SakarC40Agent) ──MQTT────┘     (RobotAdapter SPI, registry)       └── Sakar-native adapter ──MQTT──> Android agent ──Peanut SDK──> C40  (STUB: fail-closed)
```

- **Android ↔ Backend:** real, over **MQTT** (Eclipse Paho 1.2.5, same lib/version both sides). The agent (`AgentMqttClient`) publishes heartbeat/telemetry/events/errors and subscribes to a command/ack channel; the backend (`MqttGatewayService` + ingestion stack) is the counterpart. **Disabled by default** (`SAKAR_MQTT_ENABLED=false`) — no broker contact in dev.
- **Web ↔ Backend:** real REST, all 27 client modules map to existing controllers.
- **Backend ↔ robot:** two adapters behind one SPI. `KeenonRobotAdapter` = **real/live-tested** (status, battery, telemetry, areas, start/stop/pause task, return-to-dock, cleaning modes 101–105). `SakarRobotAdapter` = **deliberate stub** (all methods fail closed) pending the real MQTT→agent→SDK path + hardware.
- `mobile/` is an **empty placeholder** (README only) — there is no Flutter app; the Android application is `SakarC40Agent`.

## 2. Android (`robot/SakarC40Agent`) — status

| Area | Status | Notes |
|---|---|---|
| Module architecture (domain/data/robot/sdk/navigation/charging/operator-ui/api/telemetry/diagnostics/virtual-agent) | **Complete** | 14 modules, clean layering; only `:sdk` imports `com.keenon.*` |
| Peanut SDK bridge (`PeanutSdkBridge`) | **Complete (real surface)** | motor/nav/charging/battery/lidar/depth/sonar/imu/map; some methods "intentionally not wired yet" |
| Safety gate (`OperatingMode`) | **Complete by design** | `DIAGNOSTIC_ONLY` default; actuation needs `HARDWARE_TEST`, never auto-enabled |
| MQTT link to backend (`AgentMqttClient`, `CommandDispatcher`) | **Complete** | heartbeat/telemetry/events/errors + command/ack; `RETURN_TO_DOCK` has a real wired executor |
| `START_TASK` / go-to-point executor | **Partial (intentional)** | `PeanutSdkGoToPointExecutor` is a documented "Roadmap Phase 8" seam; app wires only a simulated START_TASK executor today |
| ROS / robot-computer link (`RosRobotAdapterStub`) | **Blocked (stub by design)** | returns NOT_AVAILABLE/NOT_VERIFIED; needs ROS endpoint + hardware |
| Operator-UI buttons: Manual Push / Recharge / Mute / Light | **Partial (UI placeholder)** | checkpoint placeholders; Recharge maps to gated `returnToDock` |
| PIN pads (IdleLock / SuperUserAuth) | **Partial (placeholder)** | any-4-digits unlock, explicitly a checkpoint stub |
| `SakarBackendApi.java` (REST boundary) | **Not implemented (vestigial)** | MQTT is the real integration; this REST placeholder is unused |
| Unit tests | **Present** | `C40RobotControllerTest` (gating), repo tests, `RosRobotAdapterStubTest`, command dispatcher/executor tests |

## 3. Backend (`backend/`) — status

| Area | Status | Notes |
|---|---|---|
| REST API (34 controllers, ~110 endpoints) | **Complete** | all under `/api/v1` (+ Keenon webhook); method-level `@PreAuthorize` throughout |
| Data model (49 entities / 49 repos / 55 services) | **Complete** | UUID-FK, tenant-scoped, `ddl-auto: validate` |
| Migrations (Flyway) | **Complete** | 26 migrations V1→V26 |
| Auth (JWT + refresh, Redis rate-limit/lockout, BCrypt) | **Complete** | |
| RBAC (12 permissions, 6 roles, seeded) + tenant isolation | **Complete** (one limit) | `TenantAccessGuard` stops at **org** level; per-user site/robot narrowing **not implemented** |
| Keenon integration (adapter, API client, OAuth, sync schedulers, webhook) | **Complete / live-tested** | sync disabled by default (real outbound HTTP) |
| MQTT ingestion + command dispatch/result/expiry | **Complete** | disabled by default; dedup/rate-limit; honest status semantics |
| WebSocket/STOMP real-time publisher | **Complete (wired)** | org-scoped topics; awaits a dashboard consumer |
| Dashboard/analytics | **Complete (real rows)** | untracked metrics returned `null`, never faked |
| OTA / IoT / resource-config / repair / remote-deployment / open-platform | **Complete (records/CRUD)** | no physical device actuation |
| Password reset (`forgot-password` / `reset-password`) | **Missing** | whitelisted in `SecurityConfig` but **no handler** (dead config) |
| `SakarRobotAdapter` | **Stub (fail-closed)** | all methods throw `FEATURE_NOT_YET_IMPLEMENTED` |
| Keenon `getMap()` / `resumeTask()`, GO_TO_POINT, lock/unlock | **Unsupported by design** | vendor gaps / deliberately unexposed |
| `notification`, `lock` domains | **Schema-only** | entity+repo, no API/service (intentional) |
| Tests (82 files) | **Comprehensive** | controller + security + service + MQTT + full Keenon stack |

## 4. Web (`web/`) — status

| Area | Status | Notes |
|---|---|---|
| Stack, design system, routing, auth, RBAC gating | **Complete** | React 19 / Vite / `sakar-*` tokens |
| Robot list/detail, status/battery, tasks (cleaning control), cleaning history, telemetry, events/errors/alerts, map panel, commands, diagnostics, audit | **Complete (live)** | all backend-backed |
| Dashboards, orgs/sites/stores, users/roles/permissions, OTA, IoT, open-platform, operational dashboard, resource config | **Complete (live)** | |
| Voice Announcements (`/voice`) | **Complete (demo)** | added earlier this session; browser Web Speech API, labeled simulated |
| C40 Cleaning Control (`/c40-control`) | **Complete (live)** | added earlier this session; composes backend-backed panels |
| Placeholders: `/analytics`, Running Statistics/Record, Learning Center, Remote log, Language config | **Unavailable (honest)** | `PlannedFeaturePage` — no backend; correctly labeled |
| Password reset UI | **Missing** | matches backend gap |
| WebSocket real-time consumption | **Partial (off)** | STOMP client exists, gated by `VITE_ENABLE_WEBSOCKET=false` |

## 5. Bugs & technical issues

| # | Issue | Severity | Location |
|---|---|---|---|
| B1 | `forgot-password`/`reset-password` whitelisted as public but no handler exists (dead `permitAll`; no self-service reset anywhere) | Medium | `security/SecurityConfig.java:46-47` |
| B2 | MQTT credential revoke is soft — does not terminate an open connection or block reconnection | Low–Med | `robot/registry/RobotCredentialService.java:83` |
| B3 | Tenant scoping stops at org level (no per-user site/robot narrowing) | Med (security) | `security/access/TenantAccessGuard.java:25-28` |
| B4 | CORS hardcoded to `http://localhost:*` — must be set per-deployment before prod | Med (deploy) | `security/SecurityConfig.java:78` |
| B5 | `SakarBackendApi.java` (Android) is an unused REST placeholder; MQTT is the real path | Low (cleanup) | `api/.../SakarBackendApi.java` |

No accidentally-broken code found; no bare TODO/FIXME in backend; Android TODO-ish markers are all labeled "placeholder for this checkpoint".

## 6. Priority & dependencies

**Critical:** none outstanding (no broken core paths).
**High:**
- H1 Password-reset flow (B1) — **needs a product/security decision** (self-service + email delivery vs admin-initiated reset). *Blocked on decision.*
- H2 `SakarRobotAdapter` real implementation — **depends on** the Android agent's real command executors **and physical C40**. *Requires physical robot.*
**Medium:**
- M1 Per-user site/robot tenant scoping (B3) — security design change. *Needs requirements confirmation.*
- M2 Prod CORS/secrets config (B4) — deployment task. *Needs deployment values.*
- M3 Android `START_TASK`/go-to-point real executor — *Requires physical robot* (gated).
- M4 `notification` read API (schema exists) — *Needs product confirmation it's in scope.*
**Low:**
- L1 Remove/retire dead config (B1 half) and vestigial `SakarBackendApi` (B5).
- L2 Test coverage for the fail-closed stubs / new web pages. **(done this pass — see §7)**
- L3 WebSocket real-time consumption in web (flag + consumer).

**Dependency notes:** H2→(agent executors + hardware); M3→(HARDWARE_TEST + hardware); H1 gates the web reset UI; everything robot-actuating is gated behind `OperatingMode.HARDWARE_TEST` by design and must not be auto-enabled.

## 7. Work completed this pass (safe, verified)

Only behavior-preserving, non-presumptuous changes were made (see `IMPLEMENTATION_SUMMARY.md` for details + test results):
- **Backend:** `SakarRobotAdapterTest` — locks in the fail-closed contract (every capability throws `FEATURE_NOT_YET_IMPLEMENTED`; adapter type `SAKAR_NATIVE`), mirroring the Android `RosRobotAdapterStubTest` safety pattern. Guards against a future accidental "make it work with fake data" regression.
- **Web:** `C40ControlPage.test.tsx` — render/contract test for the C40 control console added earlier this session.
- *(Earlier this session, toward system completeness: the web Voice feature and C40 Cleaning Control view, committed on `dev`.)*

## 8. Checklist / status tracker

| Item | Status |
|---|---|
| Whole-project inspection (Android/Web/Backend/DB/tests) | ✅ Completed |
| Functional audit (this doc) | ✅ Completed |
| Verify Android↔Backend↔Web integration is real | ✅ Completed (MQTT + REST confirmed) |
| Verify START_TASK area-id path | ✅ Completed (already wired) |
| Backend fail-closed stub test | ✅ Completed |
| Web C40 page test | ✅ Completed |
| Password-reset flow | ⛔ Blocked (product/security decision) |
| SakarRobotAdapter real impl | 🤖 Requires physical robot |
| Android START_TASK/go-to-point real executor | 🤖 Requires physical robot (gated) |
| Per-user tenant scoping | ⏸ Pending (requirements) |
| Prod CORS/secrets | ⏸ Pending (deployment values) |
| notification read API | ⏸ Pending (product scope) |
| Remove dead password-reset `permitAll` / vestigial `SakarBackendApi` | ⏸ Pending (confirm — security-config change) |
| WebSocket real-time consumer (web) | ⏸ Pending |

Legend: ✅ Completed · ⏸ Pending · ⛔ Blocked · 🤖 Requires physical robot testing.
