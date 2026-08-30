# Sakar Platform — Keenon-Parity Product Expansion Requirements

**Status: PROPOSED. NOT APPROVED FOR IMPLEMENTATION. NOT IMPLEMENTED.**

This document exists solely to scope three product modules observed in the Keenon Cloud dashboard (Open Platform, OTA/Deployment, IoT/Elevator Integration) so they can be reviewed and approved or rejected as deliberate product decisions — **not** absorbed into the codebase by inference from screenshots. Per the governing instruction that produced this document: *"Open Platform / OTA / Deployment / IoT / Elevator must NOT be silently implemented from screenshots alone. First create a formal REQUIREMENTS / PRODUCT SCOPE proposal for these modules. Do NOT implement their backend functionality yet."*

No code in this repository implements any part of this document. `SAKAR_ROBOT_PLATFORM_ROADMAP.md`'s Phases 11-13 (added alongside this document) mark these as **proposed, unapproved** phases, clearly separated from the approved Phase 0-10 roadmap.

---

## 1. Purpose

Keenon Cloud (the vendor platform Sakar currently integrates against for the C40/C40 S, `cloud.robotkeenon.com`) exposes three product surfaces that Sakar's platform does not currently have an equivalent of: a developer-facing **Open Platform** (API client/key management for third-party integrators), an **OTA/Deployment** module (firmware/app version tracking and rollout), and an **IoT/Elevator Integration** module (registering non-robot devices — elevator controllers — and letting a robot call an elevator). This document scopes what a Sakar-owned equivalent of each would require, so that a deliberate, reviewed decision — build, defer, or reject — can be made per module, instead of these appearing in the product by inference from reference screenshots.

This is a requirements/scope document, not a design document. Where a decision requires information Sakar does not yet have (an elevator vendor, a firmware update mechanism, a rate-limit policy), the open question is recorded rather than resolved by assumption.

## 2. Scope

**In scope for this document:** functional and non-functional requirements, data model shape, API surface shape, security/audit requirements, UI requirements, and acceptance criteria for three proposed modules, at a level sufficient for a product/engineering review to approve, defer, or reject each independently.

