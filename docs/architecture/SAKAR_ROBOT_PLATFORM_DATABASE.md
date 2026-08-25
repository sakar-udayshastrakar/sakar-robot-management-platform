# Sakar Robot Management Platform — Database Schema

Companion to `SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md`. Design document only — no schema has been created, no migration has been run. Types are given in PostgreSQL-flavored notation as a reference baseline; adjust to the actual chosen RDBMS during implementation without changing the logical model described here.

**Conventions used throughout:**
- Every table has a surrogate primary key `id` (`UUID`, default random) unless noted otherwise.
- Every table has `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`; mutable tables also have `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`. Append-only tables (events, telemetry, audit logs, command results) omit `updated_at` deliberately — they are never updated in place.
- Multi-tenant isolation is carried either directly (`organization_id`) or transitively via `site_id`/`robot_id` — every table below states which.
- "Retention" notes are directional guidance for §ARCHITECTURE §5 / §REQUIREMENTS §10, not final numbers — finalize during architecture sign-off.

---

## 1. `users`
| Field | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `organization_id` | UUID, FK → `organizations.id`, nullable | Null for Sakar-internal staff roles (`SUPER_ADMIN`, `SAKAR_ADMIN`, `SAKAR_SUPPORT`) who aren't scoped to a single customer org |
| `email` | TEXT, UNIQUE, NOT NULL | Login identifier |
| `password_hash` | TEXT, NOT NULL | Never store plaintext; standard slow hash (bcrypt/argon2) |
| `full_name` | TEXT | |
| `role_id` | UUID, FK → `roles.id`, NOT NULL | |
| `mfa_enabled` | BOOLEAN, NOT NULL DEFAULT false | |
| `mfa_secret` | TEXT, nullable, encrypted at rest | |
| `status` | TEXT (`active`/`suspended`/`invited`) | |
| `last_login_at` | TIMESTAMPTZ, nullable | |
| `created_at`, `updated_at` | TIMESTAMPTZ | |

**Indexes:** unique on `email`; index on `organization_id`. **Retention:** indefinite while account active; anonymize on deletion request rather than hard-delete if referenced by audit logs (see `audit_logs`).

## 2. `roles`
| Field | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `name` | TEXT, UNIQUE, NOT NULL | `SUPER_ADMIN`, `SAKAR_ADMIN`, `SAKAR_SUPPORT`, `CUSTOMER_ADMIN`, `CUSTOMER_OPERATOR`, `TECHNICIAN`, `VIEWER` |
| `description` | TEXT | |
| `created_at`, `updated_at` | TIMESTAMPTZ | |

## 3. `permissions`
| Field | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `code` | TEXT, UNIQUE, NOT NULL | `ROBOT_VIEW`, `ROBOT_CONTROL`, `ROBOT_LOCK`, `ROBOT_UNLOCK`, `ROBOT_CONFIG`, `ROBOT_DELETE`, `TASK_CREATE`, `TASK_CONTROL`, `MAP_MANAGE`, `USER_MANAGE`, `AUDIT_VIEW` |
| `description` | TEXT | |

**`role_permissions`** (join table): `role_id UUID FK`, `permission_id UUID FK`, composite PK `(role_id, permission_id)`. Encodes the matrix in `SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md` §5.2.

## 4. `organizations`
| Field | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `name` | TEXT, NOT NULL | |
| `type` | TEXT (`customer`/`internal`) | Distinguishes Sakar's internal fleet from customer orgs |
| `status` | TEXT (`active`/`suspended`) | |
| `created_at`, `updated_at` | TIMESTAMPTZ | |

## 5. `sites`
| Field | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `organization_id` | UUID, FK → `organizations.id`, NOT NULL | Tenant boundary |
| `name` | TEXT, NOT NULL | |
| `address` | TEXT, nullable | |
| `timezone` | TEXT | For schedule/report localization |
| `created_at`, `updated_at` | TIMESTAMPTZ | |

