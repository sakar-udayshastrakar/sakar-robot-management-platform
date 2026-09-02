# Sakar–Keenon Web Platform Gap Analysis

**Status: RESEARCH ONLY. No code changed to produce this document.** Every claim below is sourced from the actual repository (backend Java, frontend TypeScript, Flyway SQL migrations) or from reverse-engineering reports already committed in this repo or its immediate parent directory. Nothing here is derived from generic Keenon documentation or invented. Where the evidence was ambiguous or a specific behavior wasn't directly confirmed by the audit, it is marked **❓ NEEDS VERIFICATION** rather than assumed.

Legend: ✅ Done and wired end-to-end · ⚠️ Partial / exists but incomplete · ❌ Missing entirely · 🔒 Deliberately deferred (physical-safety or vendor-availability reason) · ❓ Needs verification

---

## 1. Current architecture

```
React + TypeScript (web/)
        ↓  axios (web/src/api/client.ts) — Bearer JWT
Sakar Backend (backend/, Spring Boot, Java 21)
        ↓                                   ↓
RobotAdapter interface              MQTT (Eclipse Paho, Mosquitto broker)
   ↓              ↓                          ↓
KeenonRobotAdapter   SakarRobotAdapter    SakarC40Agent (Android, robot/SakarC40Agent/)
   ↓ OAuth2 REST         (stub, no             ↓
Keenon Cloud            local impl yet)   PeanutSdkBridge → Keenon C40 (Peanut SDK, on-device)
(cloud.robotkeenon.com)
```

Two genuinely distinct integration paths already exist, selected per robot model via `AdapterType`/`IntegrationPath` (`robot_models` table):
- **`KEENON_CLOUD` / `KEENON_CLOUD_DEPENDENT`** — Sakar backend calls Keenon's own cloud REST API over the internet (OAuth2 client-credentials). This is what today's one seeded robot model (`C40 S`) uses.
- **`SAKAR_NATIVE` / `SAKAR_OWNED_LOCAL`** — Sakar's own Android agent (`SakarC40Agent`) talks to the Peanut SDK directly on-device and reports back to the Sakar backend over MQTT, bypassing Keenon Cloud entirely. Only `GO_TO_POINT` and `RETURN_TO_DOCK` are wired this way today, both **SOFTWARE TEST VERIFIED, physical validation NOT PERFORMED**.

Data stores: **PostgreSQL** (27 tables, system of record — see §6), **Redis** (Keenon OAuth token cache + MQTT rate-limit windows), **MQTT broker** (command/telemetry transport for the native path only).

`RobotAdapterRegistry`/`RobotAdapter` is the seam meant to keep Keenon-specific HTTP calls out of the rest of the backend — confirmed to exist with `KeenonRobotAdapter` as the only real implementation. ❓ **NEEDS VERIFICATION**: whether `RobotCommandController`/`RobotCommandService` actually routes a command to `RobotAdapterRegistry`/`KeenonRobotAdapter` for a `KEENON_CLOUD`-adapter robot, or whether the command-issuance path today only feeds the MQTT/agent pipeline. Neither backend audit pass traced this specific dispatch branch — verify before assuming Keenon-Cloud-backed cleaning control is reachable through `POST /api/v1/robots/{id}/commands` today.

## 2. Existing frontend modules

React Router routes, from `web/src/app/App.tsx` + `web/src/components/layout/navConfig.ts`:

