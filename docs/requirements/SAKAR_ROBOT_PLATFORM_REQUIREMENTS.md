# Sakar Robot Management Platform — Requirements Specification (SRS + PRD)

**Status:** Requirements/design document only. No software has been implemented. No existing project (`SakarC40Agent`, Peanut SDK, `peanut-sdk-release.aar`, existing backend/frontend projects, existing Postman collections) has been modified.

**Initial robot:** Keenon C40 / C40 S. Architecture must not assume a single robot model; a `robot_models` abstraction is required (see `SAKAR_ROBOT_PLATFORM_DATABASE.md`).

**Evidence basis:** every claim below is graded `CONFIRMED` (proven by live API test, decompiled bytecode, or direct source read — see the four source studies), `LIKELY` (implied but not directly proven), `UNKNOWN` (not established either way), or `REQUIRES PHYSICAL C40 TEST` (cannot be resolved without hardware access). Source studies referenced throughout: `PEANUT_SDK_C40_TECHNICAL_STUDY.md`, `PEANUT_SDK_C40_API_MATRIX.md`, `KEENON_C40_CLOUD_API_AUDIT.md`, `KEENON_C40_API_TEST_RESULTS.json`.

---

## 0. Product Vision

Sakar Robotics will own and operate a platform that sits between Sakar's robots (starting with Keenon C40/C40 S) and Sakar's customers, so that **Sakar — not the robot vendor — is the primary system of record** for robot data, control, and access. Concretely, Sakar owns:

robot data · telemetry · history · authentication · authorization · commands · lock/unlock · users · configuration · analytics · logs · alerts · fleet management.

This is **CONFIRMED achievable in principle**: the Peanut SDK technical study proved every hardcoded transport constant in the SDK (`127.0.0.1`, `192.168.64.20`, `192.168.64.10`, ports `5683/34569/12386/9527`) is local-only, and `SakarC40Agent` already reads a wide range of telemetry (battery, motor status/health, runtime info, navigation status) directly from the robot with no Keenon Cloud involvement. It is explicitly **not proven** that zero data ever reaches Keenon by any path (OTA subsystem and the stock Keenon app were both flagged as unverified) — this document does not claim that, and neither may any downstream document derived from it.

---

## 1. Scope — Four Applications

| # | Application | Runs on | Talks to |
|---|---|---|---|
| 1 | Mobile Application | Customer/Sakar staff phones (Android/iOS) | Sakar Backend only |
| 2 | Web Application | Browser | Sakar Backend only |
| 3 | Robot Android Tablet Application ("SakarC40Agent") | Robot's onboard Android computer | Peanut SDK (local) + Sakar Backend (remote, HTTPS) |
| 4 | Sakar Cloud/Backend | Sakar-owned infrastructure | All of the above; optionally Keenon Cloud REST API as a secondary/comparison data source only |

**Explicit non-goal:** none of the four applications route robot data through Keenon Cloud as their primary path. The existing, separately-audited Keenon Cloud REST API (`KEENON_C40_CLOUD_API_AUDIT.md`) remains available as a secondary source (e.g., historical cleaning logs already accumulated there — 697 records `CONFIRMED` present for the current fleet) but is not the system Sakar builds its product on.

---

## 2. Mobile Application Requirements

### 2.1 Roles supported
Sakar Admin, Sakar Support Engineer, Customer Admin, Customer Operator, Technician (subset of the full RBAC role list in §5.2 — a mobile session always maps to one of these).

### 2.2 Authentication
| Requirement | Priority | Notes |
|---|---|---|
| Login (email/username + password) | P0 | Against Sakar Backend Authentication Service (§ARCHITECTURE) |
| Logout | P0 | Invalidates session/refresh token server-side |
| Forgot password | P0 | Email-based reset flow |
| MFA/OTP | P1 (required for `SUPER_ADMIN`/`SAKAR_ADMIN`; optional otherwise) | TOTP or SMS OTP |
| Session management | P0 | Refresh-token rotation, device-bound sessions, remote session revocation |
| Device registration | P1 | Binds a mobile install to a user + org for push-notification targeting and anomaly detection |

### 2.3 Dashboard (fleet summary)
Total robots · online · offline · locked · low battery · with errors · currently cleaning · currently charging — all counts scoped to the organizations/sites the logged-in user is authorized for (§5.3). **Data source:** Sakar Telemetry Service, aggregated from `robot_status`/`robot_telemetry` (CONFIRMED achievable — every one of these fields is either directly read from the SDK today by `SakarC40Agent`, `CONFIRMED`, or a status derived from existing fields).

### 2.4 Robot List
Robot name, robot ID, model, serial number, online/offline, battery %, current state, lock status, current task. Battery/online-state: `CONFIRMED` available via `RuntimeInfo.getPower()`/`onlineStatus` equivalents. Current task: only meaningful once the Task Service (§ARCHITECTURE) exists — `UNKNOWN` whether the Peanut SDK exposes a single authoritative "current task" concept beyond navigation/cleaning status queried live; treat as a Sakar-side derived concept built from command history, not a raw SDK field.