**Index:** `organization_id`.

## 6. `robot_models`
| Field | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `vendor` | TEXT, NOT NULL | e.g. `Keenon` |
| `model_name` | TEXT, NOT NULL | e.g. `C40 S` |
| `sdk_name` | TEXT | e.g. `Peanut SDK` |
| `sdk_version` | TEXT | e.g. `1.3.0` (label) / `1.5.0-bate1` (AAR-reported) |
| `capabilities` | JSONB | Model-specific capability flags (e.g., has-cabin, has-cleaning), sourced from the API matrix, not invented per robot |
| `created_at`, `updated_at` | TIMESTAMPTZ | |

**Purpose:** the extensibility seam described in `SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md` §6 — new robot models are added as rows here, not as schema changes elsewhere.

## 7. `robots`
| Field | Type | Notes |
|---|---|---|
| `id` | UUID, PK | Sakar-internal identity |
| `organization_id` | UUID, FK → `organizations.id`, NOT NULL | Tenant boundary |
| `site_id` | UUID, FK → `sites.id`, NOT NULL | |
| `robot_model_id` | UUID, FK → `robot_models.id`, NOT NULL | |
| `name` | TEXT | Friendly name (e.g. "Gujrat Robot", matching existing Keenon Cloud naming where applicable) |
| `serial_number` | TEXT, UNIQUE | `mftCode`-equivalent, `CONFIRMED` field from Keenon Cloud audit |
| `robot_id_external` | TEXT, nullable | The vendor-side identifier (Keenon's `robotId`/MAC-style value), stored for cross-reference/comparison only — not the platform's primary key |
| `activation_status` | TEXT (`registered`/`active`/`deactivated`) | |
| `lock_state` | TEXT (`locked`/`unlocked`/`unknown`) | Denormalized latest-known state for fast reads; authoritative history lives in `robot_locks` |
| `app_version` | TEXT, nullable | e.g. `v3.7.6-0-g8d58883`, `CONFIRMED` field |
| `created_at`, `updated_at` | TIMESTAMPTZ | |

**Indexes:** `organization_id`, `site_id`, unique `serial_number`. **Retention:** indefinite; deactivation is a status flag, not a delete (preserves FK integrity for historical telemetry/audit records).

## 8. `robot_credentials`
| Field | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `robot_id` | UUID, FK → `robots.id`, NOT NULL, UNIQUE | One active credential set per robot (rotate by superseding, not editing in place) |
| `credential_type` | TEXT (`api_key`/`mtls_cert`) | Per `SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md` §3.3 |
| `credential_value_encrypted` | TEXT | Encrypted at rest; never logged |
| `issued_at` | TIMESTAMPTZ | |
| `revoked_at` | TIMESTAMPTZ, nullable | |
| `created_at`, `updated_at` | TIMESTAMPTZ | |

**Explicit note:** this is entirely separate from the Peanut SDK's own `AppId`/`Secret` license (an SDK-level entitlement concept with no per-robot or per-organization identity, per the SDK study) — do not conflate the two in implementation.

## 9. `robot_status`
Denormalized "latest known state" table — one row per robot, upserted on every telemetry ingestion, for fast dashboard reads without scanning `robot_telemetry`.

| Field | Type | Notes |
|---|---|---|
| `robot_id` | UUID, PK, FK → `robots.id` | |
| `online` | BOOLEAN | Derived from heartbeat recency, per `SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md` §5.6 |
| `battery_percent` | INTEGER, nullable | |
| `charging_state` | TEXT, nullable | |
| `motor_status_code` | INTEGER, nullable | Raw SDK value (16/32/49/50/51/52/255) |
| `work_mode` | INTEGER, nullable | |
| `navigation_state` | INTEGER, nullable | |
| `emergency_enabled` | BOOLEAN, nullable | |
| `emergency_open` | BOOLEAN, nullable | |
| `last_seen_at` | TIMESTAMPTZ | Heartbeat timestamp — drives `online` |
| `updated_at` | TIMESTAMPTZ | |

**Retention:** current-state only; not itself an audit trail (see `robot_telemetry`/`robot_events` for history).

## 10. `robot_telemetry`
Append-only time-series table.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID/BIGSERIAL, PK | BIGSERIAL acceptable/preferable for a high-volume append-only table |
| `robot_id` | UUID, FK → `robots.id`, NOT NULL | |
| `recorded_at` | TIMESTAMPTZ, NOT NULL | Timestamp reported by the agent/robot, not ingestion time |
| `ingested_at` | TIMESTAMPTZ, NOT NULL DEFAULT now() | |
| `metric_type` | TEXT, NOT NULL | e.g. `battery`, `odometer`, `motor_status` — one row per metric reading, or use `payload` for a batched shape (see note) |
| `value_numeric` | DOUBLE PRECISION, nullable | |
| `value_text` | TEXT, nullable | |
| `payload` | JSONB, nullable | For structured/multi-field readings (e.g., a full `RuntimeInfo` snapshot) — recommended primary shape given the SDK's many undocumented/variable topic payloads (§ARCHITECTURE §6 extensibility rationale) |

**Indexes:** composite `(robot_id, recorded_at DESC)` for time-range queries; consider partitioning by month/robot for scale. **Retention:** hot tier (e.g., 90 days) at full granularity, then downsample/aggregate to cold storage — exact policy per `SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md` §10.

## 11. `robot_events`
Append-only — raw SDK event/topic callbacks (`onEvent`, `onHealth`, `onHeartbeat`, and the 65 named topics), not yet classified as errors/alerts.

| Field | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL, PK | |
| `robot_id` | UUID, FK → `robots.id`, NOT NULL | |
| `topic` | TEXT, nullable | e.g. `MotorStatusApi`, `RuntimeHeartbeatApi` — the raw `TopicName` string where applicable |
| `event_code` | INTEGER, nullable | For `onEvent(int, Object)`-style callbacks |
| `payload` | JSONB | Raw/undecoded content — many topic schemas are `UNKNOWN` per the SDK study; store raw, parse opportunistically |
| `recorded_at` | TIMESTAMPTZ, NOT NULL | |

**Index:** `(robot_id, recorded_at DESC)`. **Retention:** shorter hot window than `robot_telemetry` is acceptable given volume; downsample aggressively.

## 12. `robot_errors`
Append-only — classified error/fault conditions (distinct from generic events, for faster fault-focused queries).

| Field | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL, PK | |
| `robot_id` | UUID, FK → `robots.id`, NOT NULL | |
| `source` | TEXT | e.g. `motor_health`, `runtime_health`, `keenon_cloud_sync` |
| `code` | TEXT/INTEGER | Vendor error code where applicable |
| `description` | TEXT | |
| `severity` | TEXT (`warning`/`error`/`critical`) | |
| `resolved_at` | TIMESTAMPTZ, nullable | |
| `recorded_at` | TIMESTAMPTZ, NOT NULL | |

**Index:** `(robot_id, recorded_at DESC)`, `severity`.

## 13. `robot_alerts`
Alert Service's own lifecycle table (distinct from raw `robot_errors` — an alert is a product-facing, potentially-actioned object).

| Field | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `robot_id` | UUID, FK → `robots.id`, NOT NULL | |
| `alert_type` | TEXT | `error`/`warning`/`critical`/`offline`/`low_battery`/`emergency`/`task_failure` |
| `message` | TEXT | |
| `status` | TEXT (`open`/`acknowledged`/`resolved`) | |
| `acknowledged_by` | UUID, FK → `users.id`, nullable | |
| `acknowledged_at` | TIMESTAMPTZ, nullable | |
| `resolved_at` | TIMESTAMPTZ, nullable | |
| `created_at`, `updated_at` | TIMESTAMPTZ | |

**Index:** `(robot_id, status)`, `created_at`.

## 14. `robot_tasks`
| Field | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `robot_id` | UUID, FK → `robots.id`, NOT NULL | |
| `created_by` | UUID, FK → `users.id`, NOT NULL | |
| `task_type` | TEXT | `navigation`/`cleaning`/`return_to_charge`/etc. |
| `status` | TEXT (`created`/`assigned`/`running`/`paused`/`completed`/`failed`/`cancelled`) | |
| `parameters` | JSONB | Target point(s), cleaning area/strategy id, etc. |
| `started_at`, `completed_at` | TIMESTAMPTZ, nullable | |
| `created_at`, `updated_at` | TIMESTAMPTZ | |

**Index:** `(robot_id, status)`, `created_by`.

## 15. `task_events`
Append-only lifecycle trail per task.

| Field | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL, PK | |
| `task_id` | UUID, FK → `robot_tasks.id`, NOT NULL | |
| `event_type` | TEXT | `created`/`started`/`paused`/`resumed`/`stopped`/`completed`/`failed`/`cancelled` |
| `detail` | JSONB, nullable | |
| `recorded_at` | TIMESTAMPTZ, NOT NULL | |

**Index:** `(task_id, recorded_at)`.

## 16. `robot_locks`
The authoritative lock-history table underpinning the dedicated Lock Management module.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `robot_id` | UUID, FK → `robots.id`, NOT NULL | |
| `action` | TEXT (`lock`/`unlock`), NOT NULL | |
| `requested_by` | UUID, FK → `users.id`, NOT NULL | |
| `reason` | TEXT, NOT NULL | Required field per `SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md` §2.7 |
| `command_id` | UUID, FK → `robot_commands.id`, nullable | Links to the dispatched command record |
| `physically_confirmed` | BOOLEAN, NOT NULL DEFAULT false | **Must remain `false` until the Phase 0 physical test plan validates real robot behavior** — never default this to `true` |
| `requested_at` | TIMESTAMPTZ, NOT NULL | |
| `resolved_at` | TIMESTAMPTZ, nullable | When a result/ack was received |

**Index:** `(robot_id, requested_at DESC)`. This table is append-only in spirit — every lock/unlock action is a new row, never an edit to a prior one; `robots.lock_state` is the only denormalized "current state" field, refreshed from the latest row here.

## 17. `robot_commands`
General command dispatch table (lock/unlock is a specialization tracked additionally in `robot_locks`; all other commands — navigate, start, pause, stop, cleaning actions — live only here).

| Field | Type | Notes |
|---|---|---|
| `id` | UUID, PK | Doubles as the "Command ID" in `SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md` §5.4 |
| `robot_id` | UUID, FK → `robots.id`, NOT NULL | |
| `issued_by` | UUID, FK → `users.id`, NOT NULL | |
| `command_type` | TEXT, NOT NULL | e.g. `lock`, `unlock`, `navigate`, `pause`, `resume`, `stop`, `return_to_charge`, `start_cleaning` |
| `payload` | JSONB | Command-specific parameters |
| `nonce` | TEXT, UNIQUE, NOT NULL | Replay protection per `SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md` §3.4 |
| `expires_at` | TIMESTAMPTZ, NOT NULL | |
| `status` | TEXT (`sent`/`acked`/`completed`/`failed`/`timed_out`), NOT NULL DEFAULT `sent` | Never collapse these into a single boolean — matches the three-state UI requirement |
| `created_at` | TIMESTAMPTZ | |

**Index:** `(robot_id, created_at DESC)`, unique `nonce`. **Retention:** long retention recommended (command history is safety/audit-relevant), do not treat as ephemeral.

## 18. `command_results`
Append-only — one or more result/ack messages per command (an ack on receipt, then a separate completion/failure result, per `SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md` §4's ack design).

| Field | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL, PK | |
| `command_id` | UUID, FK → `robot_commands.id`, NOT NULL | |
| `result_type` | TEXT (`ack`/`success`/`failure`) | |
| `detail` | JSONB, nullable | Raw SDK callback content where available (`IDataCallback.success`/`ApiError`) |
| `recorded_at` | TIMESTAMPTZ, NOT NULL | |

**Index:** `(command_id, recorded_at)`.

## 19. `maps`
| Field | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `robot_id` | UUID, FK → `robots.id`, nullable | Nullable if a map is shared across robots at a site rather than per-robot |
| `site_id` | UUID, FK → `sites.id`, NOT NULL | |
| `scene_code` | TEXT, nullable | Keenon-side `sceneCode` for cross-reference where applicable |
| `image_data` | BYTEA / object-storage reference | The PNG floor-plan (`CONFIRMED` retrievable via Keenon Cloud today); prefer object storage + a URL/reference column over inline `BYTEA` at scale |
| `origin_metadata` | JSONB | `originPosition` (`isDynamic`, `width`, `height`, `originX`, `originY`) — fields already `CONFIRMED` present in live Keenon Cloud responses |
| `created_at`, `updated_at` | TIMESTAMPTZ | |

## 20. `map_points`
| Field | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `map_id` | UUID, FK → `maps.id`, NOT NULL | |
| `external_point_id` | TEXT, nullable | Vendor-side point/target id for cross-reference |
| `name` | TEXT | e.g. "Charging pile", "Table 1" |
| `point_type` | TEXT | `charge`/`origin`/`normal`/etc. |
| `position_x`, `position_y`, `position_z` | DOUBLE PRECISION, nullable | |
| `orientation` | JSONB, nullable | Quaternion fields where provided |
| `created_at`, `updated_at` | TIMESTAMPTZ | |

**Index:** `map_id`.

## 21. `cleaning_sessions`
Live/in-progress cleaning run tracking (distinct from historical log import — see `cleaning_history`).

| Field | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `robot_id` | UUID, FK → `robots.id`, NOT NULL | |
| `strategy_id_external` | TEXT, nullable | Vendor-side schedule/strategy id |
| `status` | TEXT (`running`/`paused`/`completed`/`failed`) | |
| `started_at`, `ended_at` | TIMESTAMPTZ, nullable | |
| `created_at`, `updated_at` | TIMESTAMPTZ | |

## 22. `cleaning_history`
Append-only, designed to absorb the already-`CONFIRMED`-available Keenon Cloud cleaning-log shape (697 records observed for the current fleet) as a baseline, extensible for SDK-sourced data later.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `robot_id` | UUID, FK → `robots.id`, NOT NULL | |
| `external_uuid` | TEXT, nullable, UNIQUE where present | Vendor-side task uuid, for idempotent import |
| `clean_area` | DOUBLE PRECISION, nullable | |
| `clean_efficiency` | DOUBLE PRECISION, nullable | |
| `duration_seconds` | DOUBLE PRECISION, nullable | |
| `started_at`, `ended_at` | TIMESTAMPTZ, nullable | |
| `fail_reason` | TEXT, nullable | |
| `snapshot_url` | TEXT, nullable | The `taskSnapshot` image URL, `CONFIRMED` present in live Keenon Cloud data |
| `source` | TEXT (`keenon_cloud`/`sdk_agent`) | Explicitly track provenance since this table may be populated from two different sources |
| `created_at` | TIMESTAMPTZ | |

**Index:** `(robot_id, started_at DESC)`, unique `external_uuid` where not null.

## 23. `charging_sessions`
| Field | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `robot_id` | UUID, FK → `robots.id`, NOT NULL | |
| `charge_type` | TEXT | `auto`/`manual`/`adapter` |
| `started_at`, `ended_at` | TIMESTAMPTZ, nullable | |
| `battery_start_percent`, `battery_end_percent` | INTEGER, nullable | |
| `created_at`, `updated_at` | TIMESTAMPTZ | |

**Index:** `(robot_id, started_at DESC)`.

## 24. `notifications`
| Field | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `user_id` | UUID, FK → `users.id`, NOT NULL | |
| `alert_id` | UUID, FK → `robot_alerts.id`, nullable | |
| `channel` | TEXT (`push`/`email`/`in_app`) | |
| `title`, `body` | TEXT | |
| `read_at` | TIMESTAMPTZ, nullable | |
| `sent_at` | TIMESTAMPTZ | |
| `created_at` | TIMESTAMPTZ | |

**Index:** `(user_id, sent_at DESC)`. **Retention:** shorter than audit logs — e.g., 90 days is a reasonable default, finalize per `SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md` §10.

## 25. `audit_logs`
Append-only, immutable. **No delete or update path anywhere in the application surface** — retention/purge, if ever required for compliance, must be an explicit, separately-authorized, itself-audited administrative process, never routine application code.

| Field | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL, PK | |
| `user_id` | UUID, FK → `users.id`, nullable | Nullable only for system-originated actions, never for user actions |
| `organization_id` | UUID, nullable | Denormalized for fast tenant-scoped queries even if the acting user is Sakar-internal |
| `robot_id` | UUID, nullable | Where the action is robot-scoped |
| `action` | TEXT, NOT NULL | e.g. `login`, `logout`, `robot_register`, `robot_lock`, `robot_unlock`, `command_navigate`, `config_change`, `permission_change`, `task_create`, `task_cancel` |
| `result` | TEXT (`success`/`failure`/`denied`) | |
| `reason` | TEXT, nullable | Free text, e.g. denial reason or user-supplied lock/unlock reason |
| `request_id` | TEXT | Correlates to the request-tracing ID propagated through the API Gateway (`SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md` §NFR observability) |
| `ip_address` | INET | |
| `device_info` | TEXT, nullable | User-agent / mobile device identifier |
| `recorded_at` | TIMESTAMPTZ, NOT NULL | |

**Index:** `(organization_id, recorded_at DESC)`, `(robot_id, recorded_at DESC)`, `(user_id, recorded_at DESC)`. **Retention:** minimum 1 year per `SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md` §10, longer if compliance requires; this table is the single most retention-sensitive table in the schema.

---

## Entity Relationship Summary (textual)

```
organizations 1──* sites 1──* robots *──1 robot_models
robots 1──1 robot_credentials
robots 1──1 robot_status
robots 1──* robot_telemetry
robots 1──* robot_events
robots 1──* robot_errors
robots 1──* robot_alerts
robots 1──* robot_tasks 1──* task_events
robots 1──* robot_locks *──1 robot_commands
robots 1──* robot_commands 1──* command_results
robots 1──* maps 1──* map_points
robots 1──* cleaning_sessions
robots 1──* cleaning_history
robots 1──* charging_sessions
users *──1 roles *──* permissions (via role_permissions)
users 1──* notifications
users, organizations, robots  →  referenced (nullable, non-owning) by audit_logs
```

## Cross-cutting notes
- Every FK from a tenant-scoped table up to `organizations` (directly or via `sites`/`robots`) must be enforced at the query layer as a mandatory filter, not merely available as a join — this is the technical backbone of `SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md` §5.5.
- High-volume append-only tables (`robot_telemetry`, `robot_events`, `command_results`, `task_events`) are strong candidates for time-based partitioning as the fleet scales; not required for MVP scale but the schema above does not preclude it later.
- `payload`/`JSONB` columns are used deliberately wherever the SDK study graded the underlying data as `UNKNOWN`/schema-unconfirmed (e.g., most of the 65 SDK topics) — this avoids a rigid schema built on assumptions that later prove wrong, at the cost of needing application-level validation rather than DB-level constraints for those fields.