| Module | Route(s) | Status |
|---|---|---|
| Dashboard | `/dashboard` | ⚠️ Partial — several KPI tiles (Locked/Faulted/Cleaning/Charging) honestly render `—`/"Unavailable" rather than fake numbers; one card ("Recent Robot Events") is simulated |
| Organizations | `/organizations`, `/organizations/:id` | ✅ Real, but no list-all endpoint (see §11) |
| Sites | `/sites` | ✅ Real, but no list-all endpoint; "Alerts" column is a hardcoded `"Simulated"` string |
| Robots | `/robots`, `/robots/:id` (12 tabs) | ✅ Real for overview/status; several tabs (telemetry/events/errors/logs/timeline) are simulated |
| Fleet Map | `/fleet` | 🔒 Deliberate "unavailable" stub — no fake coordinates |
| Tasks | `/tasks` | ✅ Real, full lifecycle |
| Cleaning | `/cleaning` | ⚠️ Partial — history only confirmed (`GET /cleaning/history`) |
| Telemetry | `/telemetry` | ❌ Simulated (`generateTelemetry()`) |
| Alerts | `/alerts` | ✅ Real, full CRUD |
| Events | `/events` | ❌ Simulated |
| Errors | `/errors` | ❌ Simulated |
| Logs | `/logs` | ❌ Simulated (nav tags it "unavailable," page actually shows fake data — inconsistency to fix) |
| Analytics | `/analytics` | 🔒 Deliberate stub, explicit "no API exists" |
| Users | `/users` | ✅ Real |
| Roles / Permissions | `/roles`, `/permissions` | ✅ Real (read-mostly) |
| Audit Logs | `/audit` | ✅ Real |
| Settings | `/settings` | ⚠️ Minimal — session/account display only |
| **Reports** | — | ❌ **No route, no page, no nav item — does not exist** |
| **Inventory** (robots/consumables/spare parts) | — | ❌ **Does not exist** |
| **Scheduling** | — | ❌ **Does not exist** |
| **Distributor management** (dedicated) | — | ❌ Does not exist as its own screen — `DISTRIBUTOR`/`SUB_DISTRIBUTOR` are just values in the generic Organization type dropdown |
| **API/Integration status** | — | ❌ Does not exist |

Shell: `AppShell.tsx` + `TopNav.tsx` + `Sidebar.tsx`, nav driven by a typed `navConfig.ts` array with per-item permission gating and `SIM`/`SOON` badges — a real, reusable pattern for adding new modules.

## 3. Existing backend modules

13 REST controllers, 34 endpoints total (`backend/src/main/java/com/sakarrobotics/cloud/`):

`AuthController`, `UserController`, `RoleController`, `OrganizationController`, `SiteController`, `RobotController`, `RobotCommandController`, `RobotTaskController`, `CleaningController` (history-only), `AlertController`, `AuditController`, `KeenonWebhookController`.