### 2.5 Robot Details
| Field group | Status |
|---|---|
| Robot information (id, model, serial, firmware/app version) | `CONFIRMED` — `DeviceComponent.getBoardInfo`, `RuntimeInfo` fields |
| Battery / charging | `CONFIRMED` — `BatteryComponent.getStatus`, `PeanutCharger` |
| Location | `REQUIRES PHYSICAL C40 TEST` — `RuntimeComponent.getRobotPosition()` exists (`CONFIRMED` as an API) but SakarC40Agent's own code already flags returned data as unconfirmed on real hardware |
| Navigation state | `CONFIRMED` API exists (`NavigationComponent.getStatus`); real-robot values `REQUIRES PHYSICAL C40 TEST` |
| Cleaning state | `CONFIRMED` via Keenon Cloud (`/api/open/custom/clean/robot/status` — live-tested, real data for all 3 C40 S units) AND via Peanut SDK (`TopicName` includes cleaning-adjacent topics, though the specific "cleaning state" topic payload schema is `UNKNOWN` beyond what Keenon Cloud already proved) |
| Motor status | `CONFIRMED` — `MotorComponent.getStatus()`, values 16/32/49/50/51/52/255 |
| Health / errors / alarms | `CONFIRMED` API exists (`getHealth`, `onHealth`, Keenon Cloud `globalState.faulting` etc.); content schema for many of the SDK's 65 topics is `UNKNOWN` |
| Runtime / odometer | `CONFIRMED` — `RuntimeInfo.getTotalOdo()` |
| Firmware/app version | `CONFIRMED` — Keenon Cloud `appVersion` field, live-tested |
| Last communication | Sakar-side derived field (heartbeat timestamp), not an SDK field itself |
| Robot logs | Sakar-side aggregation of audit/event/telemetry records — a platform feature, not an SDK passthrough |

### 2.6 Robot Controls
Every control listed below is **authorization-gated** (§5.2) and **must not be represented in the UI as guaranteed-working** until the corresponding SDK call has been physically validated per §4.

| Control | SDK API (if any) | Status |
|---|---|---|
| Start / Resume | `NavigationComponent.setTarget`, `.resume` | `CONFIRMED` API exists; physical effect `REQUIRES PHYSICAL C40 TEST` |
| Pause | `NavigationComponent.pause` | Same |
| Stop | `NavigationComponent.stop` | Same |
| Return to charging | `BatteryComponent.manualCharge`/`autoCharge`, or `PeanutCharger.performAction` | `CONFIRMED` API exists; SakarC40Agent itself avoids `autoCharge` today because the pile-number semantics are unconfirmed |
| Navigation (send to point) | `NavigationComponent.setTarget(id)` | `CONFIRMED` API exists; **moves the robot** — physical test mandatory before enabling in production |
| Lock | `MotorComponent.enable(cb, MOTOR_ENABLE_LOCK=1)` | See §4 — full dedicated section |
| Unlock | `MotorComponent.enable(cb, MOTOR_ENABLE_UNLOCK=0)` | See §4 |

The mobile UI **must** visually distinguish "command sent" from "command confirmed by robot" from "physically verified behavior" — three different states, not one. Never collapse these into a single green checkmark.

### 2.7 Lock/Unlock (mobile)
A user holding `ROBOT_LOCK`/`ROBOT_UNLOCK` permission can lock/unlock. The mobile app must display: current lock state, who locked it, when, reason (free-text, required field), last unlock (who/when), and the authorized-operator list for that robot. This is a UI requirement over data the Sakar Backend's Robot Lock module owns (§ARCHITECTURE, §DATABASE `robot_locks` table) — it is **not** a direct mobile-to-robot connection under any circumstance.

### 2.8 Notifications (push)
Robot offline · low battery · critical error · robot locked · robot unlocked · task completed · task failed · charging started · charging completed · emergency event · communication lost. All are Sakar Backend-originated (Notification Service, §ARCHITECTURE) triggered by telemetry/event thresholds or lock-state changes — none require any new SDK capability beyond what's already `CONFIRMED` readable.

---

## 3. Web Application Requirements

### 3.1 Roles
`SUPER_ADMIN`, `SAKAR_ADMIN`, `SAKAR_SUPPORT`, `CUSTOMER_ADMIN`, `CUSTOMER_OPERATOR`, `TECHNICIAN`, `VIEWER` — full definitions and permission matrix in §5.2.

### 3.2 Modules

**Dashboard** — same fleet-overview data as mobile §2.3, denser layout, filterable by organization/site/robot-group.

**Fleet Management** — organizations, sites, robots, robot groups, robot assignments. This is a pure Sakar-side data model (§DATABASE `organizations`, `sites`, `robots`) with no SDK dependency — `CONFIRMED` buildable today.

