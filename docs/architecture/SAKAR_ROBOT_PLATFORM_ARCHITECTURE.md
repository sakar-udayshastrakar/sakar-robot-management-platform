# Sakar Robot Management Platform — Architecture

Companion to `SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md`. Design document only — nothing here has been implemented, and no existing project has been modified.

**Terminology note:** this document describes the platform generically. "Sakar Robot Agent" below refers to the conceptual robot-resident agent role — the current concrete implementation of that role is the `SakarC40Agent` Android module (see `robot/SakarC40Agent/`), built against the Keenon C40 / C40 S reference hardware for the first product, **Sakar CleanBot 5000 Plus**. Future robot models get their own agent implementation of the same role; see [SAKAR_ROBOT_PLATFORM_NAMING_AND_MODEL_STRATEGY.md](SAKAR_ROBOT_PLATFORM_NAMING_AND_MODEL_STRATEGY.md).

---

## 1. System Context

```
                         SAKAR ROBOT PLATFORM
                                │
        ┌───────────────────────┼───────────────────────┐
        │                       │                        │
        ▼                       ▼                        ▼
   Mobile App              Web Portal              Robot Tablet
  (Android/iOS)           (Admin/Web)          (Sakar Robot Agent)
        │                       │                        │
        └───────────────────────┼───────────────┐        │
                                ▼                │        │
                          SAKAR CLOUD             │        │
                                │                 │        │
                 ┌──────────────┼──────────────┐  │        │
                 ▼              ▼              ▼  │        │
             Backend        Database      Realtime│Channel │
           (API Gateway +   (Postgres+    (WS/MQTT─┘ or    │
            services)        cache)        HTTPS,          │
                 │                          §4)             │
                 │                                          │
                 └──────────────────────────────────────────┘
                        authenticated, secure link
                                                             │
                                                             ▼
                                                       Peanut SDK
                                                             │
                                                             ▼
                                                           C40
```

Mobile and Web never talk to the robot or the Peanut SDK directly — they only ever talk to the Sakar Backend. The Robot Tablet (Sakar Robot Agent — currently `SakarC40Agent`) is the **only** component that talks to the Peanut SDK, and it talks to the Sakar Backend as a client, not the other way around at the transport level (the backend pushes commands to the agent over whichever channel §4 selects, but the agent initiates the underlying connection — important for NAT/firewall reasons, see §4).

The existing, separately-audited **Keenon Cloud REST API** is explicitly *not* in this diagram as a primary path. It may be called by the Backend as an optional secondary data source (e.g., to pull already-accumulated historical cleaning logs) — this is a deliberate, isolated integration, not a dependency of the core platform.

---

## 2. Backend — Logical Services

Recommendation: **do not start with a microservices deployment.** Structure the backend as **one deployable application with clearly separated internal modules** (a "modular monolith"), each corresponding to one of the logical services below, communicating via well-defined internal interfaces. Split into separate deployable services later, only for the modules that actually need independent scaling (Telemetry Service and Command/Realtime Service are the most likely first candidates — see §2.2). This is justified by:
- The platform's initial scale (one initial robot model, a modest fleet) does not need the operational overhead of independent service deployment, service discovery, and distributed tracing from day one.
- A modular monolith with clean internal boundaries can be split later along exactly the seams already drawn below, without a rewrite.
- Over-engineering into microservices before the actual load/team-size justifies it is a documented anti-pattern the task explicitly warned against.

### 2.1 Logical services (module boundaries within the initial deployable)

| Service | Responsibility | Primary data owned |
|---|---|---|
| API Gateway | Single ingress, TLS termination, request routing, rate limiting, auth-token validation before routing to internal modules | none (stateless) |
| Authentication Service | Login, MFA, session/refresh tokens, password reset, device registration | `users` (credentials), sessions/tokens (short-lived, cache-backed) |
| User Service | User profile, role assignment | `users`, `roles`, `permissions` |
| Organization Service | Organizations, sites, tenant hierarchy | `organizations`, `sites` |
| Robot Registry | Robot identity, model, credentials, activation state, assignment to org/site | `robots`, `robot_models`, `robot_credentials` |
| Robot Command Service | Accepts control requests, applies authorization + command-security rules (§REQUIREMENTS §5.4), dispatches to the realtime channel, tracks command lifecycle | `robot_commands`, `command_results`, `robot_locks` |
| Telemetry Service | Ingests agent-reported telemetry/events, persists, computes derived status (online/offline, low-battery flag, etc.) | `robot_status`, `robot_telemetry`, `robot_events`, `robot_errors` |
| Task Service | Task orchestration (create/assign/start/pause/stop/cancel), lifecycle tracking | `robot_tasks`, `task_events` |
| Map Service | Map/point storage, cleaning-session/path association | `maps`, `map_points`, `cleaning_sessions`, `cleaning_history` |
| Alert Service | Threshold evaluation over telemetry/events, alert lifecycle | `robot_alerts` |
| Notification Service | Fan-out of alerts/events to push/email/in-app channels | `notifications` |
| Audit Service | Immutable recording of every sensitive action across all other services | `audit_logs` |
| Analytics Service | Aggregation/reporting over telemetry, tasks, cleaning, audit data | derived/materialized views over the above |

