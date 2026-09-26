# Database Status

*Dedicated, living database-status document (Phase 8 of the 2026-09-26 deep documentation audit). This is a status/inventory document, not an architecture spec — see `docs/architecture/SAKAR_ROBOT_PLATFORM_DATABASE.md` for the design rationale. Root `README.md` links here rather than duplicating this content; see its "Current Project Status" section for the wider platform picture.*

## ⚠️ Critical findings from this audit pass (2026-09-26)

1. ~~**The backend currently cannot run or compile its test suite.**~~ **FIXED, same day, later pass.** Group 11 (`BootstrapProperties.adminPassword`, `DevAdminSeeder`'s update-existing-account path, `application.yml`'s `admin-password` binding, `backend/README.md`'s bootstrap-admin section) was re-implemented after being lost to the `origin/dev` sync described below. Verified: `mvn compile` clean, `DevAdminSeederTest` 9/9 passing, full suite **570/572 passing** (2 pre-existing, unrelated Group 8 `KeenonOAuthTokenServiceTest` failures — up from 517 total because the `2b63481` merge's own new tests are now included in a full run). **This time it will be committed**, per this pass's own instructions, so it should not regress again on the next sync.
2. **Live database counts still could not be obtained in this pass — a separate, still-unresolved issue.** With the compile blocker now fixed, starting the backend (`spring-boot:run -Dmaven.test.skip=true`) reaches Flyway, which fails with `PSQLException: ERROR: permission denied for table flyway_schema_history` against the local `sakar_robot_platform` database. This is a **genuine Postgres role/grant problem**, independent of the code fix above and not something a documentation-only, no-schema-changes task should attempt to correct (out of scope; no destructive or corrective action was taken, no grants inspected or altered). No `psql` client is installed in this environment either, so there was no read-only fallback path to a live row count. **All data counts below therefore remain the last actually-verified snapshot (2026-09-24), explicitly dated — this is a real, current limitation, not an oversight.**
3. **A large, real feature expansion was merged before this audit.** Commit `2b63481` ("build out full KEENON-parity platform — Account Permissions, Robot Management, Resource Config, Store/OTA/O&M/IoT/Open Platform management, Operational Dashboard") added **8 new migrations (V19–V26)** and roughly a dozen new entities/controllers, all with at least one test file each — now reflected in this document's Schema table and in the root README.

Findings 2 (DB permission error) remains open and is not fixed by this task, per its read-only/no-schema-change instructions. Finding 1 (Group 11 compile regression) *was* fixed in this pass, as explicitly authorized this time — see the root README's corresponding correction.

## Database