**Robot Management** — add/register/activate/deactivate robot, lock/unlock, robot details, health, logs, configuration. "Register robot" requires only a robot identity (serial/`mftCode`, `CONFIRMED` available from Keenon Cloud robot-list) plus Sakar-side onboarding — does not require any unverified SDK capability. "Configuration" write-back to the robot (e.g. `DeviceComponent.updateConfig`) is `CONFIRMED` to exist as an API but its effect is `UNKNOWN`/`REQUIRES PHYSICAL C40 TEST`.

**Live Robot Monitoring** — state, battery, charging, position, navigation, cleaning, errors, connection, lock state. Same confidence grading as §2.5.

**Map** — robot position, robot path, cleaning path, target points, charging station, restricted zones, map management. Grading:
| Capability | Status |
|---|---|
| Static floor-plan image | `CONFIRMED` — Keenon Cloud `/api/open/custom/robot/map` returned a real PNG live in testing |
| Named target/map points with coordinates | `CONFIRMED` — both Keenon Cloud (`/api/open/custom/robot/map/position`) and Peanut SDK (`MapComponent.getMapInfo`, `RouteNode`) exist |
| Live robot position overlay | `REQUIRES PHYSICAL C40 TEST` — both the Cloud API (`data: null` for our fleet, tested) and the SDK (`getRobotPosition`, unconfirmed) failed to produce real-time coordinates in testing to date |
| Cleaning path / coverage overlay | `CONFIRMED` data exists — Keenon Cloud cleaning-log entries include a `taskSnapshot` image URL per run (live-tested) |
| Restricted zones | `UNKNOWN` — no API for defining/enforcing restricted zones was found in either source study; if required, treat as **P2/future**, pending explicit vendor confirmation |
| Map management (import/export) | `CONFIRMED` API exists — `MapManager.onImportToRos/onExportToAndroid` (SDK), USB-transfer workflow per documentation; not cloud-based |

**Tasks** — create, assign robot, start/pause/resume/stop/cancel, task history, task status. Task orchestration itself is a **Sakar-owned concept** (§DATABASE `robot_tasks`, `task_events`) that issues the underlying SDK commands (§2.6) and records their lifecycle — it is not a single SDK API.

**Cleaning** — cleaning mode, strategy, area, schedule, cleaning history, duration, status. **Distinguish confirmed vs. future:**
| Capability | Status |
|---|---|
| Cleaning history (what ran, when, duration, area, efficiency, failure reason, snapshot image) | `CONFIRMED` — Keenon Cloud `clean/log/list`, live-tested, 697 records for current fleet |
| Cleaning schedule read | `CONFIRMED` — Keenon Cloud `clean/strategy/list`, live-tested, 3 active schedules |
| Cleaning mode / return-point query | `CONFIRMED` API exists (Keenon Cloud, documented, not yet live-tested) |
| Creating/editing a cleaning schedule from Sakar | `CONFIRMED` API exists (Keenon Cloud `PUT`/`POST .../clean/robot/strategy/task`) — classified `NON_PHYSICAL_WRITE` in the Cloud audit, not yet exercised |
| Triggering an immediate/temporary cleaning task | `CONFIRMED` API exists, classified `PHYSICAL_ROBOT_CONTROL`, not yet exercised — **P1, requires physical validation before production use** |

**Robot Lock Management (dedicated security module)** — lock, unlock, lock reason, timestamp, locked-by, unlock timestamp, unlocked-by, lock history, authorization policy. This is the single most safety-critical module in the platform; see §4 for the full dedicated treatment. No user may unlock without the `ROBOT_UNLOCK` permission, which must be a strictly stronger grant than `ROBOT_LOCK` (§5.2) — locking should be easy to do (fail-safe direction), unlocking must be deliberately harder.

**Alerts** — error, warning, critical, offline, low battery, emergency, task failure. Sourced from telemetry thresholds + SDK health/event callbacks, all `CONFIRMED` as data sources.

**Analytics** — robot utilization, cleaning duration, battery usage, charging duration, task completion rate, error frequency, downtime, online/offline history, robot performance. All derivable from telemetry/audit data Sakar already owns once collected — no new SDK capability required; this is a Sakar-side aggregation/reporting concern.

**Audit Logs** — see §5.4 for the full field spec. Every sensitive action (login/logout, robot registration, lock, unlock, start, stop, navigation, config change, permission change, task creation/cancellation) must be recorded, immutable, and queryable.

---

## 4. Robot Android Tablet Application ("SakarC40Agent") Requirements

### 4.1 Role
Runs on the robot's onboard Android computer. Bridges the local Peanut SDK link to the remote Sakar Backend. This is an **evolution of the existing `SakarC40Agent` codebase**, not a new project — its current architecture (`PeanutSdkBridge` as the sole SDK chokepoint, `C40RobotController` as the safety-gated facade, `OperatingMode` as the only existing safety gate) is the correct foundation and should be extended, not replaced.

### 4.2 SDK usage
All Peanut SDK access continues to route exclusively through `PeanutSdkBridge`/`C40RobotController`, per the existing architecture (`CONFIRMED` — this isolation already exists and was verified by source read: every `com.keenon.*` import in the current codebase is confined to one file).