### 2.2 Why Telemetry/Command are the first split candidates
Telemetry ingestion is high-volume and write-heavy (many robots, frequent heartbeats); Command dispatch is latency-sensitive and needs a persistent realtime channel to agents. Both have different scaling and availability profiles than, say, the User/Organization services, which are low-volume CRUD. When/if the fleet grows enough to justify it, extract these two first, keeping everything else in the modular monolith.

### 2.3 Data flow — Telemetry (agent → dashboard)
```
Sakar Robot Agent (reads Peanut SDK locally)
   → HTTPS/WebSocket to API Gateway
      → Telemetry Service (validates, persists to robot_telemetry/robot_status)
         → Alert Service (evaluates thresholds; may create robot_alerts)
            → Notification Service (push/email as configured)
         → Analytics Service (async aggregation, not on the critical ingestion path)
      → Dashboard (Web/Mobile) reads via API Gateway → Telemetry Service query API
```

### 2.4 Data flow — Command (dashboard → robot)
```
Web/Mobile UI (user with required permission clicks Lock/Navigate/etc.)
   → API Gateway (authn/authz check)
      → Robot Command Service
         → Audit Service (records the request regardless of outcome)
         → Command Security check (§REQUIREMENTS §5.4: expiry, nonce, scope)
         → Realtime channel (§4) → Sakar Robot Agent
            → PeanutSdkBridge → Peanut SDK → C40
         ← Command acknowledgement/result → Robot Command Service
      → Audit Service (records the result)
   ← Status surfaced back to the UI (Sent / Acked / Confirmed / Timed Out / Failed — never collapsed into a single "success")
```

---

## 3. Security Architecture (technical)

### 3.1 Authentication
- JWT access tokens (short-lived, e.g. 15 minutes) + refresh tokens (longer-lived, rotated on use, revocable server-side via a session/refresh-token table so logout and remote-session-kill both actually work — a stateless-JWT-only design cannot support "logout everywhere," so refresh tokens must be tracked server-side even though access tokens are stateless).
- MFA (TOTP or SMS OTP) required for `SUPER_ADMIN`/`SAKAR_ADMIN`, optional for other roles (policy-configurable per organization).

### 3.2 Authorization
RBAC as specified in `SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md` §5.2, enforced at the API Gateway (coarse: is this token valid, does this role exist) and again at the service layer (fine: does this specific robot/org fall within this user's granted scope, per §5.3's hierarchy). **Never rely on gateway-level checks alone** — a compromised or misconfigured gateway rule must not be the only thing standing between a user and cross-tenant data.

### 3.3 Robot identity and credentials
Each robot is registered with a unique identity (serial number / `mftCode`, `CONFIRMED` available from the existing Keenon Cloud robot-list data) plus a Sakar-issued credential (e.g., an agent-specific API key or mTLS client certificate) stored in `robot_credentials`, used by the Sakar Robot Agent (currently `SakarC40Agent`) to authenticate to the Sakar Backend — **independent of, and unrelated to, the Peanut SDK's own `AppId`/`Secret` license**, which only governs SDK-to-robot communication and has no per-user or per-organization concept (per the SDK study's finding that the SDK license is an entitlement check, not an authorization system).

### 3.4 Command security implementation
Every command object (§REQUIREMENTS §5.4) is validated by the Robot Command Service before dispatch and again by the Agent before execution (defense in depth):
- **Command ID**: UUID, used for idempotency (duplicate delivery = no-op).
- **Expiration**: short window (e.g., 30-60 seconds for movement/lock commands); reject if expired on arrival at either the backend-to-agent hop or agent-side.
- **Nonce**: single-use, tracked per-robot for a bounded recent window, to prevent replay of a captured, still-unexpired command.
- **Authorization scope**: the permission(s) required, checked server-side at issuance and re-asserted in the command payload so the agent can perform a sanity check even without re-querying the backend.
- **Signature**: HMAC or asymmetric signature over the command payload using the robot's credential (§3.3), so the agent can reject a command that didn't actually originate from the Sakar Backend even if the transport channel were somehow compromised.

