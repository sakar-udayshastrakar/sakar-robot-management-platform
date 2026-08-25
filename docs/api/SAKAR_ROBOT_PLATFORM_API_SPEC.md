# Sakar Robot Management Platform — API Specification

Companion to `SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md` and `SAKAR_ROBOT_PLATFORM_DATABASE.md`. **Specification only — nothing below is implemented.** No endpoint has been built, no route exists yet. All request/response shapes are illustrative of the required *fields*, not a final wire-format contract.

Two API surfaces are specified:
1. **§1 — Public/Client REST API** (Mobile + Web → Sakar Backend)
2. **§2 — Robot Agent API** (SakarC40Agent ↔ Sakar Backend, internal)

---

## 1. Public/Client REST API

**Conventions:** all endpoints require `Authorization: Bearer <JWT access token>` except `POST /auth/login`. All responses use a consistent envelope: `{ "data": ..., "error": null }` on success, `{ "data": null, "error": { "code": ..., "message": ... } }` on failure. All list endpoints support `page`/`page_size` query params. Every endpoint enforces the RBAC + multi-tenant scoping defined in `SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md` §5.2/§5.3 — this is not repeated per-row below but applies universally.

### 1.1 Authentication

| Endpoint | Method | Auth | Request | Response | Errors |
|---|---|---|---|---|---|
| `/auth/login` | POST | none | `{email, password}` | `{access_token, refresh_token, expires_in, mfa_required}` | `401` invalid credentials, `403` account suspended |
| `/auth/mfa/verify` | POST | partial (MFA-pending token) | `{mfa_token, otp_code}` | `{access_token, refresh_token, expires_in}` | `401` invalid OTP, `410` expired challenge |
| `/auth/refresh` | POST | refresh token | `{refresh_token}` | `{access_token, refresh_token, expires_in}` | `401` invalid/revoked refresh token |
| `/auth/logout` | POST | bearer | `{}` | `{success: true}` | `401` |
| `/auth/forgot-password` | POST | none | `{email}` | `{success: true}` (always, to avoid email enumeration) | — |
| `/auth/reset-password` | POST | reset token | `{token, new_password}` | `{success: true}` | `410` expired token |
| `/auth/devices` | POST | bearer | `{device_id, platform, push_token}` | `{registered: true}` | `401` |

### 1.2 Users / Roles

| Endpoint | Method | Permission | Notes |
|---|---|---|---|
| `/users` | GET | `USER_MANAGE` | List users in caller's scope |
| `/users` | POST | `USER_MANAGE` | Create user, assign role/org |
| `/users/{id}` | GET/PATCH | `USER_MANAGE` (or self for profile fields) | |
| `/users/{id}/role` | PUT | `USER_MANAGE` | Change role assignment |
| `/roles` | GET | `USER_MANAGE` | List roles + their permission sets |

### 1.3 Organizations / Sites

| Endpoint | Method | Permission | Notes |
|---|---|---|---|
| `/organizations` | GET/POST | `SUPER_ADMIN`/`SAKAR_ADMIN` only for POST | |
| `/organizations/{id}` | GET/PATCH | scoped | |
| `/organizations/{id}/sites` | GET/POST | scoped, `USER_MANAGE`-equivalent for POST | |
| `/sites/{id}` | GET/PATCH | scoped | |

### 1.4 Robots