### 4.3 Local robot communication
Uses whichever `LinkType` (`COM`/`COM_COAP`/`COAP`/`HTTP`) is appropriate for the deployed C40/C40 S — **`UNKNOWN` which one**, per SakarC40Agent's own code comments; must be determined during Phase 0 physical validation (see `SAKAR_ROBOT_PLATFORM_ROADMAP.md`).

### 4.4 Telemetry collection (local, on-device)
| Data | Status |
|---|---|
| Battery, charging | `CONFIRMED` |
| Motor status | `CONFIRMED` |
| Runtime state, work mode, odometer | `CONFIRMED` |
| Robot IP, robot properties | `CONFIRMED` |
| Navigation state | `CONFIRMED` API; real values `REQUIRES PHYSICAL C40 TEST` |
| Position | `REQUIRES PHYSICAL C40 TEST` |
| Emergency state | `CONFIRMED` (read-only today) |
| Health, errors | `CONFIRMED` API; full 65-topic payload schema `UNKNOWN` beyond what's documented/tested |
| Door status | `CONFIRMED` API exists; not currently read by SakarC40Agent |
| Map information | `CONFIRMED` API exists (`MapComponent`) |
| Robot events/topics (65 confirmed topic names) | `CONFIRMED` topic names exist; most payload schemas `UNKNOWN` |

Do not collect or expose any capability not in this table without first updating the source studies.

### 4.5 Upload to Sakar Backend
All collected telemetry is forwarded to the Sakar Backend over authenticated HTTPS (or the protocol selected in §ARCHITECTURE §Communication). **Keenon Cloud is never the primary telemetry destination for this data path** — this is a Sakar-built forwarding layer added on top of `PeanutSdkBridge`'s existing read calls, requiring no new Keenon-side access. This is `CONFIRMED` architecturally sound: nothing in the SDK study found any conflict between reading data locally and also forwarding it elsewhere — the SDK has no concept of "who else the app tells."

### 4.6 Command handling
Agent receives authorized commands from Sakar Backend (§14 Robot Agent API), validates them (freshness, signature, scope — §5.4), and — **only for commands already confirmed safe by physical testing** — invokes the corresponding SDK call through the existing `guard()`/`OperatingMode` mechanism, extended with server-side authorization rather than (or in addition to) the current hardcoded `DIAGNOSTIC_ONLY` default.

### 4.7 Offline operation
| Scenario | Required behavior |
|---|---|
| Internet unavailable | Agent continues local SDK reads; queues telemetry locally (bounded buffer) for later upload; does not block any local-only diagnostic function |
| Sakar server unavailable | Same as above; commands cannot arrive (no path exists to receive them), which is the **safe** direction — no command execution is possible without a server round-trip in this design |
| Robot Wi-Fi unavailable | Equivalent to "internet unavailable" if Wi-Fi is the agent's uplink; local SDK link (serial/CoAP) is independent of Wi-Fi and continues per §6 |
| Peanut SDK unavailable (init/runtime failure) | Agent must surface a clear on-device error state (already partially implemented — `ConnectionStatus.INIT_FAILED`) and must not silently report stale telemetry as live |

**Governing principle (P0, non-negotiable):** local robot safety behavior (e-stop, current motor state) must never depend on Sakar Backend reachability. The Sakar Backend can *request* a lock; it cannot be a *single point of failure* for the robot already being in a safe state.

---

## 5. Cross-Cutting: Remote Lock, Security, Multi-Tenancy, Offline Behavior

### 5.1 Remote Lock System (CRITICAL)

**Flow:** Sakar Admin → Sakar Backend → Authorized Command → SakarC40Agent → Peanut SDK → C40.

**What is proven vs. not**, restated here because this is the single most important requirement in the whole document:

| Layer | Status |
|---|---|
| SDK exposes a lock API | `CONFIRMED` — `MotorComponent.enable(cb, int)`, values `MOTOR_ENABLE_LOCK=1`/`MOTOR_ENABLE_UNLOCK=0` recovered from the compiled AAR's constant pool (the vendor's own documentation never states these values) |
| SDK reports lock status | `CONFIRMED` — `MotorComponent.getStatus()` returns `255` (`CODE_LOCKED`) / `16` (button-unlocked) / `32` (app-unlocked) |
| Lock physically prevents robot movement | `REQUIRES PHYSICAL C40 TEST` — **not proven by any source material** |
| Navigation can be blocked by motor lock | `REQUIRES PHYSICAL C40 TEST` — no cross-reference between `NavigationComponent`/`PeanutNavigation` and motor-lock state was found anywhere in the SDK |
| Lock survives app restart | `REQUIRES PHYSICAL C40 TEST` — not documented |
| Lock survives robot reboot | `REQUIRES PHYSICAL C40 TEST` — not documented |
| Lock survives network/link disconnect | `REQUIRES PHYSICAL C40 TEST` — not documented |
| Stock Keenon app cannot bypass the lock | `LIKELY FALSE` as an app-only claim — the SDK's own Integration Guide treats any app holding a valid license as a peer with equal access; **`REQUIRES PHYSICAL C40 TEST`** to confirm, and `REQUIRES OS-LEVEL ENFORCEMENT` to actually guarantee |
| Unauthorized local users cannot operate the robot | Depends entirely on the above two rows — **cannot be claimed true today** |