Entities that exist with **no controller/service wired at all**:
- `lock` package (`RobotLock` — schema-only; 🔒 deliberately unwired, matches the project's own physical-actuation caution)
- `map` package (`RobotMap`, `MapPoint` — schema-only, no logic)
- `notification` package (`Notification` — schema-only)
- `robot_telemetry` time-series table — populated by MQTT ingestion, **no REST endpoint to read it back**
- `robot_events`, `robot_errors`, `application_logs` (the `srels` package) — populated by ingestion, **no REST endpoint**

This last group is the direct backend-side cause of 4 of the frontend's simulated pages (Telemetry/Events/Errors/Logs) — the data already exists in Postgres, it's just not exposed. See §15 for why this is the recommended first fix.

## 4. Existing Keenon API integration

`backend/src/main/java/com/sakarrobotics/cloud/integration/keenon/` — `KeenonRobotAdapter`, `KeenonApiClient`, `KeenonOAuthTokenService`, `KeenonProperties`/`KeenonConfig`, `KeenonWebhookController`, `KeenonAreaMapping`(+repo), `VendorWebhookEvent`(+repo).

- **Auth**: OAuth2 `client_credentials` grant, token cached in Redis with vendor-supplied TTL. Credentials only via env vars (`SAKAR_KEENON_CLIENT_ID`/`SECRET`), never hardcoded. **Integration disabled by default** (`sakar.integration.keenon.enabled=false`).
- **Outbound** (Sakar → Keenon Cloud): robot status, battery, cleaning status, area list, cleaning modes, back-points, cleaning logs (all reads), plus start/stop/pause/recharge task (writes) — see §5 for exact paths.
- **Inbound**: `POST /integrations/keenon/webhooks/{eventType}`, shared-secret header auth (not JWT), idempotent by SHA-256 dedup key, raw payload stored — **normalization into typed `robot_events` rows is explicitly deferred**, not yet implemented.
- `resumeTask` throws `UNSUPPORTED_CAPABILITY`; `getMap` throws not-implemented — **even though the external live Keenon Cloud API audit for this account reportedly found a working map endpoint** (per the Keenon integration report), it was never wired into `KeenonRobotAdapter`. Flagged as a concrete near-term opportunity.
- `lock`/`unlock` have no method at all on this adapter — Keenon Cloud does not expose motor control.

## 5. Verified Keenon endpoints

All confirmed present in `KeenonApiClient.java` as real HTTP calls against `https://cloud.robotkeenon.com` (configurable):

| Method | Path | Purpose | Wired to adapter? |
|---|---|---|---|
| POST | `/api/open/oauth/token` | OAuth2 token | ✅ |
| GET | `/api/open/scene/v1/robot/status` | Robot status | ✅ |
| GET | `/api/open/custom/robot/battery/level` | Battery | ✅ |
| GET | `/api/open/custom/clean/robot/status` | Cleaning status | ✅ |
| GET | `/api/open/custom/clean/robot/area/list` | Area list | ✅ |
| GET | `/api/open/custom/clean/robot/strategy/clean/model` | Cleaning modes | ✅ |
| GET | `/api/open/custom/clean/robot/strategy/back/point` | Back/charge points | ✅ |
| GET | `/api/open/custom/clean/log/list` | Cleaning logs/history | ✅ |
| POST | `/api/open/custom/clean/robot/strategy/temporary/task` | Start task | ✅ |
| POST | `/api/open/custom/clean/robot/finish/task` | Stop task | ✅ |
| POST | `/api/open/custom/clean/robot/pause/task` | Pause task | ✅ |
| POST | `/api/open/custom/clean/robot/recharge/task` | Return to dock | ✅ |
| — | (resume) | No confirmed vendor endpoint | 🔒 `UNSUPPORTED_CAPABILITY` |
| — | (map) | Reportedly available per external audit, not wired | ❌ throws not-implemented |

On the robot-agent/Peanut-SDK side (local, non-Cloud path): `RETURN_TO_DOCK` and `GO_TO_POINT` are OFFICIAL SDK VERIFIED and wired end-to-end (SOFTWARE TEST VERIFIED, physical NOT PERFORMED). **`START_CLEANING` is confirmed NOT AVAILABLE anywhere in the officially-distributed Peanut SDK AAR** — no `clean()` accessor exists in the SDK's public API surface at all (verified via full `javap` decompilation, ~1399 classes). This does **not** mean cleaning control is impossible for Sakar overall — it's available today only through the Keenon Cloud REST path above, not through the on-device SDK.

## 6. Current database schema

27 tables across 9 Flyway migrations (V1–V8, V11; V9/V10 are seed-only). Full inventory:

- **Core/IAM (V1)**: `organizations`, `sites`, `roles`, `permissions`, `role_permissions`, `users`, `refresh_tokens`
- **Robot registry (V2)**: `robot_manufacturers`, `robot_models`, `robot_capabilities`, `robots`, `robot_credentials`
- **Telemetry/SRELS (V3)**: `robot_status`, `robot_telemetry`, `robot_events`, `robot_errors`, `robot_alerts`, `application_logs`
- **Commands/locks (V4)**: `robot_commands`, `command_results`, `robot_locks`
- **Tasks/cleaning (V5)**: `robot_tasks`, `task_events`, `cleaning_sessions`, `charging_sessions`
- **Maps/notifications (V6)**: `maps`, `map_points`, `notifications`
- **Audit (V7)**: `audit_logs`
- **Keenon (V8)**: `keenon_area_mappings`, `vendor_webhook_events`
- **MQTT (V11)**: `mqtt_inbound_messages`

`organizations.parent_organization_id` is a genuine self-referential FK, plus a materialized `path` column (indexed for prefix search) — the distributor/sub-distributor/client tree is a real adjacency structure already, not a flat tenant list. **However**: `org_type VARCHAR(32)` has **no DB-level CHECK constraint**; the only literal value ever seeded is `'SAKAR_ROOT'` (for the one seeded "Sakar Robotics" org). The `DISTRIBUTOR`/`SUB_DISTRIBUTOR`/`CLIENT`/`DIRECT_CLIENT`/`INTERNAL` values exist only as a Java enum (`OrganizationType.java`) — enforced at the application layer, not the database layer. Minor hardening gap, not a missing feature.

**No inventory, consumables, spare-parts, warehouse, or maintenance-record table exists anywhere in the schema.**

## 7. Current authorization hierarchy

This is more built than the task description assumed. Already in place:

- **`TenantAccessGuard`** (`security/access/TenantAccessGuard.java`) — the single enforcement chokepoint. `hasOrganizationAccess`/`assertOrganizationAccess`/`accessibleOrganizationIds`, all backed by the `path`-prefix descendant check (`OrganizationService.isSameOrDescendant`). `SUPER_ADMIN` bypasses (returns `null` = unrestricted); every other role is bounded to its own org subtree.
- **RBAC**: 6 roles (`SUPER_ADMIN, ORG_ADMIN, SITE_ADMIN, OPERATOR, TECHNICIAN, VIEWER`), 13 permissions (`ROBOT_VIEW, ROBOT_CONTROL, ROBOT_TASK_CREATE, ROBOT_TASK_CANCEL, ROBOT_LOCK, ROBOT_UNLOCK, ROBOT_CONFIGURE, ROBOT_DIAGNOSTICS, ROBOT_LOG_VIEW, AUDIT_VIEW, USER_MANAGE, ROLE_MANAGE, SYSTEM_ADMIN`), enforced via `@PreAuthorize` on every controller method.
- **Enforcement is backend-only, not frontend-trust**: the frontend's `hasPermission()` check is explicitly documented in its own source comment as UI-only ("the backend re-validates... nothing here is ever treated as an authorization decision").
- **Not-found-not-forbidden pattern**: cross-tenant access to a robot/user by id returns 404, never a distinguishable 403 — deliberate anti-enumeration design, already tested (`RobotControllerSecurityTest`).
- **Auditability**: `AuditService.record()` is already called from 8 services (user create/suspend/activate/role-change, auth, commands, tasks, alerts, credentials) — privileged actions are already logged.

Existing security-boundary tests: `RobotControllerSecurityTest`, `OrganizationControllerSecurityTest`, `OrganizationHierarchyTest` — cover cross-org robot access, org-creation privilege escalation, and path-prefix logic.

**Gaps against the requested hierarchy rules:**
- ❓ **NEEDS VERIFICATION**: whether `OrganizationController.create` restricts *which* `org_type` a caller may assign to a new child org (e.g., stopping a `DISTRIBUTOR`-scoped `ORG_ADMIN` from creating another top-level `DISTRIBUTOR`, or a `CLIENT` from spawning a `SUB_DISTRIBUTOR`). The audit confirmed generic subtree-scoping ("can create under own subtree") but not type-specific rules — this needs a direct code read/test before relying on it.
- **Single global role per user** (`users.role_id`, not a per-organization assignment) — there is no way today to say "this user is `ORG_ADMIN` for Distributor X only, `VIEWER` everywhere else." Adequate for the current 6-role model but worth flagging if finer per-tenant role delegation is wanted later.
- `audit_logs` has no DB-level `REVOKE` hardening against UPDATE/DELETE (the migration's own comment calls this out as a known limitation).
- Documented limitation in `TenantAccessGuard`'s own Javadoc: scoping stops at organization level — no narrower per-site or per-robot assignment within an org yet.

## 8. Existing simulated data

Single source: `web/src/mocks/simulated.ts`, always paired with a `<SimulatedDataBanner/>`. Confirmed fabricated:

- `generateTelemetry()` — battery %/odometer via `Math.random()`, status text from a fixed random pool. Used by `/telemetry` and the Robot Detail Telemetry tab.
- `generateEvents()` — random severity/category/message. Used by `/events`, Robot Detail Events tab, **and one Dashboard card** ("Recent Robot Events").
- `generateErrors()` — random error codes/severity/status. Used by `/errors` and Robot Detail Errors tab.
- `generateLogs()`/`generateRobotLogs()` — random log lines. Used by `/logs` and Robot Detail Logs tab.
- `generateTimeline()` — random interleaved timeline entries. Used only by the Robot Detail Timeline tab.
- `SitesPage.tsx` — "Alerts" column hardcodes the literal string `"Simulated"`.

Honest non-fabrication (worth noting as the right pattern to reuse): Dashboard's Locked/Faulted/Cleaning/Charging KPI tiles and the Fleet Map page render `"—"`/an explicit "Unavailable" state rather than fake numbers, each with a reason shown to the user.

## 9. Real-data integrations already completed

Backend↔frontend, confirmed live end-to-end: Auth (login/refresh/logout), Organizations (create/get/children), Sites (create/list-by-org), Robots (full CRUD + status + MQTT credential issuance), Robot Commands (issue/get/list), Robot Tasks (full lifecycle: create/start/pause/resume/stop/cancel), Cleaning history, Alerts (list/acknowledge/resolve), Users (full CRUD + suspend/activate/role-change), Roles (read), Permissions (read), Audit Logs (list).

Backend↔external, confirmed real code (not mocked), currently disabled-by-default: Keenon Cloud OAuth2 + the 12 endpoints in §5. Backend↔robot, confirmed real: MQTT command/telemetry/heartbeat pipeline plus `GO_TO_POINT`/`RETURN_TO_DOCK` against the physical Peanut SDK (SOFTWARE TEST VERIFIED, physical NOT PERFORMED).

## 10. Missing modules

| Module (from your list) | Status |
|---|---|
| Dashboard | ⚠️ Exists, partially real |
| User Management | ✅ Exists (Sakar/distributor/client all modeled as `Organization`+`User`; no dedicated "distributor" sub-screen) |
| Robot Management | ✅ Exists |
| Cleaning Operations | ⚠️ Partial — task lifecycle + Keenon Cloud adapter calls exist; ❓ needs verification whether cleaning start/pause/stop in the UI actually reaches `KeenonRobotAdapter` today (see §1) |
| Cleaning History | ✅ Exists |
| Scheduling | ❌ Does not exist — no backend table, no controller, no frontend page |
| Maps / Areas | ❌ Schema exists (`maps`, `map_points`), everything else unwired |
| Alerts / Faults | ✅ Exists |
| Inventory | ❌ Does not exist at any layer |
| Reports | ❌ Does not exist at any layer |
| Audit Logs | ✅ Exists |
| API / Integration status | ❌ Does not exist |

## 11. Missing APIs

- `GET /api/v1/organizations` (list-all) — only get-by-id and get-children exist today; frontend already "works around" this gap.
- `GET /api/v1/sites` (list-all, org-independent) — only list-by-organizationId exists.
- `GET /api/v1/robots/{id}/telemetry` — table exists (`robot_telemetry`), no endpoint.
- `GET /api/v1/robots/{id}/events`, `/errors`, `/logs` — tables exist (`robot_events`, `robot_errors`, `application_logs`), no endpoints. **These four are the single highest-leverage gap in this whole analysis** — closing them de-simulates 4 existing frontend pages with zero new architecture.
- Inventory/consumables/spare-parts CRUD — none exist.
- Reports/aggregation endpoints — none exist.
- Scheduling CRUD — none exist.
- Maps/Areas CRUD — entities exist, no service/controller.
- `KeenonRobotAdapter.getMap()`/`resumeTask()` — stubbed, not implemented.
- Lock/Unlock — 🔒 deliberately unimplemented (physical-safety deferral, consistent with project convention — not recommended for this phase).

## 12. Missing database tables

- Inventory-specific tables: none exist. `robots` (registry) already carries `organization_id`/`site_id`/`serial_number`, but nothing tracks purchase/warranty/allocation history distinct from current assignment.
- `consumables` (item/SKU/quantity/min-stock/assigned client-site/usage records) — does not exist.
- `spare_parts` (part number/name/quantity/min-stock/warehouse-location/assigned robot/maintenance history) — does not exist.
- `schedules`/`scheduled_tasks` (if Scheduling is modeled as recurring jobs distinct from one-off `robot_tasks`) — does not exist.
- No new table is obviously needed for Reports if reports are computed on demand from existing tables (`robot_tasks`, `cleaning_sessions`, `robot_alerts`, `audit_logs`) — flag this as a design decision rather than an assumed gap.
- Hardening-only, not a new feature: a CHECK constraint or lookup table for `organizations.org_type` and other free-text status columns (currently app-enforced only).

## 13. Missing frontend pages

Inventory (3 sub-views: robots inventory, consumables, spare parts), Reports, Scheduling, a dedicated Distributor management screen (optional — could also be a filtered view of the existing Organizations page), API/Integration status dashboard, a real Maps/Areas page (replacing the Fleet Map stub once backend support exists). Additionally, Telemetry/Events/Errors/Logs pages already exist and just need their data source swapped from `simulated.ts` to the new endpoints in §11 once built — not new pages, just real wiring.

## 14. Security / tenant-isolation gaps

1. ❓ Org-type-based creation restrictions (which `org_type` a given caller may create) — unconfirmed, verify before building distributor/sub-distributor self-service creation on top of it.
2. Single global role per user — no per-organization role scoping; acceptable today, a real limitation if finer per-tenant delegation is later required.
3. No DB-level protection against `audit_logs` mutation (app-layer-only "never UPDATE/DELETE" convention).
4. Authorization scoping stops at organization level — no per-site/per-robot narrower grant within an org (documented, known limitation in the code itself).
5. New Inventory/Scheduling/Reports modules will need their own `TenantAccessGuard`-based scoping and security tests from day one — none of this is automatic, it has to be applied per new entity/controller exactly like `RobotControllerSecurityTest` already demonstrates the pattern for.
6. `org_type`/most status columns have no DB CHECK constraint — defense-in-depth gap, low severity given consistent Java enum usage today.

## 15. Recommended implementation order

Your requested order (Phase A → I) is sound as a target shape, but current-state evidence shows several of those phases are already substantially done. Recommended sequencing, adjusted for actual state:

1. **Quick wins (new, not in your original list, highest leverage-to-effort ratio)**: add the 4 read-only endpoints in §11 (`telemetry`, `events`, `errors`, `logs` history) — de-simulates 4 existing pages with zero new architecture, using tables and MQTT ingestion that already exist and are already populated.
2. **Phase A (Auth + hierarchy + authorization)** — ~80% done. Remaining work is verification and hardening, not net-new build: confirm/add org-type-based creation restrictions with explicit tests (item 14.1), decide whether per-org role scoping is actually needed now (recommend: not yet), add a DB CHECK constraint for `org_type`.
3. **Phase B (Robot Management + Keenon adapter)** — Robot Management is done. For the adapter: verify the `RobotCommandController` → `RobotAdapterRegistry` → `KeenonRobotAdapter` dispatch path (§1 open question) before building UI on top of it; wire `getMap()` since the external audit reportedly already found a working endpoint.
4. **Phase C (Robot status/dashboard)** — largely done; folds into the quick wins above.
5. **Phase D (Cleaning operations + tasks)** — backend task lifecycle is done; resolve the Cleaning-page-to-adapter dispatch question from §1, then wire real start/pause/stop buttons if not already reaching the backend.
6. **Phase E (Maps/areas)** — genuine gap, schema exists but nothing else does; real phase of new work.
7. **Phase F (Cleaning history + alerts)** — essentially complete already.
8. **Phase G (Scheduling)** — genuine gap, build from scratch (DB + backend + frontend).
9. **Phase H (Inventory)** — genuine gap, build from scratch (DB + backend + frontend), following the multi-tenant scoping pattern already established for robots.
10. **Phase I (Reports + audit)** — Audit is done; Reports is a genuine gap, likely computed from existing tables rather than a new store.

No further code changes were made to produce this document. Awaiting review before starting implementation on any module above.