| Endpoint | Method | Permission | Request | Response |
|---|---|---|---|---|
| `GET /robots` | GET | `ROBOT_VIEW` | query: `site_id`, `status` filters | List of robots (name, model, serial, online, battery, lock_state, current_task) |
| `GET /robots/{id}` | GET | `ROBOT_VIEW` | — | Full robot detail (§REQUIREMENTS §2.5 field set) |
| `POST /robots` | POST | `ROBOT_CONFIG` | `{site_id, robot_model_id, name, serial_number}` | Created robot record, `activation_status: registered` |
| `PATCH /robots/{id}` | PATCH | `ROBOT_CONFIG` | Partial fields | Updated robot |
| `POST /robots/{id}/activate` | POST | `ROBOT_CONFIG` | — | `{activation_status: active}` |
| `POST /robots/{id}/deactivate` | POST | `ROBOT_CONFIG` | — | `{activation_status: deactivated}` |
| `DELETE /robots/{id}` | DELETE | `ROBOT_DELETE` | — | Soft-delete only, per `SAKAR_ROBOT_PLATFORM_DATABASE.md` §7 retention note |
| `GET /robots/{id}/status` | GET | `ROBOT_VIEW` | — | Current `robot_status` row |
| `GET /robots/{id}/telemetry` | GET | `ROBOT_VIEW` | query: `metric_type`, `from`, `to` | Paginated `robot_telemetry` rows |
| `GET /robots/{id}/events` | GET | `ROBOT_VIEW` | query: `topic`, `from`, `to` | Paginated `robot_events` rows |
| `GET /robots/{id}/errors` | GET | `ROBOT_VIEW` | query: `severity`, `from`, `to` | Paginated `robot_errors` rows |
| `GET /robots/{id}/history` | GET | `ROBOT_VIEW` | query: `from`, `to` | Combined timeline (commands + tasks + lock changes + errors), for the "robot logs" UI requirement |

### 1.5 Robot Commands

| Endpoint | Method | Permission | Request | Response | Notes |
|---|---|---|---|---|---|
| `POST /robots/{id}/commands` | POST | `ROBOT_CONTROL` | `{command_type, payload}` | `{command_id, status: "sent"}` | Generic dispatch for navigate/pause/resume/stop/return_to_charge/etc.; backend generates `nonce`/`expires_at` per `SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md` §3.4 |
| `GET /robots/{id}/commands/{command_id}` | GET | `ROBOT_CONTROL` | — | `{status: sent\|acked\|completed\|failed\|timed_out, result_detail}` — poll or receive via realtime channel |
| `POST /robots/{id}/lock` | POST | `ROBOT_LOCK` | `{reason}` (required) | `{command_id, status: "sent", physically_confirmed: false}` | See §1.5.1 below — response must never claim physical confirmation |
| `POST /robots/{id}/unlock` | POST | `ROBOT_UNLOCK` | `{reason}` (required) | `{command_id, status: "sent", physically_confirmed: false}` | Stricter permission than lock, per Requirements §5.1/§5.2 |
| `GET /robots/{id}/lock-history` | GET | `ROBOT_VIEW` | query: `from`, `to` | Paginated `robot_locks` rows (action, requested_by, reason, timestamps, physically_confirmed) |

**1.5.1 — Mandatory response contract for `/lock` and `/unlock`:** the response body **must** include an explicit `physically_confirmed: false` field (or equivalent) for every response, until such time as the Phase 0 physical test plan (`PEANUT_SDK_C40_TECHNICAL_STUDY.md` §11) has validated real hardware behavior for that robot model. This is a deliberate API-contract-level safeguard against a client (mobile/web) ever rendering an unverified physical guarantee as fact — it must not be left to each client's UI logic to remember this caveat independently.

### 1.6 Tasks

| Endpoint | Method | Permission | Notes |
|---|---|---|---|
| `GET /robots/{id}/tasks` | GET | `ROBOT_VIEW` | Task history/status |
| `POST /robots/{id}/tasks` | POST | `TASK_CREATE` | `{task_type, parameters}` → creates `robot_tasks` row, does not itself dispatch — dispatch happens via `/robots/{id}/commands` referencing the task, or an internal orchestration step (implementation detail, not fixed here) |
| `POST /tasks/{id}/start` \| `/pause` \| `/resume` \| `/stop` \| `/cancel` | POST | `TASK_CONTROL` | Lifecycle transitions; each recorded in `task_events` |
| `GET /tasks/{id}` | GET | `ROBOT_VIEW` | Full task detail + event trail |

### 1.7 Maps

| Endpoint | Method | Permission | Notes |
|---|---|---|---|
| `GET /robots/{id}/maps` | GET | `ROBOT_VIEW` | List maps for the robot/site |
| `GET /maps/{id}` | GET | `ROBOT_VIEW` | Map metadata + image reference |
| `GET /maps/{id}/points` | GET | `ROBOT_VIEW` | Named points with coordinates |
| `POST /maps` \| `PATCH /maps/{id}` | POST/PATCH | `MAP_MANAGE` | Map management — only for capabilities `CONFIRMED` available (§REQUIREMENTS §3.2 Map section) |