**Requirement:** the platform's lock feature **must ship with an explicit, user-visible maturity flag** distinguishing:
- `LOCK API: Planned` (backend/API surface, UI) — buildable now, `CONFIRMED` feasible.
- `SDK implementation: Available` — the underlying `enable()` call is real and callable now.
- `Physical C40 behavior: REQUIRES TEST` — must not be marked production-ready, must not be advertised to customers as a safety guarantee, until the seven conditions below are physically validated:
  1. Robot actually stops/prevents movement when locked.
  2. Navigation cannot bypass the lock.
  3. Lock survives app restart.
  4. Lock survives robot reboot.
  5. Lock survives network disconnect.
  6. Stock Keenon application cannot bypass the lock, **or** an OS-level enforcement mechanism (kiosk/device-owner mode, stock-app removal) prevents it.
  7. Unauthorized local users cannot operate the robot through any path.

Until all seven are validated, the platform must present lock as **"requested / best-effort," not "guaranteed."** This maturity gate is itself a P0 requirement — it is cheaper and safer to build the honesty into the UI/API now than to retrofit it after a customer relies on an unverified guarantee.

### 5.2 Authorization (RBAC)

**Permissions:** `ROBOT_VIEW`, `ROBOT_CONTROL`, `ROBOT_LOCK`, `ROBOT_UNLOCK`, `ROBOT_CONFIG`, `ROBOT_DELETE`, `TASK_CREATE`, `TASK_CONTROL`, `MAP_MANAGE`, `USER_MANAGE`, `AUDIT_VIEW`.

**Role → permission matrix (baseline; refine during design review):**

| Role | VIEW | CONTROL | LOCK | UNLOCK | CONFIG | DELETE | TASK_CREATE | TASK_CONTROL | MAP_MANAGE | USER_MANAGE | AUDIT_VIEW |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `SUPER_ADMIN` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `SAKAR_ADMIN` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `SAKAR_SUPPORT` | ✓ | ✓ | ✓ | — | ✓ | — | ✓ | ✓ | ✓ | — | ✓ |
| `CUSTOMER_ADMIN` | ✓ | ✓ | ✓ | ✓ (own org only) | ✓ (own org only) | — | ✓ | ✓ | — | ✓ (own org only) | ✓ (own org only) |
| `CUSTOMER_OPERATOR` | ✓ | ✓ | ✓ | — | — | — | ✓ | ✓ | — | — | — |
| `TECHNICIAN` | ✓ | ✓ | — | — | ✓ | — | — | ✓ | — | — | — |
| `VIEWER` | ✓ | — | — | — | — | — | — | — | — | — | — |

Rationale: `ROBOT_UNLOCK` is deliberately withheld from `SAKAR_SUPPORT` and `TECHNICIAN` by default (support/technical roles should be able to *stop* a robot but escalate to an admin to *release* it) — reflects §5.1's "unlocking must be deliberately harder than locking" principle. Adjust per real organizational policy during implementation, but preserve the asymmetry.

### 5.3 Robot Authorization Hierarchy
`Organization → Site → Robot`. A user's accessible-robot set is the union of robots under every site they're granted access to, within their organization (or across organizations only for `SUPER_ADMIN`/`SAKAR_ADMIN`/`SAKAR_SUPPORT`). Every API call and every UI list must filter through this hierarchy server-side — never trust a client-supplied robot ID without an authorization check.

### 5.4 Command Security
Every robot command (lock/unlock/navigate/start/stop/etc.) must carry: command ID (UUID), robot ID, user ID, timestamp, expiration, nonce, authorization scope, and — where the transport allows it — a signature. The Agent must reject: expired commands, replayed nonces, commands for a robot ID it doesn't recognize as itself, and commands outside the authorization scope it was told to expect. See `SAKAR_ROBOT_PLATFORM_API_SPEC.md` §14 for the exact message format.

**Audit log fields (every sensitive action):** user, organization, robot, action, timestamp, IP, device, result, reason, request ID. Audit records are append-only (no update/delete path in the product surface — see `SAKAR_ROBOT_PLATFORM_DATABASE.md` `audit_logs` retention note).

### 5.5 Multi-Tenancy
```
Sakar
 ├── Customer A
 │    ├── Site 1 → Robots [...]
 │    └── Site 2 → Robots [...]
 ├── Customer B
 │    └── Site 1 → Robots [...]
 └── Sakar Internal Fleet (treated as an organization like any other)
```
**Requirement:** every table that stores robot-scoped or user-scoped data carries an `organization_id` (directly or via `site_id → robot_id` chain), and every query path enforces it at the data-access layer, not only in UI filtering. No cross-tenant data leakage is acceptable even via a crafted API request — this must be enforced server-side, unconditionally.