**Out of scope for this document:** any implementation, migration, entity, controller, or service. Out of scope for the modules themselves (regardless of future approval): anything requiring Keenon to expose an API it does not currently expose (see §9 "Integration Requirements" and Module B for the specific capability gaps this repository's own Keenon API evidence has already identified).

## 3. Product Rationale

Sakar's stated goal (Master Requirements Part 10/40) is for Sakar — not the robot vendor — to be the system of record for robot data, commands, and fleet operations. Today, three legitimate product capabilities exist only inside Keenon's own platform and are invisible to Sakar's customers unless they log into Keenon Cloud directly:

- Third-party integrators who want to build on top of "the platform running Sakar's robots" have no Sakar-branded way to do so (Keenon's Open Platform serves this today, under Keenon's brand, not Sakar's).
- Firmware/app version visibility and rollout tracking exists only in Keenon Cloud; Sakar has no visibility into what firmware a given robot is running, nor any rollout mechanism of its own.
- Elevator-integrated multi-floor cleaning (a real Keenon Cloud capability, seen in the reference screenshots) has no Sakar equivalent, which matters for any customer site with multiple floors.

Each is a legitimate expansion of "Sakar as system of record," not feature-parity for its own sake — hence three separate module proposals rather than one undifferentiated "copy Keenon" effort.

## 4. Relationship to the Existing Approved Platform

These modules are additive to, and depend on, the already-approved organization/site/robot/user/role model (`org`, `iam`, `robot.registry` packages) and the existing `TenantAccessGuard`/`AuditService`/`SakarErrorCode` chokepoints — **not** a parallel authorization or audit system. If approved, each module's implementation phase must reuse those chokepoints per the same rule that governed this session's approved-scope work ("inspect existing code... reuse existing authorization... do not create duplicate entities/controllers/services").

None of the three modules replace or modify Path 1 (`Sakar Backend → Keenon Robot Adapter → Keenon Cloud → Robot`) or the target Path 2 (`Web/Mobile → Sakar Cloud → MQTT → SakarC40Agent → Peanut SDK → C40`) described in the architecture and master requirements documents. See §13 for how each module relates to both paths.

## 5. Functional Requirements Overview

| Module | One-line function | Status label |
|---|---|---|
| A — Open Platform | Sakar-branded API client/key management so third parties can integrate against Sakar's own API, scoped to specific robots/sites/orgs they're granted access to | `REQUIREMENT` (proposed) |
| B — OTA / Deployment | Track firmware/app versions per robot and record deployment attempts/history; rollout mechanism itself is vendor-dependent (see Module B) | `REQUIREMENT` (proposed), partially `REQUIRES VENDOR SUPPORT` |
| C — IoT / Elevator Integration | Register non-robot devices (starting with elevator controllers), associate them with a site, and let an authorized robot task call one | `REQUIREMENT` (proposed), protocol `OPEN QUESTION` |

Detailed requirements for each module are in §§ Module A, Module B, Module C below.

## 6. User Roles and Permissions

All three modules are additive to the existing `RoleName`/`PermissionCode` enums (`iam/RoleName.java`, `iam/PermissionCode.java`) — no new role hierarchy is proposed. New permission codes this expansion would require, if approved (naming follows the existing `<DOMAIN>_<ACTION>` convention):

| Proposed permission | Module | Rationale |
|---|---|---|
| `OPEN_PLATFORM_MANAGE` | A | Create/rotate/revoke API clients and keys — an administrative action, not a general operator one |
| `OPEN_PLATFORM_VIEW` | A | View API clients, usage stats, request/webhook logs without managing them |
| `OTA_MANAGE` | B | Create deployment groups, initiate a rollout, view/approve rollback |
| `OTA_VIEW` | B | View version/deployment status and history, read-only |
| `IOT_DEVICE_MANAGE` | C | Register/configure IoT devices (elevators), provision credentials |
| `IOT_DEVICE_VIEW` | C | View device status/health/event history |

Which existing roles (`ORG_ADMIN`, `SITE_ADMIN`, etc.) would be granted which of these is an **open question** (§16) — a decision for whoever approves this document, following the same rationale documented in `V9__seed_rbac.sql` (e.g., "unlocking must be deliberately harder to obtain than locking").

## 7. Organization/Tenant Isolation Requirements

Every resource introduced by any of the three modules — API clients, OTA deployment groups, IoT devices — **must** carry an `organization_id` and be resolved through `TenantAccessGuard` exactly like every existing resource (`Robot`, `RobotTask`, `RobotAlert`, etc.), using the same not-found-not-forbidden pattern (`getAccessibleOrThrow`) documented throughout this codebase. No module in this document introduces, or should introduce, a second authorization mechanism.

Module A additionally requires a **per-client access grant** model narrower than "everything in my organization" — see Module A §"Access Grants" for the explicit example (`Client A → Robot X, Robot Y but NOT Robot Z`) that motivates a grant table distinct from organization-level scoping.

## 8. Data Model Requirements (shape only — no migrations in this document)

High-level entities each module would introduce, at the same level of detail as an early-roadmap-phase deliverables list (full column-level schema is implementation-phase work, not this document's):

**Module A:** `api_clients`, `api_keys`, `api_client_access_grants` (client ↔ organization/site/robot, plus which capabilities), `api_request_logs` (append-only, high-volume — same `AppendOnlyEntity` pattern as `robot_telemetry`/`audit_logs`), `webhook_configs`, `webhook_delivery_attempts` (append-only).

**Module B:** `firmware_versions` (or `software_versions`, name TBD — open question, since "firmware" vs. Sakar-agent "app version" are different things, see Module B), `deployment_groups`, `deployment_group_members`, `deployment_runs`, `deployment_run_events` (append-only history — mirrors the existing `TaskEvent` append-only-history pattern), `compatibility_rules` (which version is valid for which robot model).

**Module C:** `iot_devices` (generic device row: id, org, site, device_type, external_device_id, status), `iot_device_credentials` (mirrors `RobotCredentialService`'s existing pattern for robot MQTT credentials — reuse that service's design, do not invent a second credential-provisioning mechanism), `iot_device_events` (append-only), `elevator_call_authorizations` (which robot/task may invoke which elevator, and the resulting audit trail).

## 9. API Requirements (shape only)

Path shape should follow the existing `SAKAR_ROBOT_PLATFORM_API_SPEC.md` convention (`/api/v1/<resource>`, nested under `/api/v1/organizations/{id}/...` where organization-scoped). Illustrative only, not a committed contract:

- Module A: `POST/GET /api/v1/open-platform/clients`, `POST /api/v1/open-platform/clients/{id}/keys`, `DELETE /api/v1/open-platform/keys/{id}` (revoke), `POST/GET /api/v1/open-platform/clients/{id}/access-grants`, `GET /api/v1/open-platform/clients/{id}/usage`, `GET /api/v1/open-platform/clients/{id}/request-logs`, `POST/GET /api/v1/open-platform/clients/{id}/webhooks`, `GET /api/v1/open-platform/webhooks/{id}/deliveries`.
- Module B: `GET /api/v1/robots/{id}/version` (read current reported version — `REQUIRES VENDOR SUPPORT` for Keenon-integrated robots, see Module B), `POST/GET /api/v1/deployment-groups`, `POST /api/v1/deployment-groups/{id}/rollout` (`DESIGN` only — see Module B's explicit prohibition on inventing a firmware-push mechanism), `GET /api/v1/deployment-groups/{id}/history`.
- Module C: `POST/GET /api/v1/iot-devices`, `GET /api/v1/iot-devices/{id}`, `POST /api/v1/iot-devices/{id}/credentials`, `GET /api/v1/iot-devices/{id}/events`, `POST /api/v1/robots/{robotId}/elevator-calls` (`DESIGN` only, protocol an open question).

Every endpoint must return `SakarErrorCode`-shaped errors (never a raw Keenon code or vendor HTTP status), per the platform-wide rule already enforced everywhere else in this codebase.

## 10. Security Requirements

- API keys (Module A) must be hashed at rest exactly like `User.passwordHash` — never stored or logged in plaintext, never returned again after initial issuance (standard "show once" pattern).
- Key rotation and revocation (Module A) must be immediate and audited — same bar as `RobotCredentialService`'s existing revoke path (Phase 3 Security Hardening).
- IoT device credentials (Module C) must reuse `RobotCredentialService`'s existing design rather than invent a second credential-issuance/rotation/revocation mechanism.
- Rate limiting on Open Platform client requests (Module A) is a `REQUIREMENT` before any external client is granted a key — the existing `LoginRateLimiterService`/MQTT rate-limiter pattern (Redis-backed, per-identity) is the model to reuse, not a new mechanism.
- No module in this document may weaken, bypass, or duplicate `TenantAccessGuard` — see §7.
- Elevator call authorization (Module C) is a `SYSTEM_ADMIN`/site-admin-level decision per authorization request, not a blanket robot capability — mirrors the existing `ROBOT_UNLOCK` "deliberately harder to obtain" precedent from `V9__seed_rbac.sql`.

## 11. Audit Requirements

Every state-changing action in all three modules — key issuance/rotation/revocation, access-grant changes, deployment-group creation, rollout initiation, IoT device registration/credential issuance, elevator-call authorization — is a `AuditService.record(...)` call, exactly like every existing sensitive action in this codebase (`COMMAND_ISSUED`, `TASK_CREATED`, `USER_ROLE_CHANGED`, etc. from the approved Decision 1 work). No module introduces a second audit mechanism. Master Requirements Part 12.E's blanket sensitive-action-logging rule applies without modification.

## 12. UI Requirements

If approved, each module's UI must follow the existing Sakar-branded design system (light theme, Sakar orange accent, the exact header/sidebar structure already migrated to match the real Support Portal — see `docs/architecture/SAKAR_WEB_UI_DESIGN_SYSTEM.md`) — **never** Keenon's blue/dark visual identity, and never any Keenon logo, wordmark, or branding element. The Keenon Cloud screenshots referenced when scoping this document are a **navigation/layout/information-architecture reference only** — e.g., "an Open Platform module plausibly needs a client list, a key list per client, and a usage/logs view" — never a visual or brand template, and never evidence that Sakar operates or is affiliated with Keenon's platform.

Illustrative sidebar placement (not a commitment — final navigation is a design-phase decision): a new top-level "Open Platform" section (Module A); an "OTA / Deployment" tab under the existing Robot Management area (Module B); an "IoT Devices" section, possibly under Site Management, given elevators are site-scoped (Module C).

## 13. Integration Requirements

- **Module A** is Sakar-facing only — it does not call Keenon or any other vendor. It exposes *Sakar's own* API to third parties; it is orthogonal to both Path 1 and Path 2.
- **Module B** is real for Sakar's own agent-based path (Path 2: `SakarC40Agent` has an app/APK version Sakar can track and could, in principle, roll out via a Sakar-controlled update channel) but is **`REQUIRES VENDOR SUPPORT`** for the C40/C40 S's own onboard firmware, which is Keenon/vendor-controlled — see Module B §"Explicit Non-Assumptions" for what this document does and does not assume Sakar can do today.
- **Module C** would integrate with Path 2 (a robot-side elevator call would need to originate from `SakarC40Agent`/`PeanutSdkBridge`, which has no such capability today — confirmed absent from the Peanut SDK study) or, alternatively, in the interim, could theoretically route through Keenon Cloud's own elevator integration if Sakar chose to depend on it (**not recommended** — reintroduces the Keenon-Cloud-dependency this platform is trying to reduce, per Master Requirements Part 10/40). This tradeoff is recorded as an open question (§16), not decided here.

## 14. Acceptance Criteria (per module, illustrative — to be finalized at approval)

**Module A:** *Given* an approved Open Platform client with an access grant limited to Robot X and Robot Y, *when* that client calls the Sakar API for Robot Z, *then* the request is rejected with a `SakarErrorCode`-shaped not-found/forbidden response, identically to how `TenantAccessGuard` already rejects cross-tenant access for internal users.

**Module B:** *Given* a deployment group and a rollout initiated against it, *when* the rollout is queried, *then* its status is one of a defined, honest set of states (e.g., `INITIATED`, `IN_PROGRESS`, `COMPLETED`, `FAILED`, `NOT_SUPPORTED_BY_VENDOR`) — never a state implying the firmware was actually pushed to a Keenon-controlled robot unless Keenon has confirmed that capability exists (see Module B).

**Module C:** *Given* an IoT device registered and associated with a site, *when* an authorized robot task requests an elevator call, *then* the call is logged as an authorization event whether or not the underlying device integration has been physically validated — mirroring this codebase's existing "API accepted ≠ robot executed ≠ verified" distinction (Master Requirements Part 40) applied to a new device type.

## 15. Risks

| Risk | Module | Notes |
|---|---|---|
| Keenon has no public firmware-push API | B | Confirmed absent from source evidence gathered this session — see Module B. Rollout for Keenon-integrated robots may be permanently `REQUIRES VENDOR SUPPORT`, not a temporary gap |
| No confirmed elevator protocol | C | Recorded as an open question, not assumed — building against a guessed protocol risks a rebuild once a real integration is scoped |
| Rate-limit/abuse surface from third-party API clients | A | A public-facing API is a materially larger attack surface than the current internal-only API; requires its own security review before any external client is onboarded |
| Scope creep back into "be Keenon" | A, B, C | The explicit purpose of this document (§1) is to prevent silent scope creep from screenshots; re-review against source Keenon evidence (not assumption) before every future increment |
| Elevator integration liability | C | A robot mis-calling or blocking an elevator has real-world physical/safety implications beyond typical software risk — treat as a safety-adjacent feature, same rigor tier as lock/unlock (Master Requirements Part 11/38), not an ordinary CRUD feature |

## 16. Open Questions

1. Which roles (beyond `SUPER_ADMIN`) should hold the six new permission codes in §6? No recommendation is made here.
2. Elevator communication protocol: not assumed. Candidates would need vendor research (a specific elevator controller manufacturer/protocol was never confirmed in any source evidence reviewed this session).
3. Should Module B track only Sakar-agent (APK) versions initially, deferring Keenon-firmware tracking entirely until Keenon exposes a version-read API? (No such API was confirmed to exist in this session's Keenon API evidence — see Module B.)
4. Should Module C's elevator-call path go through Keenon Cloud in the interim (faster, but re-couples to Keenon) or wait for a native Path 2 capability (slower, but architecturally correct per Part 10/40)? See §13.
5. Should Open Platform clients be organization-scoped only, or could a client legitimately need cross-organization access (e.g., a distributor integrating across multiple client orgs it manages)? The existing organization hierarchy (`org/`) may already model this — needs explicit design review, not assumed here.
6. Rate limit defaults for Module A (requests/minute per client) — no number is proposed here; should follow a security review, not an arbitrary default.

## 17. Out-of-Scope Items (explicit, for this document and for any future approved implementation)

- Copying Keenon's UI, branding, logo, or visual identity in any form (§12).
- Implementing a firmware-push mechanism Keenon has not confirmed exists (§13, Module B).
- Assuming any specific elevator protocol without vendor confirmation (§13, Module C).
- Storing or exposing raw Keenon credentials, tokens, vendor URLs, or vendor internal IDs to Open Platform third-party clients (Module A) — Sakar remains the system of record; vendor internals are never leaked through Sakar's own API surface, per Master Requirements Part 10/40's data-ownership rule.
- Any change to Path 1 or Path 2's existing implemented behavior — this document proposes additive modules only.
- Any backend implementation at all — see this document's status banner.

## 18. Phase Recommendation

If approved, sequence these as **new roadmap phases after the existing Phase 0-10**, not interleaved with them, since none of the three modules are dependencies for anything in the approved roadmap:

- **Phase 11 — Open Platform (Module A):** lowest physical/safety risk of the three (pure software, no robot-motion implication); reasonable first candidate if this expansion is approved at all.
- **Phase 12 — OTA / Deployment (Module B):** should start with the Sakar-agent-version tracking sub-scope only (§16 Q3) if approved, since the Keenon-firmware half is presently `REQUIRES VENDOR SUPPORT` and may never be fully buildable.
- **Phase 13 — IoT / Elevator Integration (Module C):** highest physical/safety-adjacent risk (§15) and the most open protocol questions (§16 Q2) — recommend this be the last of the three approved, and only after a real elevator vendor/protocol is identified, not before.

See `SAKAR_ROBOT_PLATFORM_ROADMAP.md`'s "Proposed Product-Expansion Phases" section for how these are recorded there (clearly marked unapproved, distinct from the approved Phase 0-10 sequence).

---

## Module A — Open Platform (API Client / Key Management)

**Purpose:** let a third party integrate against *Sakar's own* API (never Keenon's), scoped to exactly the robots/sites/organizations Sakar grants them.

**Functional requirements:**
- Register an API client (name, owning organization, contact metadata). `REQUIREMENT`.
- Issue one or more API keys per client; each key hashed at rest, shown once at issuance, individually revocable. `REQUIREMENT`.
- **Access grants**, modeled as an explicit, auditable list of (client, resource-type, resource-id, capability) tuples — e.g. *Client A → Robot X, Robot Y but NOT Robot Z* — not a blanket "client belongs to org, sees everything in org" model, since a third-party integrator plausibly needs narrower access than an internal org user. `REQUIREMENT`.
- Model/capability access: a grant may be scoped further to specific capabilities (e.g., read-only telemetry vs. task creation) using the existing `RobotCapabilityType`/`PermissionCode` vocabulary, not a new one. `REQUIREMENT`.
- Rate limits per client (not just per-IP), independent of the general API rate limit — mirrors Master Requirements' existing login-endpoint rate-limit-independence requirement. `REQUIREMENT`.
- Request logs: append-only record of every call an API client makes (endpoint, timestamp, result, not full payload unless a specific compliance need is identified — avoid over-logging sensitive payloads by default). `REQUIREMENT`.
- Webhook configuration (client-registered callback URL + event-type subscription) and webhook delivery history (append-only, including delivery success/failure and retry count). `REQUIREMENT`.
- API documentation: generated from the same OpenAPI/Swagger foundation already in place (`config/OpenApiConfig.java`), scoped to only the endpoints a given client's grants expose — not a new documentation system. `REQUIREMENT`.
- Key rotation and revocation, both immediate and audited. `REQUIREMENT`.
- Usage statistics (calls per period, per endpoint) surfaced to the client's own organization admin. `REQUIREMENT`.

**Explicit non-assumptions:** no assumption is made about which specific external integrators exist or want this today — this module is scoped generically, not against a named customer requirement.

---

## Module B — OTA / Deployment

**Purpose:** give Sakar visibility into, and eventually control over, what software/firmware version is running on each robot, and a record of deployment attempts.

**Functional requirements:**
- Version tracking: record the currently-known software/firmware version per robot. `REQUIREMENT` for the record-keeping; **`REQUIRES VENDOR SUPPORT`** for the C40/C40 S's onboard firmware version specifically — no Keenon API to read a robot's current firmware version was confirmed in this session's Keenon API evidence gathering. `AVAILABLE THROUGH VENDOR` is not confirmed either; this is presently an **unknown**, not a negative — it simply has not been found, and must not be assumed available.
- Sakar-agent (`SakarC40Agent` APK) version tracking: `REQUIREMENT`, and unlike the robot firmware, this is fully within Sakar's control (Sakar built and vendors the agent) — the more tractable half of this module if approved (see §16 Q3).
- Releases: a named, versioned release record (what changed, target robot model(s), minimum compatible prior version). `REQUIREMENT`.
- Deployment groups: named sets of robots/sites a release is targeted at, distinct from ad-hoc "roll out to everything." `REQUIREMENT`.
- Deployment status per robot within a group: a defined, honest state machine (e.g., `PENDING`, `IN_PROGRESS`, `SUCCEEDED`, `FAILED`, `NOT_APPLICABLE`) — mirrors the existing `CommandStatus`/`TaskLifecycleStatus` philosophy of never collapsing "attempted" into "succeeded." `REQUIREMENT`.
- Deployment history: append-only, per-robot, per-attempt. `REQUIREMENT`.
- Rollback concept: recorded as a requirement to be able to *identify* the previous known-good version per robot and *record* a rollback attempt — the actual push mechanism inherits the same vendor-dependency caveat as forward deployment. `REQUIREMENT` (record-keeping) / `REQUIRES VENDOR SUPPORT` (mechanism, for Keenon-integrated robots).
- Compatibility validation: before targeting a deployment group, validate that the release is declared compatible with the target robot model(s) (reuses the existing `RobotCapabilityType`-style model-scoped validation pattern, e.g. `RobotCapabilityService.assertSupported`). `REQUIREMENT`.
- Maintenance windows: a deployment run may be constrained to a configured time window per site, to avoid interrupting active cleaning operations. `REQUIREMENT`.

**Explicit non-assumptions (carried over verbatim from the governing instruction, because this is the module most at risk of over-claiming):**
- **Do not assume Sakar can currently update Keenon firmware.** No such capability has been confirmed to exist in any Keenon API evidence gathered in this session.
- **Do not invent a firmware-update mechanism.** Where a mechanism does not exist, the correct label is `NOT YET AVAILABLE` or `REQUIRES VENDOR SUPPORT`, never a placeholder implementation that appears functional.
- The Sakar-agent-version half of this module (APK updates) is the one part of "OTA" Sakar can actually build without vendor cooperation, since Sakar controls that binary's distribution.

---

## Module C — IoT / Elevator Integration

**Purpose:** register and manage non-robot devices at a site — starting with elevator controllers — so a robot can be authorized to request an elevator call as part of a multi-floor task.

**Functional requirements:**
- Device registration: register a device with a type (starting with `ELEVATOR_CONTROLLER`, modeled extensibly for future device types), an owning site, and an external device identifier. `REQUIREMENT`.
- Device status/health: online/offline, last-seen, and a health indicator — mirrors the existing `robot_status`/`RobotStatusService` pattern (do not invent a second status model). `REQUIREMENT`.
- Site association: every device belongs to exactly one site, resolved through the same organization/site hierarchy as robots. `REQUIREMENT`.
- Elevator integration specifically: a robot task may request an elevator call, subject to authorization (see Security Requirements, §10) and subject to a **protocol that is not yet chosen** — see Explicit Non-Assumptions below.
- Device credentials: reuse `RobotCredentialService`'s existing design (provision/rotate/revoke, audited, secret never logged) rather than invent a second credential system. `REQUIREMENT`.
- Device events: append-only event log per device (connection, disconnection, command-issued, command-result), mirroring `TaskEvent`/`CommandResult`'s append-only pattern. `REQUIREMENT`.
- Command authorization: an elevator-call request is authorized per-request (not a standing robot capability flag), at the same rigor tier as `ROBOT_UNLOCK` (§10). `REQUIREMENT`.
- Audit: every device registration, credential action, and elevator-call authorization is audit-logged (§11). `REQUIREMENT`.

**Explicit non-assumptions:**
- **Do not assume a specific elevator protocol.** No elevator communication protocol (proprietary REST, a building-management-system standard, a specific vendor's SDK, or something else) has been confirmed anywhere in this session's source evidence. This is recorded as **`OPEN QUESTION`** (§16 Q2), not resolved by this document, and must not be resolved by inventing a plausible-sounding protocol during implementation.
- No assumption is made about which real elevator hardware/vendor a pilot customer site actually has.
- No assumption is made about whether Keenon's own elevator integration (visible in the reference screenshots) could be depended on directly (§13) — recorded as an explicit tradeoff, not a decision.