### 1.8 Cleaning

| Endpoint | Method | Permission | Notes |
|---|---|---|---|
| `GET /robots/{id}/cleaning/history` | GET | `ROBOT_VIEW` | Backed by `cleaning_history`, includes `snapshot_url` |
| `GET /robots/{id}/cleaning/schedules` | GET | `ROBOT_VIEW` | Backed by live sync from Keenon Cloud `clean/strategy/list` and/or SDK equivalent |
| `POST /robots/{id}/cleaning/schedules` | POST | `TASK_CREATE` | Create schedule — `CONFIRMED` API exists server-side (Keenon Cloud), classified `NON_PHYSICAL_WRITE` |
| `POST /robots/{id}/cleaning/tasks` | POST | `TASK_CREATE` + `ROBOT_CONTROL` | Trigger immediate/temporary cleaning task — classified `PHYSICAL_ROBOT_CONTROL`, gate behind the same physical-validation posture as §1.5.1 |

### 1.9 Alerts / Notifications

| Endpoint | Method | Permission | Notes |
|---|---|---|---|
| `GET /alerts` | GET | `ROBOT_VIEW` | query: `robot_id`, `status`, `severity` |
| `POST /alerts/{id}/acknowledge` | POST | `ROBOT_CONTROL` | |
| `POST /alerts/{id}/resolve` | POST | `ROBOT_CONTROL` | |
| `GET /notifications` | GET | self | Current user's notification inbox |
| `POST /notifications/{id}/read` | POST | self | |

### 1.10 Analytics

| Endpoint | Method | Permission | Notes |
|---|---|---|---|
| `GET /analytics/utilization` | GET | `ROBOT_VIEW` | query: `robot_id`/`site_id`, `from`, `to` |
| `GET /analytics/errors` | GET | `ROBOT_VIEW` | Error-frequency aggregation |
| `GET /analytics/downtime` | GET | `ROBOT_VIEW` | Online/offline history aggregation |

### 1.11 Audit Logs

| Endpoint | Method | Permission | Request | Response |
|---|---|---|---|---|
| `GET /audit-logs` | GET | `AUDIT_VIEW` | query: `user_id`, `robot_id`, `action`, `from`, `to` | Paginated, immutable audit records (§DATABASE §25 fields) |