### 5.6 Offline / Failure Behavior Matrix

| Scenario | Expected behavior | User-visible status | Recovery | Safety behavior |
|---|---|---|---|---|
| Robot offline (no heartbeat) | Backend marks robot `OFFLINE` after a configurable timeout | Dashboard/robot-list shows `Offline`, last-seen timestamp | Automatic on next heartbeat | No commands are deliverable; last-known lock state is displayed with an explicit "unconfirmed since X" caveat |
| Cloud/backend offline | Agent buffers telemetry locally, retries upload with backoff | Mobile/web show "no recent data" banner, not fabricated live values | Automatic reconnect + buffered-data replay | Robot's own local safety state is unaffected (§4.7) |
| Internet lost (agent side) | Same as above | Same as above | Automatic | Same |
| Tablet restart | Agent re-initializes SDK on boot; reports fresh connection | Brief "reconnecting" status | Automatic | Lock state must be re-queried (`getStatus()`) and reconciled, not assumed unchanged — pending §5.1 persistence testing |
| Agent crash | OS/watchdog restarts the agent process | Gap in telemetry visible in history | Automatic restart (Android service/process-restart policy) | Same reconciliation requirement as above |
| SDK failure (init/runtime error) | Agent surfaces `INIT_FAILED`/error state; does not fabricate telemetry | Explicit error banner on tablet and reflected to backend | Manual/automatic retry per error type | Do not present stale cached values as live |
| Backend unavailable | See "Cloud/backend offline" | Same | Same | Same |
| Database unavailable | Backend degrades to read-only/cached responses where possible, rejects writes with a clear error rather than silently dropping them | API returns explicit 503-class error | Automatic on DB recovery | Commands must not be silently lost — either rejected clearly or durably queued, never both accepted and dropped |
| MQTT/WebSocket disconnect | Client-side auto-reconnect with backoff; missed messages reconciled via a subsequent full-state poll | "Reconnecting" indicator | Automatic | No command should be assumed delivered without an explicit ack (§ARCHITECTURE communication design) |
| Duplicate commands | Idempotency via command ID; second delivery of the same ID is a no-op, logged, not re-executed | N/A (transparent) | N/A | Prevents double-navigation/double-unlock from a retried request |
| Command timeout | Backend marks command `TIMED_OUT` if no ack within the configured window; does not assume success or failure | Command status shown as `Timed out`, not `Success` | Manual retry by user, or automatic per policy | Never infer a physical outcome from a timeout |
| Partial telemetry | Store whatever fields arrived; do not block ingestion waiting for a complete record | Missing fields shown as "unavailable," not zero/false | N/A | Avoids fabricating values for fields the robot didn't report |

---

## 6. Robot Tablet UX Screen Inventory
1. Boot screen
2. Agent status
3. Robot status
4. Connection status
5. Lock status
6. Diagnostic screen
7. Error screen
8. Maintenance screen

## 7. Mobile UX Screen Inventory
1. Login
2. Dashboard
3. Robot list
4. Robot details
5. Live monitoring
6. Robot control
7. Lock/unlock
8. Tasks
9. Alerts
10. Profile

## 8. Web UX Screen Inventory
1. Login
2. Dashboard
3. Organizations
4. Sites
5. Robots
6. Robot details
7. Live monitoring
8. Maps
9. Tasks
10. Cleaning
11. Alerts
12. Analytics
13. Users
14. Roles
15. Audit logs
16. Settings

---

## 9. Requirement Priority (P0/P1/P2)

**P0 (mandatory for MVP):**
- Robot registration
- Authentication (login, session, RBAC skeleton)
- Robot monitoring (status, battery, connection state)
- Telemetry ingestion and storage
- Error/health visibility
- Robot history (basic event log)
- Sakar Backend (core services: auth, robot registry, telemetry, command)
- Sakar Database (core schema)
- SakarC40Agent telemetry-forwarding capability (extends existing read paths)
- Secure agent↔backend communication (authenticated, encrypted)
- Lock/unlock **architecture** (API surface, permission model, audit trail) — explicitly **not** the physically-validated guarantee, which is a later phase gate
- Audit logging