### 3.5 Lock security
`ROBOT_UNLOCK` must be a strictly separate, stronger-gated permission than `ROBOT_LOCK` (see the role matrix in Requirements §5.2) — reflecting the product principle "fail toward locked, require more to unlock." Recommend: unlock commands require a mandatory reason field, are never available to `TECHNICIAN`/`SAKAR_SUPPORT` by default, and are the one command type where MFA re-confirmation at time of action (not just at login) should be considered even for roles that already have MFA — this can be a P1 hardening item rather than blocking MVP, but the *permission separation* itself is P0.

### 3.6 Device/agent management (technical approach)
See `SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md` §10-equivalent content, technical detail here:
- **Device registration**: the tablet registers itself with the Robot Registry using a provisioning credential, receiving back its long-lived `robot_credentials` entry.
- **Agent authentication**: every backend call from the agent uses its robot-specific credential — never a shared/global secret.
- **Kiosk/device-owner mode**: Android's Device Owner / Device Policy Controller APIs (not part of the Peanut SDK — a separate Android platform capability) are the recommended mechanism to lock the tablet to running only `SakarC40Agent`, preventing the stock Keenon app (or any other app) from being launched at all. This is the **only** mechanism identified across both source studies that could actually satisfy Requirements §5.1 condition 6 with confidence, rather than relying on an app-layer toggle that a peer app can equally reverse.
- **Secure updates**: agent app updates should be signed and delivered through a controlled channel (e.g., Android's managed-configuration/EMM update path under device-owner mode, or a Sakar-controlled update mechanism) — not sideloading.
- **Unauthorized app prevention**: a direct consequence of kiosk/device-owner mode — do not treat this as a separate, softer control; treat "no other app can run" as the actual requirement, since "no other app *should* run" is exactly the assumption the SDK study showed is not otherwise enforced.

**Explicit statement required by the source task:** application-level locking alone (a toggle inside the Sakar Robot Agent) is **not** assumed sufficient. OS-level enforcement is the recommended primary control, with the application-level `enable()` call as the actual mechanism that achieves the lock state on the robot once OS-level enforcement ensures only the authorized agent can invoke it.

---

## 4. Robot Communication — Protocol Comparison and Recommendation

| Option | Real-time telemetry | Commands | Offline robots | NAT | Security | Scalability | Reconnection | Ack/dup handling |
|---|---|---|---|---|---|---|---|---|
| **A. HTTPS polling** | Poor (latency = poll interval) | Workable via a command-queue-and-poll pattern | Simple to reason about (agent just stops polling) | No issue — agent-initiated outbound only | Standard TLS + bearer auth, easy to reason about | Simple to scale (stateless requests, standard load balancing) | Trivial — every request is independent | Straightforward — each poll/response is a discrete, ack'able unit |
| **B. WebSocket** | Good (push-based, low latency) | Good — persistent channel for near-real-time dispatch | Requires explicit reconnect/backoff logic; agent must re-sync state on reconnect | Agent-initiated outbound connection avoids inbound-NAT problems | TLS + token-based auth on connect; needs its own heartbeat/keepalive design | Needs connection-state management (sticky sessions or a shared connection registry) as the fleet grows | Must be explicitly implemented (backoff, resubscribe, state reconciliation) | Must be explicitly implemented (ack messages, sequence numbers) |
| **C. MQTT** | Good (pub/sub, low latency, built for exactly this use case) | Good — QoS levels give built-in at-least-once/exactly-once delivery semantics | Well-suited — brokers natively support "last will" (offline detection) and persistent sessions/queued messages for offline clients | Agent-initiated broker connection; standard pattern for IoT fleets | TLS + per-device credentials (well-trodden pattern); broker ACLs give topic-level authorization for free | Purpose-built for large device fleets; brokers scale horizontally | Native reconnect/session-resume support in the protocol itself | QoS 1/2 give built-in ack and dedup semantics — the least custom code required here |
| **D. Hybrid** | Good | Good | Good | Good | Good | Good | Good | Good |

**Recommendation: Option D (Hybrid) — HTTPS for request/response and bulk telemetry batching, plus MQTT for real-time push (commands, heartbeats, event notifications).**

Rationale:
- **MQTT** is purpose-built for exactly this problem shape (many intermittently-connected devices, need for offline-awareness via last-will, need for reliable command delivery via QoS) and requires the least amount of custom-built reconnection/ack/dedup logic — those are the hardest parts of Option B to get right, and Option C gives them "for free" at the protocol level.
- **Plain HTTPS** remains the right tool for: robot registration/onboarding (one-time, not latency-sensitive), historical telemetry queries from Mobile/Web (request/response, not streaming), and bulk telemetry upload where a single batched POST is simpler and more efficient than many small pub/sub messages (e.g., a burst of buffered readings after a reconnect, per Requirements §5.6).
- Pure **HTTPS polling** alone (Option A) is the simplest to build and reason about, and is an acceptable **Phase 1/MVP fallback** if MQTT infrastructure isn't ready yet — but it will not meet the "commands feel real-time" expectation the product requires, and reconnection/dedup logic is still needed regardless (just less naturally supported).
- Pure **WebSocket** alone (Option B) can deliver the same real-time properties as MQTT but requires Sakar to hand-build reconnection, session resumption, and ack/dedup semantics that a broker already provides — not recommended as the sole channel given the standard, well-supported alternative.

**Command timeout / dedup / ack design (applies regardless of chosen transport, detailed for the MQTT case):**
- Commands are published to a per-robot topic with QoS 1 (at-least-once) and a client-generated command ID; the agent tracks recently-seen command IDs (bounded window) to dedup redelivery.
- The agent publishes an ack to a per-robot result topic immediately on receipt (before executing), and a separate result message on completion/failure — giving the backend three states to track (`sent → acked → completed/failed`), matching the "never collapse into one status" requirement from `SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md` §2.6.
- If no ack arrives within the command's expiration window (§3.4), the backend marks it `TIMED_OUT` and does **not** infer either success or failure of the underlying robot action.
- MQTT's "last will and testament" message, published by the broker when an agent's connection drops uncleanly, is the primary mechanism for near-real-time offline detection — backed up by a heartbeat-timeout fallback (§REQUIREMENTS §5.6) in case the last-will itself is not delivered (e.g., broker restart).

---

## 5. Data Ownership — Technical Flow

```
Robot generates state (motor, battery, navigation, etc.)
   ↓  (local SDK read — CONFIRMED local-only per PEANUT_SDK_C40_TECHNICAL_STUDY.md §7)
Sakar Robot Agent  (reads via PeanutSdkBridge)
   ↓  (authenticated HTTPS/MQTT — Sakar-controlled channel)
Sakar Backend  (Telemetry Service validates + persists)
   ↓
Sakar Database  (robot_telemetry, robot_status, robot_events, robot_errors — see SAKAR_ROBOT_PLATFORM_DATABASE.md)
   ↓
Sakar Dashboard (Web/Mobile query via API Gateway)
```

**What data is collected:** everything in the "CONFIRMED"-graded rows of `SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md` §2.5/§4.4 — battery, charging, motor status/health, runtime state, work mode, odometer, robot IP/properties, navigation status, emergency state, door state, map info, and whichever of the 65 named SDK topics are wired up.

**Where it is stored:** Sakar's own PostgreSQL database (or equivalent — see `SAKAR_ROBOT_PLATFORM_DATABASE.md`), on Sakar-controlled infrastructure.

**How long:** per the retention policy in `SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md` §10 (hot/cold tiering for telemetry; append-only, long-retention for audit logs) — exact numbers to be finalized during architecture sign-off, not invented here.

**Who can access it:** gated by the RBAC + multi-tenancy model in `SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md` §5.2/§5.3/§5.5, enforced server-side.

**Encryption:** TLS in transit (agent↔backend, backend↔clients); at-rest encryption for the database (standard managed-database encryption-at-rest, or disk-level encryption if self-hosted) — a baseline expectation, not a differentiator, and not optional.

**Backup / deletion / export:** standard database backup policy (§REQUIREMENTS §10); deletion must respect audit-log immutability (never delete audit records as part of a routine data-retention job — only per an explicit, logged, authorized data-retention/legal-hold process); export capability (e.g., CSV/JSON download of a robot's telemetry/task history) is a P1 feature, not required for MVP.

**Mandatory caveat, repeated here because it governs every claim in this section:** *"Network packet capture and physical C40 validation are required before claiming zero external Keenon communication."* This architecture is designed so that **Sakar's own data path** never requires Keenon Cloud — but it does not, and cannot, prove that the robot or its OTA subsystem never communicates with Keenon by some path outside Sakar's control. That is an explicit, standing caveat on the entire "data ownership" narrative, not a footnote to be dropped in later revisions without new evidence.

---

## 6. Multi-Robot-Model Extensibility

The first target product, **Sakar CleanBot 5000 Plus**, is initially built on the Keenon C40 / C40 S hardware platform, but the Sakar Robot Management Platform itself is designed as a multi-robot platform. Concretely, the architecture must not hardcode C40-specific assumptions into the Robot Registry or Telemetry Service schemas. Concretely: `robots` references a `robot_models` entity (see `SAKAR_ROBOT_PLATFORM_DATABASE.md`), and the Telemetry Service's ingestion contract is a generic key/value or JSON-document shape (not a rigid, C40-specific fixed-column table) so that a future robot model with a different vendor SDK can be onboarded by adding a new agent implementation and a new `robot_models` row, without a schema migration across the whole platform. This is a design constraint, not a feature to build now.

---

## 7. Current Tested Path vs. Target Sakar Architecture

**This distinction must be preserved in every summary of this document — do not collapse the two into one diagram.**

**Current tested path** (`KEENON-CLOUD DEPENDENT` — see `SAKAR_ROBOT_PLATFORM_MASTER_REQUIREMENTS.md` Part 10/40 and `SAKAR_LIVE_API_VALIDATION_MATRIX.md`). This is the only path exercised by the newly supplied live API evidence:

```
External Client (Postman / test harness)
        |
        v
Keenon Cloud / Open Platform   (https://cloud.robotkeenon.com)
        |
        v
Robot (Keenon C40 S)
```

**Target Sakar production architecture** (not yet built, not yet physically validated end-to-end):

```
Sakar Web / Mobile
        |
        v
Sakar API
        |
        v
Sakar Platform
        |
        v
Robot Integration / Robot Adapter
        |
        v
Sakar Robot Agent  (where applicable — see §8 capability mapping)
        |
        v
Robot
```

Keenon Cloud must **not** be represented as the primary Sakar application backend in any architecture summary, pitch, or customer-facing material. At the same time, this document does **not** claim Keenon Cloud has been eliminated — the current tested path depends entirely on it, and it remains a legitimate secondary/adapter-level integration (§8) until the Sakar-owned local path (Sakar Robot Agent → Peanut SDK, per §1/§5) is physically validated for the same capability set.

---

## 8. Robot Adapter Layer (Cloud-Side Capability Abstraction)

Full requirement text and the capability-command list live in `SAKAR_ROBOT_PLATFORM_MASTER_REQUIREMENTS.md` §6.A; this section gives the architectural placement. This is distinct from, and complementary to, the on-device `RobotAdapter` concept described in `SAKAR_ROBOT_PLATFORM_NAMING_AND_MODEL_STRATEGY.md` §6 (which isolates vendor-SDK calls *inside* a single Sakar Robot Agent instance) — the layer below is the **cloud-side** abstraction that lets the Robot Command Service (§2.1) dispatch to *either* a local agent-mediated robot *or* a cloud-mediated vendor integration without its own code caring which:

```
                    Sakar Platform
                         |
                   Sakar Cloud / API
                         |
                 Robot Abstraction
                         |
              Robot Adapter Layer
                         |
          +--------------+--------------+
          |              |              |
          v              v              v
    Keenon Adapter   Sakar Adapter   Other Adapter
          |              |              |
          v              v              v
     Keenon Robots   Sakar Robots   Future Robots
```

The Robot Command Service (§2.1) speaks only the generic capability commands (`GET_STATUS`, `GET_BATTERY`, `GET_TELEMETRY`, `START_TASK`, `STOP_TASK`, `PAUSE_TASK`, `RESUME_TASK`, `RETURN_TO_DOCK`, `LOCK`, `UNLOCK`) to this layer. **Today, the concrete `Keenon Adapter` implementation available is a Keenon-Cloud-backed adapter** (§7) — it satisfies `GET_STATUS`/`GET_BATTERY`/`GET_TELEMETRY` (partial)/`START_TASK`/`RETURN_TO_DOCK` per the live evidence, and returns `UNSUPPORTED_CAPABILITY` for `LOCK`/`UNLOCK` (Keenon Cloud does not expose motor control at all — that capability, if ever supported, can only come from a local Peanut-SDK-backed adapter path, Part 11). A future local-agent-backed `Keenon Adapter` variant, a `Sakar Adapter` for a Sakar-branded/non-Keenon robot, and any third-party `Other Adapter` all implement the same generic command interface — onboarding one requires a new `robot_models` row and a new adapter implementation, never a change to the Robot Command Service, the database schema, or the public API (`SAKAR_ROBOT_PLATFORM_API_SPEC.md`).

**Unsupported-capability rule:** if a robot model's `robot_models.capabilities` flags do not include a requested capability, the adapter layer returns `UNSUPPORTED_CAPABILITY` and the Web/Mobile UI must not render that control for that robot — server-driven, not inferred client-side from the robot's model name.