### 1.12 Standard Error Handling
| HTTP status | Meaning |
|---|---|
| `400` | Malformed request body/params |
| `401` | Missing/invalid/expired auth token |
| `403` | Authenticated but not authorized (RBAC or tenant-scope failure) |
| `404` | Resource not found (or not visible to caller's scope — do not distinguish from a true 404, to avoid leaking cross-tenant existence) |
| `409` | Conflict (e.g., duplicate registration, command already in a terminal state) |
| `422` | Semantically invalid (e.g., unlock without a reason) |
| `429` | Rate limited |
| `500`/`503` | Backend/dependency failure — per `SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md` §5.6, must not be silently swallowed by clients |

---

## 2. Robot Agent API (SakarC40Agent ↔ Sakar Backend)

This is the internal protocol the existing `PeanutSdkBridge`/`C40RobotController` architecture would need to speak to the Sakar Backend. Transport per `SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md` §4 (hybrid HTTPS + MQTT recommended); message *shapes* below apply regardless of transport choice.

### 2.1 Register
**Agent → Backend, once per agent install/provisioning.**
```
Request:  { robot_serial_number, robot_model, agent_version, sdk_version, provisioning_token }
Response: { robot_id, robot_credentials: { credential_type, credential_value }, backend_endpoints: {...} }
```
Establishes the `robots`/`robot_credentials` rows (§DATABASE §7/§8). `provisioning_token` is a one-time, separately-distributed value — not the same as the ongoing `robot_credentials` value it results in.

### 2.2 Heartbeat
**Agent → Backend, periodic.**
```
Request:  { robot_id, timestamp, agent_version, sdk_connection_state, sdk_init_status }
Response: { ack: true, server_time }
```
Drives `robot_status.online`/`last_seen_at` (§DATABASE §9). Frequency: configurable, default `UNKNOWN` pending Phase 0 real-link-latency measurement (§REQUIREMENTS §10).

### 2.3 Telemetry
**Agent → Backend, periodic or on-change.**
```
Request:  { robot_id, recorded_at, readings: [ { metric_type, value_numeric?, value_text?, payload? }, ... ] }
Response: { ack: true, accepted_count }
```
Batched shape supports the "buffer while offline, replay on reconnect" requirement (§REQUIREMENTS §4.7/§5.6) — a single request can carry many buffered readings.

### 2.4 Events
**Agent → Backend, on SDK callback (`onEvent`/`onHealth`/topic push).**
```
Request:  { robot_id, recorded_at, topic?, event_code?, payload }
Response: { ack: true }
```
Maps to `robot_events` (§DATABASE §11) — stored raw given the many `UNKNOWN`-schema topics; classification into `robot_errors`/`robot_alerts` happens backend-side, not agent-side.

### 2.5 Command (Backend → Agent)
**Backend → Agent, pushed over the realtime channel.**
```
Payload:  { command_id, robot_id, command_type, params, issued_at, expires_at, nonce, auth_scope, signature }
```
Agent validates per `SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md` §3.4 (expiry, nonce dedup, scope, signature) before invoking any `PeanutSdkBridge`/`C40RobotController` method, and only for command types the current `OperatingMode`/authorization state permits.

### 2.6 Command Acknowledgement (Agent → Backend)
**Immediately on receipt, before execution.**
```
Request:  { command_id, robot_id, received_at }
Response: { ack: true }
```

### 2.7 Command Result (Agent → Backend)
**On completion or failure.**
```
Request:  { command_id, robot_id, result: "success"|"failure", detail?, completed_at }
Response: { ack: true }
```
For `lock`/`unlock` specifically: `detail` should include the SDK's own `getStatus()` follow-up read (§REQUIREMENTS §5.1) so the backend records what the SDK *reported*, distinct from — and never substituted for — physical confirmation.

### 2.8 Lock / Unlock (specialization of §2.5-2.7)
Same message shapes as Command/Ack/Result, with `command_type: "lock"|"unlock"`. Additionally, the agent should perform a `getStatus()` read immediately after invoking `enable()` and include the resulting motor-status code in the result `detail` — this is the one piece of real signal available today (SDK-reported state), even though it is explicitly not the same as physically-verified robot behavior (§REQUIREMENTS §5.1).

### 2.9 Configuration (Backend → Agent)
```
Payload:  { robot_id, config_version, settings: { heartbeat_interval_s, telemetry_batch_interval_s, enabled_topics: [...], operating_mode } }
```
Allows remote tuning of telemetry frequency and which of the 65 SDK topics are actively subscribed, without an app redeploy. `operating_mode` changes (e.g., permitting `HARDWARE_TEST`) must themselves be authorized commands, audited identically to a lock/unlock action — this is a safety-relevant configuration change, not a cosmetic setting.

### 2.10 Health / Version (Agent → Backend, on heartbeat or on-demand)
```
Request:  { robot_id, agent_version, sdk_version_reported, native_lib_abi, last_error? }
Response: { ack: true, update_available: boolean }
```
`native_lib_abi` surfaces the 32-bit-only (`armeabi`/`armeabi-v7a`) native library constraint identified in the SDK study, so fleet-wide compatibility can be monitored centrally rather than discovered per-device.

### 2.11 Message security
All Agent↔Backend messages travel over TLS. Command messages (§2.5) additionally carry a signature validated against the robot's `robot_credentials` entry (§ARCHITECTURE §3.3/§3.4) — this is in addition to transport-level TLS, not a replacement for it, since defense-in-depth against a compromised intermediate hop is the explicit goal of per-command signing.

---

## 3. What This Specification Deliberately Does Not Do
- It does not fix a final wire format (JSON vs. protobuf vs. MQTT-specific encoding) — that is an implementation decision within the constraints above.
- It does not implement authentication, authorization, or any endpoint — this document is the contract to build against, not the build.
- It does not expose any SDK capability graded `UNKNOWN` in the source studies as a first-class typed field — those remain in `payload`/JSONB shapes until confirmed, per `SAKAR_ROBOT_PLATFORM_DATABASE.md`'s cross-cutting notes.
- It does not claim `/robots/{id}/lock` or `/unlock` are safety-certified — see §1.5.1's mandatory response contract.