**P1 (important, not MVP-blocking):**
- Web dashboard analytics
- Mobile push notifications
- Map visualization
- Task orchestration UI
- Cleaning schedule management UI
- Physical validation and production sign-off of lock/unlock (§5.1's seven conditions)
- Command signing/replay protection (should be designed at P0 but full HSM-grade signing can phase in at P1)

**P2 (future):**
- Restricted-zone map features (capability `UNKNOWN` in current SDK)
- Multi-robot-model abstraction beyond C40/C40 S
- Advanced analytics/ML-based predictive maintenance
- Full kiosk/device-owner OS-level lockdown rollout across the fleet
- Cross-organization fleet benchmarking

**Explicit exclusion from MVP:** any feature whose only source is "possible future capability" without a `CONFIRMED`/`LIKELY` grading in the source studies (e.g., restricted zones, autonomous multi-robot scheduling) must not be pulled into MVP scope regardless of stakeholder enthusiasm — this is a deliberate anti-scope-creep guardrail.

---

## 10. Non-Functional Requirements

| Category | Requirement |
|---|---|
| Security | TLS 1.2+ everywhere; secrets never logged; command signing per §5.4; least-privilege RBAC enforced server-side |
| Availability | Backend core services target 99.5%+ monthly availability post-launch; robot local safety must not depend on backend availability (§4.7) |
| Performance | API p95 response time < 500ms for read endpoints under nominal load; telemetry ingestion must not block on database writes (queue-and-persist pattern) |
| Scalability | Architecture must support horizontal scaling of the Telemetry/Command services independently of the Auth/User services (see `SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md`) |
| Reliability | At-least-once delivery for commands with idempotency (§5.6); no telemetry data loss beyond a bounded, documented buffer window during outages |
| Observability | Structured logging, request tracing (request ID propagated end-to-end per §5.4), metrics on command latency and telemetry freshness |
| Logging | All audit-relevant actions logged per §5.4; application logs separate from audit logs (audit logs are a product feature with retention guarantees; app logs are operational) |
| Backup | Database backups on a defined schedule (daily minimum) with tested restore procedure |
| Disaster recovery | Defined RPO/RTO targets to be set during architecture sign-off; multi-AZ or equivalent for production database |
| Data retention | Telemetry: define a hot/cold retention policy (e.g., 90 days hot, longer-term aggregated/cold); audit logs: retain per compliance requirement, minimum 1 year, append-only |
| Privacy | Robot location/site data may be commercially sensitive per customer — enforce multi-tenant isolation (§5.5) as a privacy control, not just an access control |
| API response time | See Performance row |
| Telemetry frequency | Configurable per deployment; default heartbeat interval to be set during Phase 1 based on physical testing of real link latency (`UNKNOWN` today — depends on which `LinkType` the C40 uses) |
| Command latency | Target end-to-end (dashboard click → agent receipt) under a few seconds on a healthy network; must be measured, not assumed, once the communication protocol (§ARCHITECTURE) is implemented |
| Offline behavior | Per §5.6 matrix |

---

## 11. Acceptance Criteria (representative — expand per feature during backlog grooming)

**Robot registration**
> Given an authorized admin, when a valid robot is registered with a unique serial/identity, then the robot appears in the fleet list scoped to the correct organization/site.

**Telemetry**
> Given an online C40 with a functioning SakarC40Agent, when telemetry is available from the SDK, then the Sakar Backend receives and durably stores it within the configured ingestion window, and it becomes visible on the dashboard.

**Lock (staged acceptance — do not mark the final row PASS without physical testing)**
> Given an authorized admin, when LOCK is requested, then Sakar Backend authorizes the command, Sakar Agent receives it, the Peanut SDK lock API (`MotorComponent.enable(cb, MOTOR_ENABLE_LOCK)`) is invoked, the event is stored in audit logs — **all of which can be verified in software today** — **and** the physical C40 remains immobile — **which cannot be marked PASS until the Phase 0 physical test plan (`PEANUT_SDK_C40_TECHNICAL_STUDY.md` §11) has been executed on real hardware.**

**Unlock**
> Given an authorized admin holding `ROBOT_UNLOCK`, when UNLOCK is requested, then the same chain as above executes, the robot's reported motor status transitions away from `255`, and the action is recorded with `unlocked_by`/timestamp in the lock history — physical resumption-of-normal-operation is likewise gated on physical testing.

**Audit logging**
> Given any sensitive action defined in §5.4, when it occurs, then an immutable audit record is created containing all required fields, and it is retrievable via the audit-log query API/UI by any user holding `AUDIT_VIEW` scoped to that record's organization.

**Multi-tenancy isolation**
> Given a user authorized only for Organization A, when they query any robot/telemetry/audit endpoint for a robot belonging to Organization B, then the request is rejected (403/404, not silently filtered client-side) regardless of a valid session token.

---

## 12. Risks

| Risk | Description | Mitigation direction |
|---|---|---|
| C40 SDK compatibility | Which `LinkType`/serial config the real C40 needs is `UNKNOWN` | Phase 0 physical validation before any dependent feature ships |
| Motor lock behavior | Physical lock effect entirely unconfirmed | Mandatory physical test gate before production claim (§5.1) |
| Stock Keenon app interference | Likely able to bypass an app-only lock | Plan OS-level enforcement (kiosk/device-owner) from the start |
| OTA/cloud communication | SDK's OTA subsystem and the stock app were not fully traced for external calls | Explicit "requires packet capture" caveat in all data-ownership claims (§9 of Requirements is not a section number here — see the dedicated data-ownership note above and in Architecture doc) |
| 32-bit native library | `libkeenon_serial.so` ships only `armeabi`/`armeabi-v7a` (32-bit) — no 64-bit build found in the AAR | Confirm target tablet's Android version/ABI policy supports 32-bit native libs before any OS/Android-version upgrade; treat as a hard compatibility constraint, not a preference |
| SDK vendor dependency | Peanut SDK is a closed-source, vendor-controlled `.aar` with no source access | Maintain the existing single-chokepoint (`PeanutSdkBridge`) isolation so a future SDK-version change is a contained blast radius |
| Robot firmware changes | STM32/ROS firmware updates (OTA) could change documented behavior without notice | Re-run the physical test plan after any firmware/SDK update before re-certifying lock/control features |
| Network failure | Agent↔backend link loss | Covered by §5.6 offline matrix |
| Unauthorized access | Compromised credentials/session | RBAC + MFA (§5.2/§2.2) + audit logging |
| Lock bypass | Via stock app or unmanaged device | §5.1's seven-condition gate; OS-level enforcement roadmap item |
| Device compromise | Tablet rooted/tampered | Device-management requirements (kiosk mode, app-protection) as a P1/P2 roadmap item |
| Data loss | Backend/DB outage during telemetry ingestion | Bounded local buffering on the agent (§4.7) + backend durability guarantees (§10) |
| Command duplication | Network retries causing double execution | Idempotency via command ID (§5.4/§5.6) |

---

## Executive Decision

1. **What are we building?** A Sakar-owned Robot Management Platform (mobile app, web app, robot tablet agent, and Sakar Cloud backend) that makes Sakar — not Keenon — the primary system of record for robot data, control, and access, starting with the Keenon C40/C40 S.
2. **What applications are required?** Four: Mobile Application, Web Application, Robot Android Tablet Application (evolved from the existing `SakarC40Agent`), and the Sakar Cloud/Backend.
3. **What runs on the robot?** `SakarC40Agent`, talking to the Peanut SDK locally (serial/CoAP/HTTP per `LinkType`, all `CONFIRMED` local-only) and forwarding telemetry/receiving commands from the Sakar Backend over authenticated HTTPS (or the protocol chosen in the Architecture document).
4. **What runs on Sakar Cloud?** Authentication, User, Organization, Robot Registry, Robot Command, Telemetry, Task, Map, Alert, Notification, Audit, and Analytics services (logical services; see Architecture document for the practical initial deployment shape).
5. **What runs on mobile?** A read-heavy monitoring and permission-gated control client for the same Sakar Backend APIs the web app uses — no direct robot connection, ever.
6. **What runs on web?** The primary administrative surface: fleet/organization/site/robot management, live monitoring, maps, tasks, cleaning, the dedicated Lock Management module, alerts, analytics, users/roles, and audit logs.
7. **Where is robot data stored?** Primarily in Sakar's own database, populated by `SakarC40Agent`'s forwarding of locally-read SDK telemetry. Keenon Cloud remains available as a secondary source (e.g., its already-accumulated 697 cleaning-history records) but is not the platform's primary store.
8. **Is Keenon Cloud required?** Not for the core product's primary data path. It is **not proven eliminated as a dependency for everything** — the SDK's OTA subsystem and the separate stock Keenon application were both explicitly flagged as unverified, and this document does not claim otherwise.
9. **What does Peanut SDK provide?** Confirmed local access to battery, charging, motor status/health, runtime state, work mode, odometer, robot IP, navigation status, emergency-button state, door state, map data, and 65 named event/telemetry topics — plus an SDK-level motor lock/unlock API whose physical effect is unconfirmed.
10. **How does remote lock work (as designed)?** Sakar Admin → Sakar Backend (authorizes) → SakarC40Agent (validates + gates) → Peanut SDK `MotorComponent.enable(MOTOR_ENABLE_LOCK)` → C40. The API chain is buildable today; its physical guarantee is not yet established.
11. **What remains unverified?** Real-robot position data; the full payload schema of ~49 of the SDK's 65 event topics; whether the motor lock persists across app restart/reboot/link-loss; whether the stock Keenon app or any unmanaged process can override the lock; whether any component (OTA, stock app) sends data to Keenon Cloud outside what this study examined.
12. **What must be physically tested?** The full 15-test plan in `PEANUT_SDK_C40_TECHNICAL_STUDY.md` §11, most critically: motor lock's physical effect, its persistence across restart/reboot/disconnect, navigation-under-lock behavior, and direct stock-app-vs-lock interaction.
13. **What is the MVP?** Robot registration, authentication, monitoring, telemetry, errors, history, the Sakar Backend core services, the Sakar Database core schema, SakarC40Agent telemetry forwarding, secure agent↔backend communication, the lock/unlock **architecture** (not yet the physically-certified guarantee), and audit logging — see §9 for the full P0 list.
14. **What should developers build first?** Phase 0: physical C40 SDK validation (resolve every `REQUIRES PHYSICAL C40 TEST` item above) — before any dependent feature is built on an assumption. See `SAKAR_ROBOT_PLATFORM_ROADMAP.md`.