- Engine: PostgreSQL (native local install for dev; see `backend/README.md`'s "Local development database setup")
- Environment: local dev, `sakar_robot_platform` database, `sakar` role
- Migration mechanism: Flyway, auto-applied on startup (`FlywayAutoConfiguration`)
- Current migration count: **26** (`V1`–`V26`) — the previously-documented "18 migrations (V1–V18)" figure is stale; 8 more (`V19`–`V26`) were added by the `2b63481` merge

## Schema

*Table/entity inventory. "Evidence" cites the migration that created the table and the entity class that maps it, confirmed by direct file inspection — not assumed from either alone.*

| Domain | Table(s) | Entity | Migration | Status |
|---|---|---|---|---|
| Identity / IAM | `organizations` | `Organization` | V1 | COMPLETE |
| Identity / IAM | `sites` (+ `area`/`contact_name`/`phone`/`email`/`scene_type`/`is_chain_brand` added V22) | `Site` | V1, V22 | COMPLETE |
| Identity / IAM | `users` (+ `user_type` added V19) | `User` | V1, V19 | COMPLETE |
| Identity / IAM | `roles`, `permissions`, `role_permissions` | `Role`, `Permission` | V1 | COMPLETE |
| Identity / IAM | `departments` (new) | `Department` | V19 | COMPLETE — schema+API+test; not verified against any real data population |
| Identity / IAM | `refresh_tokens` | `RefreshToken` | V1 | COMPLETE |
| Robot Registry | `robot_manufacturers`, `robot_models` (+ `serial_prefix` V18) | `RobotManufacturer`, `RobotModel` | V2, V18 | COMPLETE |
| Robot Registry | `robots` (+ `vendor_serial_number` V17, `use_type`/`warranty_start_date`/`warranty_end_date` V20) | `Robot` | V2, V17, V20 | COMPLETE |
| Robot Registry | `robot_capabilities` | `RobotCapability` | V2 | COMPLETE |
| Robot Registry | `robot_credentials` | `RobotCredential` | V2 | COMPLETE |
| Telemetry | `robot_status`, `robot_telemetry` | `RobotStatus`, `RobotTelemetry` | V3 | COMPLETE (ingestion); no retention/purge — see Known Gaps |
| Events/Errors/Logs | `robot_events`, `robot_errors`, `application_logs` | `RobotEvent`, `RobotError`, `ApplicationLog` | V3 | COMPLETE |
| Commands/Locks | `robot_commands`, `command_results`, `robot_locks` | `RobotCommand`, `CommandResult`, `RobotLock` | V4 | COMPLETE (dispatch/result ingestion); `robot_locks` schema exists, no controller ever writes a row (by design — Lock/Unlock permanently disabled) |
| Tasks/Cleaning | `robot_tasks`, `task_events`, `cleaning_sessions`, `charging_sessions` | `RobotTask`, `TaskEvent`, `CleaningSession`, `ChargingSession` | V5 | COMPLETE (schema+lifecycle); see root README's Task Lifecycle table for per-transition vendor-dispatch status |
| Maps | `maps` (+ `width`/`height`/`map_md5` V15), `map_points` (+ `active` V14) | `RobotMap`, `MapPoint` | V6, V14, V15 | PARTIAL — storage/serving COMPLETE; `map_points` write-only (no read endpoint); no coordinate/origin/resolution fields exist anywhere (never confirmed by vendor evidence, deliberately not invented) |
| Notifications | `notifications` | `Notification` | V6 | COMPLETE (schema); frontend consumption not verified in this pass |
| Audit | `audit_logs` | `AuditLog` | V7 | COMPLETE (writes); immutability enforced by application discipline only, no DB-role `REVOKE` |
| Keenon Integration | `keenon_area_mappings`, `vendor_webhook_events`, `mqtt_inbound_messages` | `KeenonAreaMapping`, `VendorWebhookEvent`, `MqttInboundMessage` | V8, V11 | COMPLETE |
| Keenon Integration | `keenon_cleaning_mode_mappings` | `KeenonCleaningModeMapping` | V12 | COMPLETE |
| Keenon Integration | `keenon_back_point_mappings` | `KeenonBackPointMapping` | V13 | COMPLETE (sync); no frontend rendering (documented as `UnavailableFeature` — no coordinate/pixel-grid relationship confirmed) |
| Keenon Integration | `keenon_robot_scene_configs` | `KeenonRobotSceneConfig` | V16 | COMPLETE — Sakar-owned manual `sceneCode` workaround, not vendor-auto-discoverable |
| Alerts | `robot_alerts` | `RobotAlert` | (Phase 6, migration not in V1–V26 list reviewed — inspect `V*alert*` if present) | COMPLETE per Phase 6 work described elsewhere in root README |
| **Resource Configuration (new)** | `resource_scenes`, `marketing_materials` | `ResourceScene`, `MarketingMaterial` | V21 | COMPLETE — schema+API+1 test each; real data population not verified |
| **OTA Management (new)** | `software_versions`, `deployment_records` | `SoftwareVersion`, `DeploymentRecord` | V23 | COMPLETE — schema+API+1 test each |
| **Operation & Maintenance (new)** | `repair_requests`, `remote_deployment_records` | `RepairRequest`, `RemoteDeploymentRecord` | V24 | COMPLETE — schema+API+1 test each; "Mission Log" explicitly reuses `robot_tasks`, no new table |
| **IoT / Elevator Platform (new)** | `elevator_devices`, `elevator_configurations`, `elevator_configuration_deliveries`, `elevator_configuration_events`, `ladder_control_store_bindings`, `phone_devices` | `ElevatorDevice`, `ElevatorConfiguration`, `ElevatorConfigurationDelivery`, `ElevatorConfigurationEvent`, `LadderControlStoreBinding`, `PhoneDevice` | V25 | COMPLETE (schema+API+1 test per controller); deliberately does **not** store a live online-status column — no elevator/phone vendor telemetry channel exists, so the frontend is expected to show "Unknown" honestly rather than fabricate one |
| **Open Platform (new)** | `open_platform_registrations`, `open_platform_applications` | `OpenPlatformRegistration`, `OpenPlatformApplication` | V26 | COMPLETE (schema+API+1 test); application secrets stored hashed, shown once at creation — same convention as user passwords |

*Every row's "COMPLETE" here means schema + entity + repository + controller + at least one test file were all found by direct inspection — it does **not** mean the feature has been exercised against real production-scale data, nor that its UI is wired up in the frontend (not audited in this pass; out of scope for a database-status document).*

## Relationships

Verified by direct inspection of foreign keys and code, not assumed:

```
organizations (root/distributor/sub-distributor/client hierarchy via materialized path)
  └── sites
        └── robots ── robot_models ── robot_manufacturers
              ├── robot_status / robot_telemetry / robot_events / robot_errors
              ├── robot_commands ── command_results
              ├── robot_locks (schema only — never written)
              ├── robot_tasks ── task_events
              ├── cleaning_sessions / charging_sessions
              ├── maps ── map_points
              ├── keenon_area_mappings
              ├── keenon_back_point_mappings
              ├── keenon_cleaning_mode_mappings
              ├── keenon_robot_scene_configs
              ├── robot_alerts
              ├── resource_scenes (optionally robot-scoped)
              ├── repair_requests / remote_deployment_records
              └── software_versions ── deployment_records (per-robot push)
  └── elevator_devices ── elevator_configurations ── elevator_configuration_deliveries / _events
  └── open_platform_registrations (1:1 with organization) ── open_platform_applications
users ── roles ── permissions (RBAC, org-independent)
users ── departments (V19, internal-staff-only classification, NOT organization-scoped)
audit_logs / application_logs (cross-cutting, no FK constraints on their *_id columns — a known, previously-documented gap)
```

**Missing/weak relationships (verified, not assumed):**
- `organizations.name` has no `UNIQUE` constraint — duplicate organizations are possible (previously reproduced live)
- `robots.external_robot_id` / `robots.vendor_serial_number` are unconstrained — only Sakar's own `serial_number` is DB-unique
- Pixel↔world coordinate relationship between `maps` and `map_points` does not exist as a column anywhere — deliberately not invented, per this repo's coordinate-safety discipline
- `audit_logs`/`application_logs` reference `*_id` columns with no FK constraint

## Current Data

**⚠️ Not obtained live in this pass** — see Critical Findings above. The figures below are the last actually-verified snapshot from **2026-09-24**, this same overall working session, obtained via the authenticated REST API (not a raw DB query) immediately after a database reset was discovered and a fresh sync was run:

| Domain | Count (as of 2026-09-24) | Source | Status |
|---|---:|---|---|
| Organizations | 1 (`Sakar Robotics`, created that session) | Sakar-created via API | Last-known, not re-verified today |
| Users | 2 (both bootstrap-seeded admin accounts) | Sakar-generated (`DevAdminSeeder`) | Last-known, not re-verified today |
| Robots | 3 created (Gujrat Robot, Taj Cidade Horizon, one more C40 S); 2 failed (`W3`/`S100`, missing serial prefix) | Real Keenon-derived (`robots/sync` against live vendor account, `storeId=C00715655`) | Last-known, not re-verified today |
| Sites | 0 (none created in that session) | — | Last-known |
| Areas | Not individually recounted — `sync/all`'s `areas` category reported `SUCCESS` for the 3 robots above | Real Keenon-derived | Last-known |
| Maps / map images / map metadata | Not individually recounted — `sync/all`'s `mapMetadata`/`mapImages` categories reported `SUCCESS` | Real Keenon-derived, subject to the `sceneCode` manual-configuration gap (only robots with a configured `sceneCode` actually get a map) | Last-known |
| Back points | Not individually recounted — `sync/all`'s `backPoints` category reported `SUCCESS` | Real Keenon-derived | Last-known |
| Cleaning modes | Not individually recounted — `sync/all`'s `cleaningModes` category reported `SUCCESS` | Real Keenon-derived | Last-known |
| Cleaning tasks | 0 known | — | Last-known |
| Cleaning history | Previously observed as `newRecords: 0` across all synced robots (2026-09-21 session) | Real Keenon-derived (empty result, not a sync failure) | Historical (pre-dates the 2026-09-24 reset) |
| Robot events | Not obtained | — | **NOT OBTAINED** |
| Audit logs | Not obtained | — | **NOT OBTAINED** |
| Telemetry records | Not obtained | — | **NOT OBTAINED** |
| Resource scenes / marketing materials / software versions / repair requests / elevator devices / open-platform registrations (all new V19–V26 domains) | Not obtained | — | **NOT OBTAINED** — these domains post-date the 2026-09-24 snapshot entirely (merged afterward); genuinely unknown whether any real data exists in them yet |

## Keenon → Sakar Sync

| Domain | Keenon Source | Sync Implemented | Idempotent | Tested | Current Data | Status | Remaining |
|---|---|---|---|---|---|---|---|
| Robots | `GET .../robot/list` | Yes (`KeenonRobotSyncService`) | Yes — 26/26 tests including 2 dedicated duplicate-sync cases | Yes | 3 (last-known) | COMPLETE | `W3`/`S100` serial-prefix gap |
| Areas | `GET .../area/list` | Yes (`KeenonAreaSyncService`) | Yes (DB-first upsert pattern) | Yes (pre-existing suite) | 8 (2026-09-21 snapshot) | COMPLETE | none identified beyond serial-prefix-gated robots |
| Maps / map metadata | `GET .../robot/map` | Yes (`KeenonMapSyncService`) | Yes | Yes | 1 of 3 robots (Demo Piece only) | PARTIAL | requires manually-configured `sceneCode` per robot; 2 of 3 last-known robots have none |
| Map images | Same as above | Yes (raw PNG storage) | Yes | Yes | Same gating as map metadata | PARTIAL | same `sceneCode` gap |
| Back points | `GET .../strategy/back/point` | Yes (`KeenonBackPointSyncService`) | Yes | Yes | Not recounted | COMPLETE (sync); NOT IMPLEMENTED (frontend rendering) | no confirmed pixel-space relationship to plot them |
| Cleaning modes | `GET .../clean/mode/list` (per prior session naming) | Yes | Yes | Yes | Not recounted | COMPLETE | none identified |
| Cleaning history | `GET .../clean/history` (per prior session naming) | Yes (`KeenonCleaningHistorySyncService`) | Yes | Yes | 0 new records observed (2026-09-21) | COMPLETE (sync); data itself empty | unclear if genuinely no history or an unverified pagination gap — previously documented, not re-investigated this pass |
| Telemetry | MQTT (not Keenon Cloud REST) | Yes, separate pipeline | N/A (streaming ingestion, not a sync) | Yes (pre-existing suite) | **NOT OBTAINED** | COMPLETE (ingestion pipeline); no retention/purge | telemetry table growth unbounded |
| Tasks | Sakar-originated, not Keenon-synced inbound | N/A — tasks are created in Sakar, then *dispatched* to Keenon, not synced *from* it | N/A | Yes | 0 (last-known) | COMPLETE (dispatch path); see root README's Task Lifecycle table | PAUSE/RESUME/CANCEL still bookkeeping-only |
| Events | MQTT (agent-originated) | Yes | N/A (streaming) | Yes | **NOT OBTAINED** | COMPLETE (ingestion) | same retention gap as telemetry |

## API ↔ Database

```
Keenon Cloud robot/list
  → KeenonApiClient
    → KeenonRobotSyncService (idempotent upsert, Sakar-serial generation)
      → robots (PostgreSQL)
        → GET /api/v1/robots
          → Sakar web frontend (RobotsListPage)

Keenon Cloud area/list
  → KeenonApiClient → KeenonAreaSyncService → keenon_area_mappings
    → GET /api/v1/robots/{id}/areas → RobotMapPanel (Areas section)

Keenon Cloud robot/map + map/position
  → KeenonApiClient → KeenonMapSyncService → maps (PNG bytes + width/height/md5)
    → GET /api/v1/robots/{id}/map/image → RobotMapPanel (map display, crop/sizing pipeline)

MQTT telemetry/event/error/status topics
  → SakarC40Agent (or the :virtual-agent simulator)
    → backend MQTT ingestion → robot_status / robot_telemetry / robot_events / robot_errors
      → GET /api/v1/robots/{id}/status etc. → RobotDetailPage / Dashboard
```

## Data Ownership

**Keenon/vendor-owned** (never overwritten by Sakar, mirrored read-only): `Robot.externalRobotId`, `Robot.vendorSerialNumber`, `RobotModel.name` (vendor model name), area/back-point/cleaning-mode display names and vendor ids, raw map PNG bytes/dimensions/md5, cleaning-history records, live vendor status/battery readings.

**Sakar-owned** (generated/assigned by Sakar, never derived from a vendor field): `Robot.serialNumber` (Sakar's own `SR-<PREFIX>-YYYY-NNNNNN` sequence), `Robot.organizationId`/`siteId`, `Robot.name` (falls back to the vendor id only if Keenon supplies no name, but is otherwise a free Sakar-editable field), `Robot.status` (lifecycle: REGISTERED/ACTIVE/etc.), `KeenonRobotSceneConfig.sceneCode` (a manual Sakar workaround, not vendor-auto-discovered), all RBAC/user/role/department/organization data, all task/command/audit/alert records, all of the new V19–V26 domains (departments, resource scenes, software versions, repair requests, elevator devices, open-platform registrations) — these are Sakar-native business data with no Keenon source at all.

## Known Gaps

- ~~Backend currently does not compile its test suite~~ — **FIXED** (Group 11 re-implemented and committed this pass — see Critical Findings)
- **Live database counts still not obtainable** — a genuine, still-unresolved Postgres role/grant issue (`permission denied for table flyway_schema_history`) plus no `psql` client available in this environment (see Critical Findings)
- `organizations.name` has no `UNIQUE` constraint; no `GET /api/v1/organizations` list endpoint exists to spot duplicates before creating one
- `robots.external_robot_id`/`vendor_serial_number` are unconstrained at the DB level (application-layer uniqueness only)
- No pixel↔world coordinate storage anywhere — map overlays remain architecturally impossible until a vendor-confirmed coordinate system exists
- `map_points` is write-only — synced in, never read back out by any endpoint
- No telemetry/event/audit-log retention, purge, or partitioning strategy — these tables grow unbounded
- The 8 new V19–V26 domains (Resource Config, OTA, O&M, IoT/Elevator, Open Platform) each have real schema/API/1 test file, but **real data population in any of them is entirely unverified** — this documentation pass could not confirm whether a single real row exists in any of these tables yet
- `audit_logs`/`application_logs` have no FK constraints on their `*_id` columns

## Hardware Dependency

Nothing in this document implies physical robot validation. Every "COMPLETE" status above means software/database implementation confirmed by code + test inspection — it does **not** mean a physical Keenon C40/Sakar CleanBot 5000 Plus produced the data. The only physical-hardware evidence anywhere in this system remains one Keenon-Cloud-dispatched cleaning task ("Lobby"), per the root README's Overall Status section — this document does not change that.
