# Sakar Robot Management Platform — Security Requirements

**Version:** 1.0 · **Date:** 2026-08-26 · **Status:** Requirements/design document — nothing implemented, no existing source code modified · **Companion to:** `SAKAR_ROBOT_PLATFORM_MASTER_REQUIREMENTS.md` (Parts 16–29, 35, 38 of that document are reproduced and expanded here as a standalone, self-contained security specification for independent review).

**Purpose of this document:** a security reviewer, auditor, or new engineer should be able to read this file alone and understand every security control the Sakar Robot Management Platform requires, without needing the full master requirements document. Cross-references to the master document's Parts are given for traceability, not as a dependency.

**Status vocabulary used throughout:** `PLANNED`, `REQUIREMENT`, `DESIGN COMPLETE`, `IMPLEMENTATION PENDING`, `REQUIRES PHYSICAL C40 TEST`, `REQUIRES NETWORK TEST`, `REQUIRES VENDOR SUPPORT`, `UNKNOWN`. `IMPLEMENTED` is never used — nothing described here has been built.

**Scope note on the underlying robot platform:** this document's robot-specific claims (Sections 8, 11, 19) are grounded in `PEANUT_SDK_C40_TECHNICAL_STUDY.md`, `PEANUT_SDK_C40_API_MATRIX.md`, `KEENON_C40_CLOUD_API_AUDIT.md`, and `KEENON_C40_API_TEST_RESULTS.json`. An SDK API existing is never treated as proof that the physical C40 behaves as expected — every such claim is graded `CONFIRMED` / `LIKELY` / `UNKNOWN` / `REQUIRES PHYSICAL C40 TEST` / `REQUIRES NETWORK TEST` / `REQUIRES VENDOR SUPPORT`.

**Terminology note:** "Sakar Robot Agent" below refers to the generic robot-resident agent role; the current concrete implementation of that role is the `SakarC40Agent` Android module, built for the first product, **Sakar CleanBot 5000 Plus**, against the Keenon C40 / C40 S reference hardware.

---

## Section 1 — Security Architecture

**Core principle:** the web and mobile applications communicate through Sakar Backend only. They never communicate directly with a robot, under any circumstance, at any layer.

```
                              Internet
                                 |
                                 v
                        +----------------+
                        |  WAF / Firewall |
                        +--------+-------+
                                 |
                                 v
                        +----------------+
                        |      Nginx      |  (TLS termination, routing, rate limiting)
                        +--------+-------+
                                 |
                                 v
                        +----------------+
                        |  Sakar Backend   |  (application logic, RBAC enforcement, command signing)
                        +--------+-------+
                                 |
                                 v
                     +-----------+-----------+
                     |     Private Network     |
                     |  +------------+           |
                     |  | PostgreSQL |            |
                     |  +------------+           |
                     |  |    Redis   |            |
                     |  +------------+           |
                     |  |    MQTT    |            |
                     |  +------------+           |
                     |  | Monitoring |            |
                     |  +------------+           |
                     +-----------+-----------+
                                 |
                                 v
                        +--------------------+
                        | Sakar Robot Agent  |  (per robot; authenticates to MQTT/backend independently)
                        +---------+----------+
                                 |
                                 v  (local only — Section 8)
                        +----------------+
                        |   Peanut SDK    |
                        +--------+-------+
                                 |
                                 v
                        +----------------+
                        |      C40        |
                        +----------------+
```

| Layer | Purpose | Status |
|---|---|---|
| WAF / Firewall | Filters malicious traffic before it reaches Nginx | `REQUIREMENT` |
| Nginx | TLS termination, request routing, coarse rate limiting | `REQUIREMENT` |
| Sakar Backend | Enforces authentication, RBAC, tenancy, command security | `REQUIREMENT` |
| Private network segmentation | PostgreSQL, Redis, MQTT broker, and monitoring have no public network exposure | `REQUIREMENT` |
| Sakar Robot Agent (currently `SakarC40Agent`) | Enforces local command validation before calling the Peanut SDK | `REQUIREMENT` |
| Peanut SDK / C40 | The physical boundary everything above protects | N/A (vendor component) |

**Non-goal, stated as a control:** neither the web nor the mobile application is ever granted network reachability to any robot, any MQTT topic outside its authorized scope, or any agent directly — enforced redundantly by network segmentation and application-layer authorization (Section 4).

*(Master document reference: Part 16.)*

---

## Section 2 — Security Threat Model

| # | Threat actor / scenario | Primary attack path | Primary mitigation | Status |
|---|---|---|---|---|
| T1 | External attacker via the internet | Exploits a web/API vulnerability | WAF, input validation, authentication, RBAC | `REQUIREMENT` |
| T2 | Malicious or compromised customer account | Valid credentials used to access another tenant's data/robots | Multi-tenant isolation, server-side authorization on every request | `REQUIREMENT` |
| T3 | Insider threat (Sakar staff) | Abuse of legitimate elevated access | RBAC least-privilege, audit logging, step-up auth for unlock | `REQUIREMENT` |
| T4 | Compromised robot/agent device | Rooted/tampered tablet forges telemetry or bypasses local safety | Device identity, agent integrity checks, secure local storage | `REQUIREMENT` |
| T5 | Network-level MITM | Intercepts/tampers with traffic in transit | TLS everywhere, certificate validation, no cleartext traffic | `REQUIREMENT` |
| T6 | Physical device theft | Stolen tablet used to extract credentials or operate the robot | Android Keystore, secure local storage, device-owner/kiosk enforcement | `REQUIREMENT` |
| T7 | Supply-chain / dependency compromise | Compromised third-party library | Dependency scanning, pinned versions, CI/CD security gates | `REQUIREMENT` |
| T8 | Command replay | Captured, re-sent command (e.g., an old UNLOCK) | Nonce + expiration on every command | `REQUIREMENT` |
| T9 | Command forgery | Crafted command outside authorized channels | Command signing, agent-side local validation | `REQUIREMENT` |
| T10 | Cross-robot message leakage (MQTT) | Robot A receives/subscribes to Robot B's commands/telemetry | Per-robot topic isolation + broker ACLs | `REQUIREMENT` |
| T11 | Keenon-side compromise or unexpected behavior | Stock app, Keenon Cloud, or OTA subsystem behaves unexpectedly | Accepted residual risk pending network testing; core architecture doesn't depend on Keenon Cloud | `REQUIRES NETWORK TEST` / `REQUIRES VENDOR SUPPORT` |
| T12 | Lock bypass via an unmanaged device | Any app with SDK access calls `enable(unlock)` outside Sakar's authorization | OS-level device management (kiosk/device-owner) | `REQUIRES PHYSICAL C40 TEST` |
| T13 | Database compromise | Direct or exfiltrated access to PostgreSQL | Private-network-only DB, least-privilege roles, encryption at rest | `REQUIREMENT` |
| T14 | Backup compromise | Stolen/leaked backup exposes historical data | Encrypted backups, access-controlled storage | `REQUIREMENT` |

*(Master document reference: Part 17. Each threat maps to one or more rows in Section 3's formal risk register.)*

---

## Section 3 — Security Risk Register

See `SAKAR_SECURITY_RISK_REGISTER.md` for the full standalone register with mitigation and validation detail. Twenty risks are tracked (R01–R20), spanning cloud/database compromise, credential/token theft, command forgery/replay, cross-tenant access, MQTT/WebSocket/Android/agent compromise, stock-app lock bypass, unresolved OTA/Keenon network traffic, insider threat, backup compromise, dependency vulnerabilities, and (added following live Keenon Open Platform API testing, §13.A) vendor-credential exposure and stale hardcoded vendor configuration values.

*(Master document reference: Part 18.)*

---

## Section 4 — Authentication & RBAC

**Role model:** `SUPER_ADMIN`, `ORG_ADMIN`, `SITE_ADMIN`, `OPERATOR`, `TECHNICIAN`, `VIEWER`.
**Permission model:** `ROBOT_VIEW`, `ROBOT_CONTROL`, `ROBOT_TASK_CREATE`, `ROBOT_TASK_CANCEL`, `ROBOT_LOCK`, `ROBOT_UNLOCK`, `ROBOT_CONFIGURE`, `ROBOT_DIAGNOSTICS`, `ROBOT_LOG_VIEW`, `AUDIT_VIEW`, `USER_MANAGE`, `ROLE_MANAGE`, `SYSTEM_ADMIN`.

### 4.1 Authentication requirements

| Control | Requirement | Status |
|---|---|---|
| Password hashing | Modern, salted, adaptive hashing (bcrypt/Argon2-class) — never reversible encryption, never unsalted | `REQUIREMENT` |
| Password policy | Minimum length/complexity; checked against known-breached-password lists | `REQUIREMENT` |
| Brute-force protection | Progressive delay and/or lockout after repeated failed attempts, per account and per source IP | `REQUIREMENT` |
| Rate limiting | Applied at the login endpoint independently of the general API rate limit | `REQUIREMENT` |
| Account lockout | Temporary lockout after a defined threshold, with a defined unlock path | `REQUIREMENT` |
| JWT | Signed access tokens carrying identity, org/site scope, permission claims | `REQUIREMENT` |
| Short-lived access tokens | Minutes-scale lifetime | `REQUIREMENT` |
| Refresh tokens | Longer-lived, used only to mint new access tokens | `REQUIREMENT` |
| Refresh token rotation | Each use issues a new refresh token, invalidates the old; reuse of an invalidated token is treated as a compromise signal | `REQUIREMENT` |
| Token revocation | Server-side capability to revoke a session immediately, independent of expiry | `REQUIREMENT` |
| Session invalidation | Logout, password change, role/permission change all invalidate existing sessions | `REQUIREMENT` |
| MFA | Available to all roles; **required** for `SUPER_ADMIN`/`ORG_ADMIN` | `REQUIREMENT` |
| Administrator MFA | Non-optional for any role holding `SYSTEM_ADMIN`, `USER_MANAGE`, or `ROLE_MANAGE` | `REQUIREMENT` |
| Step-up authentication | Required before `ROBOT_UNLOCK` is exercised, regardless of the session's original login method | `REQUIREMENT` |
| Secure password reset | Time-limited, single-use, out-of-band reset token; no plaintext password ever transmitted or displayed | `REQUIREMENT` |
| Secure logout | Invalidates both access and refresh tokens server-side | `REQUIREMENT` |

### 4.2 Authorization / RBAC

**Role → permission matrix (baseline):**

| Permission | `SUPER_ADMIN` | `ORG_ADMIN` | `SITE_ADMIN` | `OPERATOR` | `TECHNICIAN` | `VIEWER` |
|---|---|---|---|---|---|---|
| `ROBOT_VIEW` | Y | Y | Y | Y | Y | Y |
| `ROBOT_CONTROL` | Y | Y | Y | Y | Y | - |
| `ROBOT_TASK_CREATE` | Y | Y | Y | Y | - | - |
| `ROBOT_TASK_CANCEL` | Y | Y | Y | Y | Y | - |
| `ROBOT_LOCK` | Y | Y | Y | Y | - | - |
| `ROBOT_UNLOCK` | Y | Y (own org) | - | - | - | - |
| `ROBOT_CONFIGURE` | Y | Y (own org) | Y (own site) | - | Y | - |
| `ROBOT_DIAGNOSTICS` | Y | Y | Y | - | Y | - |
| `ROBOT_LOG_VIEW` | Y | Y | Y | Y | Y | Y |
| `AUDIT_VIEW` | Y | Y (own org) | - | - | - | - |
| `USER_MANAGE` | Y | Y (own org) | - | - | - | - |
| `ROLE_MANAGE` | Y | - | - | - | - | - |
| `SYSTEM_ADMIN` | Y | - | - | - | - | - |

**Rationale:** `ROBOT_UNLOCK` is granted only to `SUPER_ADMIN` and `ORG_ADMIN` — unlocking must be deliberately harder to obtain than locking. `SITE_ADMIN`/`OPERATOR`/`TECHNICIAN` can lock (a fail-safe action) but must escalate to an org-level admin to unlock.

**Tenancy hierarchy:** `Organization -> Site -> Robot`. A user's accessible-robot set is the union of robots under every site they're granted access to. **Every API call and UI list filters through this hierarchy server-side — a client-supplied `robot_id` is never trusted without a server-side authorization check.**

| Vulnerability class | Requirement |
|---|---|
| IDOR | Every object lookup by ID re-verifies the requesting user's authorization for that specific object |
| BOLA | Same, applied specifically to API objects (robots, tasks, commands, logs) |
| Cross-tenant access | No query path, cache key, or WebSocket subscription may return data for an unauthorized organization, even transiently |
| Privilege escalation | A user can never grant themselves/another user a role/permission outside what their own role may assign |

*(Master document reference: Part 19.)*

---

## Section 5 — Robot Command Security

```
USER -> AUTHENTICATION -> RBAC -> TENANT/SITE AUTHORIZATION -> COMMAND VALIDATION ->
COMMAND EXPIRATION -> NONCE/REPLAY PROTECTION -> SECURE TRANSPORT -> SAKAR BACKEND ->
SECURE MQTT/HTTPS -> SAKAR ROBOT AGENT -> LOCAL COMMAND VALIDATION -> PEANUT SDK -> C40
```

**Every command conceptually contains:** `command_id`, `robot_id`, `user_id`, `timestamp`, `expiration`, `nonce`, `command_type`, `request_id`, and an authorization context.

| Requirement | Description | Status |
|---|---|---|
| Replay protection | A previously-used `nonce` for a given robot is rejected on reuse | `REQUIREMENT` |
| Command expiration | Every command carries an `expiration`; expired commands are rejected without execution | `REQUIREMENT` |
| Duplicate command protection | Idempotent handling keyed on `command_id` | `REQUIREMENT` |
| Robot identity validation | The agent verifies `robot_id` matches its own identity before acting | `REQUIREMENT` |
| Command authorization | Backend verifies permission + tenancy scope at issuance; command carries that context for the agent to also check | `REQUIREMENT` |
| Command audit | Every command's full lifecycle is recorded, win or fail | `REQUIREMENT` |
| Command acknowledgement | The agent acknowledges receipt independently of the eventual result | `REQUIREMENT` |
| Command result | A terminal result is reported distinct from acknowledgement | `REQUIREMENT` |
| Command timeout | Backend marks a command timed-out if no result arrives within a defined window | `REQUIREMENT` |
| Local command validation | The agent independently re-validates freshness/nonce/scope before invoking the SDK — defense in depth | `REQUIREMENT` |

*(Master document reference: Part 20.)*

---

## Section 6 — Remote Lock/Unlock Security (the platform's highest-risk feature)

**Authorization model:**

| Action | Permission | Additional requirement |
|---|---|---|
| LOCK | `ROBOT_LOCK` | Reason (required); audit record |
| UNLOCK | `ROBOT_UNLOCK` (strictly stronger than `ROBOT_LOCK`) | Reason (required); step-up authentication/MFA; audit record |

**Audit fields:** `user`, `organization`, `site`, `robot`, `command_id`, `timestamp`, `ip`, `device`, `reason`, `result`.

**SDK capability vs. physical behavior — the distinction this entire section exists to enforce:**

| Claim | Status |
|---|---|
| SDK exposes a lock API | `CONFIRMED` — `MotorComponent.enable(IDataCallback, int)`, `MOTOR_ENABLE_LOCK=1`/`MOTOR_ENABLE_UNLOCK=0` (values recovered only from decompiled bytecode) |
| SDK reports lock status | `CONFIRMED` — `getStatus()` returns 255/16/32 |
| Lock physically prevents movement | `REQUIRES PHYSICAL C40 TEST` |
| Navigation/manual control cannot bypass | `REQUIRES PHYSICAL C40 TEST` |
| Lock survives restart/reboot/network loss | `REQUIRES PHYSICAL C40 TEST` (all three, separately) |
| Unauthorized local user cannot bypass | Cannot be claimed true today |
| Stock Keenon app cannot bypass, or OS-level enforcement prevents it | `LIKELY FALSE` as an app-only claim — requires Section 8's device management controls |
| Only authorized Sakar users can unlock | `CONFIRMED` in the software authorization layer only — does not by itself prevent the device-level bypass |
| Power-cycle behavior is understood | `REQUIRES PHYSICAL C40 TEST` |

**This feature may not be marked production-ready until Section 11's ten physical conditions are all confirmed. Until then, it must be presented in the product as "requested/best-effort," never as a guaranteed safety control.**

*(Master document reference: Part 11.)*

---

## Section 7 — Sakar Robot Event & Log System (SRELS) Security

Six categories are deliberately separated: Application Logs, Robot Events, Robot Errors, Robot Command Log, Security/Audit Log, Telemetry. Every record carries a correlation/request ID, timestamp, severity, source, and (where applicable) `robot_id`/`organization_id`/`site_id`/`user_id`/`command_id`.

**Immutability requirement:** security-sensitive events (the Security/Audit Log in full, and the security-relevant subset of Robot Events/Command Log — e.g. `MOTOR_LOCKED`/`MOTOR_UNLOCKED`, every command record) are append-only from the application's perspective. No UI, API, or ordinary database role may update or delete them. At the database level, the application's role is granted `INSERT`/`SELECT` only on `audit_logs` — never `UPDATE`/`DELETE`. A correction is a new, linked record, never a mutation.

*(Master document reference: Part 12, Part 13.)*

---

## Section 8 — Android / Sakar Robot Agent Security

**These are requirements only. `SakarC40Agent`'s (the current Sakar Robot Agent implementation) existing source was read-only during this review and was not modified.**

| Control | Requirement | Status |
|---|---|---|
| Android Keystore | Robot credentials and signing material stored in the Keystore, never in plain files or `SharedPreferences` | `REQUIREMENT` |
| Secure local storage | Locally-cached telemetry/command data encrypted appropriate to its classification (Section 9) | `REQUIREMENT` |
| No hardcoded secrets | No credential/key/secret compiled into the APK — see Section 10 | `REQUIREMENT` |
| Release build hardening | Signed release builds, debugging/logging disabled | `REQUIREMENT` |
| Debug disabled in production | `android:debuggable="false"`, no verbose sensitive logging | `REQUIREMENT` |
| Minimal permissions | Only demonstrably-needed Android permissions requested; existing declarations (`INTERNET`, `ACCESS_NETWORK_STATE`, `READ_PHONE_STATE`) re-justified at implementation | `REQUIREMENT` |
| Secure exported components | No component exported unless necessary; every exported component enforces its own permission check | `REQUIREMENT` |
| Secure services / receivers | Validate caller/broadcast origin before acting | `REQUIREMENT` |
| Certificate validation | Never disabled/bypassed in production | `REQUIREMENT` |
| TLS / no cleartext traffic | All agent traffic TLS-protected; network security config disallows cleartext HTTP | `REQUIREMENT` |
| App integrity | Tamper/repackaging detection evaluated at implementation | `REQUIREMENT` |
| Device identity | Each agent install has its own identity, distinct from the robot's | `REQUIREMENT` |
| Agent identity / version | Backend can identify agent build/version to detect unexpected/outdated agents | `REQUIREMENT` |
| Local security | Agent enforces its own `guard()`/`OperatingMode` gate as defense-in-depth | `REQUIREMENT` (extends existing pattern) |
| ADB / USB debugging restrictions | Disabled on production tablets; field exceptions are deliberate, audited, time-boxed | `REQUIREMENT` |
| Kiosk / device-owner mode | Where Section 11/Section 12 determines OS-level enforcement is necessary for the lock feature, the tablet is enrolled in device-owner/kiosk mode restricting which apps (including the stock Keenon app) may run | `REQUIRES PHYSICAL C40 TEST` (necessity) + `REQUIREMENT` (once confirmed) |

*(Master document reference: Part 21.)*

---

## Section 9 — Network Security

| Control | Requirement | Status |
|---|---|---|
| Public database access | None — PostgreSQL, Redis, MQTT broker reachable only within the private network | `REQUIREMENT` |
| Firewall rules | Default-deny; only necessary ports/protocols opened, only to necessary peers | `REQUIREMENT` |
| WAF | Filters common web/API attack patterns | `REQUIREMENT` |
| DDoS/volumetric protection | WAF/CDN layer or equivalent | `REQUIREMENT` |
| Network segmentation | Backend, database tier, monitoring logically/physically separate from each other and from test/staging | `REQUIREMENT` |
| VPN/bastion for admin access | Direct administrative access never exposed to the open internet | `REQUIREMENT` |

### 9.1 Keenon Network Security — Physical Test Requirement (unresolved by design)

| Item | Status |
|---|---|
| Stock Keenon application's own network traffic | `UNKNOWN` — `REQUIRES NETWORK TEST` |
| OTA/update subsystem traffic | `UNKNOWN` — `REQUIRES NETWORK TEST` |
| Unexpected outbound traffic generally | `UNKNOWN` — `REQUIRES NETWORK TEST` |
| DNS requests | `UNKNOWN` — `REQUIRES NETWORK TEST` |
| HTTPS connections to unknown endpoints | `UNKNOWN` — `REQUIRES NETWORK TEST` |

**Future test:** isolated VLAN → router/firewall → packet capture → internet, recording destination IP, resolved domain, protocol, port, frequency, and inferred purpose for every connection.

**No summary of this document may claim Keenon outbound communication has been eliminated or fully characterized until this test is performed.**

*(Master document reference: Part 22.)*

---

## Section 10 — MQTT Security

**Updated 2026-08-29 (Phase 3 Security Hardening) — see `SAKAR_PHASE_3_SECURITY_HARDENING_REPORT.md` for full evidence.** Per that report's own governing distinction: a row marked `DESIGN COMPLETE` below is **software-hardened and automated-test-verified only** — it has not been run against a live broker or a physical robot, and is not a claim of production readiness.

| Control | Requirement | Status |
|---|---|---|
| TLS | All MQTT connections TLS-protected | `DESIGN COMPLETE` — both backend and agent accept `ssl://` broker URLs with real (never trust-all) certificate validation, plus optional private-CA truststore config; `REQUIRES NETWORK TEST` to confirm against an actual TLS-terminating broker |
| Unique robot credentials | Each robot's agent authenticates with its own credential — no shared/global credential | `DESIGN COMPLETE` (issuance/storage) / `REQUIREMENT` (broker enforcement) — `robot_credentials` now populated via `POST /api/v1/robots/{id}/mqtt-credentials`, BCrypt-hashed, one-time-returned; the dev broker does not yet check it at CONNECT time |
| Unique robot certificates (where feasible) | Client-certificate auth per robot is the preferred long-term posture | `REQUIREMENT` — not attempted this phase (out of scope; username/password credential issued instead) |
| ACL | Per-client topic ACLs — a robot's credential can only publish/subscribe to its own topics | `REQUIREMENT` — `REQUIRES BROKER CONFIGURATION`; the software-side equivalent (robot/tenant identity cross-check on every message, independent of the broker) is `DESIGN COMPLETE` |
| Topic isolation | Namespaced by organization, site, robot | `DESIGN COMPLETE` — implemented exactly as specified (`MqttTopicResolver`/`AgentMqttTopics`), test-covered |
| Organization / robot isolation | No cross-org credential access; **Robot A must never receive Robot B's commands**, enforced by ACL matching the topic scheme | `DESIGN COMPLETE` (software cross-check) / `REQUIREMENT` (broker ACL) — see R08 in the risk register for the exact split |
| Credential rotation | Rotatable without a full agent redeploy | `DESIGN COMPLETE` — `POST` re-issues (rotates) and `DELETE` revokes, both audit-logged; revocation is not yet broker-enforced (see R08) |
| Connection limits | Per-credential connection caps | `REQUIREMENT` — not implemented; would require broker-side configuration |
| Message validation | Backend validates schema/sender before acting — broker delivery is not proof of validity | `DESIGN COMPLETE` — schema version, required fields, timestamp skew (both directions), sequence sanity, payload size cap, per-robot rate limit, all enforced server-side regardless of what the broker does or doesn't check |

**Topic scheme:**
```
sakar/{organization}/{site}/{robot}/telemetry
sakar/{organization}/{site}/{robot}/events
sakar/{organization}/{site}/{robot}/commands
sakar/{organization}/{site}/{robot}/ack
```

*(Master document reference: Part 23.)*

---

## Section 11 — WebSocket Security

| Control | Requirement | Status |
|---|---|---|
| Authentication | Only accepted from a client presenting a valid, unexpired access token | `REQUIREMENT` |
| Authorization | Every subscription checked against robot-level authorization at subscribe time | `REQUIREMENT` |
| Tenant isolation | A user only receives events for authorized robots, enforced per message | `REQUIREMENT` |
| Origin validation | `Origin` header checked against an allow-list | `REQUIREMENT` |
| Rate limiting | Connection-establishment and message rate both limited | `REQUIREMENT` |
| Connection limits | Cap on concurrent connections per user/session | `REQUIREMENT` |
| Token expiry | Connection terminated/re-authenticated when the tied access token expires | `REQUIREMENT` |
| Message validation | Every inbound message schema-validated | `REQUIREMENT` |
| Heartbeat | Application-level ping/pong to detect dead connections | `REQUIREMENT` |
| Timeout | Idle connections closed after a defined window | `REQUIREMENT` |

*(Master document reference: Part 24.)*

---

## Section 12 — Data Protection

| Classification | Examples |
|---|---|
| `PUBLIC` | Marketing material, public documentation |
| `INTERNAL` | Aggregate/anonymized analytics, non-sensitive configuration |
| `CONFIDENTIAL` | Robot telemetry, events/errors, task/cleaning history, site/organizational information |
| `HIGHLY_CONFIDENTIAL` | Robot credentials, user credentials, access/refresh tokens, MFA secrets, audit records, signing/API keys |

| Control | Requirement | Status |
|---|---|---|
| Transport encryption | TLS 1.2 minimum, 1.3 preferred, every hop, no exceptions for internal traffic | `REQUIREMENT` |
| Encryption at rest | Database volume encryption; encrypted backups | `REQUIREMENT` |
| Secret handling in logs | Never written to any log; explicit redaction of known-sensitive fields | `REQUIREMENT` |
| Classification-driven handling | `HIGHLY_CONFIDENTIAL` data has stricter access/retention/export controls than `CONFIDENTIAL` | `REQUIREMENT` |
| Data minimization | Only data with a defined product/operational purpose is collected | `REQUIREMENT` |

*(Master document reference: Part 25.)*

---

## Section 13 — Secrets Management

**No production secret may ever appear in:** React source, mobile app source/bundles, Git history (including old commits), Dockerfiles, public configuration files, or APK resources.

| Control | Requirement | Status |
|---|---|---|
| No hardcoded passwords/API keys/JWT signing secrets/database passwords | Never in source, committed config, or container images | `REQUIREMENT` |
| No MQTT passwords in source | Robot/broker credentials provisioned at runtime | `REQUIREMENT` |
| No private keys in Git | Including history — a leaked key is compromised even if later removed | `REQUIREMENT` |
| Secret rotation | Defined procedure and cadence for every secret class | `REQUIREMENT` |
| Credential rotation | Robot/agent credentials individually rotatable | `REQUIREMENT` |
| Environment separation | Dev/staging/production use entirely distinct secrets | `REQUIREMENT` |
| Secret manager | Dedicated secrets store for all production secrets | `REQUIREMENT` |
| Secure robot credentials | Unique per robot, provisioned at registration, Keystore-backed on-device | `REQUIREMENT` |

**Self-check applied to this document:** no credential, token, or secret value from any prior audit appears anywhere in this document or its companions — verified by direct text search before publication.

### 13.A Vendor (Keenon Open Platform) Credential & API Handling

Added following the newly supplied live Keenon Open Platform API testing reference (`SAKAR_ROBOT_PLATFORM_MASTER_REQUIREMENTS.md` Part 40). The tested integration authenticates to `https://cloud.robotkeenon.com` via OAuth `client_credentials` (`client_id`/`client_secret` → bearer `access_token`) — the same custody rules that apply to every other production secret in this section apply here without exception:

| Control | Requirement | Status |
|---|---|---|
| Keenon credentials server-side only | `client_id`/`client_secret` and the resulting `access_token` are never sent to, stored in, or reachable from the Web application, mobile application, or any other frontend — held only by the backend-side Keenon Integration Adapter (`SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md` §8) | `REQUIREMENT` |
| OAuth client_secret never reaches frontend | Same as above, stated explicitly for this specific credential given the Postman testing reference exercised it directly | `REQUIREMENT` |
| Access tokens not stored in frontend source | The bearer `access_token` returned by `/api/open/oauth/token` is cached only in backend-controlled storage (e.g., the secret manager or a short-lived server-side cache), never in a mobile bundle, browser storage, or React source | `REQUIREMENT` |
| Keenon-specific IDs treated as internal integration data | Store ID, robot SN/ID, scene code, map ID, area IDs, and charging-point IDs (all exercised in the live test) are internal integration data — external users see only the corresponding Sakar-issued `robots`/`sites`/`organizations` identifiers, never the raw Keenon values | `REQUIREMENT` |
| Vendor API credentials in a secret manager | `client_id`/`client_secret` for Keenon Open Platform are stored in the same secret manager as every other production secret (this Section, top-level table) — not in a `.env` file, Postman environment export, or config file committed to any repository | `REQUIREMENT` |
| External users see Sakar identifiers only | Consistent with the API vendor-neutrality boundary (`SAKAR_ROBOT_PLATFORM_API_SPEC.md`, "Vendor integration boundary") — no Keenon endpoint path, field name, or status code (e.g., `610000`, `CleanStrategyTemporary`) is ever surfaced through a Sakar client-facing API or UI | `REQUIREMENT` |
| Organization/site authorization enforced on vendor-backed data | Data retrieved via the Keenon Adapter is subject to the same `Organization -> Site -> Robot` authorization hierarchy (Part 19) as data from any other adapter — a vendor-backed robot is not a lower-authorization-bar robot | `REQUIREMENT` |
| Vendor API failures do not expose credentials/errors | A Keenon Open Platform error response (including any embedded diagnostic detail) is never relayed verbatim to a Web/Mobile client — the backend translates it to the generic error model (`SAKAR_ROBOT_PLATFORM_API_SPEC.md` §1.12) before responding | `REQUIREMENT` |
| Vendor API request/response audited | Every call to a Keenon Open Platform endpoint that changes robot state (`START_TASK`/`STOP_TASK`/`PAUSE_TASK`/`RETURN_TO_DOCK` equivalents) is recorded in `robot_commands`/`audit_logs` exactly as any other command would be (Part 12.D/12.E) — a vendor-mediated command is not exempt from the command audit trail | `REQUIREMENT` |

**Explicit note on the placeholders in the source testing document:** the supplied `SAKAR_KEENON_C40S_LIVE_API_TESTING_REFERENCE.pdf` itself states that access tokens, client secrets, and credentials are represented as placeholders (`{{token}}`, `{{client_id}}`, `{{client_secret}}`) — no real credential value was present in that document, and none is reproduced here. A workspace-wide secret scan performed while producing this revision found no real Keenon credential, access token, or API key anywhere in this repository (see the Final Report of the task that produced this revision).

*(Master document reference: Part 26, Part 40.)*

---

## Section 14 — Backup & Disaster Recovery

| Control | Requirement | Status |
|---|---|---|
| Backup frequency | Daily minimum for production database | `REQUIREMENT` |
| Backup encryption | Every backup encrypted at rest, keys managed separately from storage | `REQUIREMENT` |
| Separate backup storage | Distinct location/account from production | `REQUIREMENT` |
| Restore testing | Tested on a defined cadence | `REQUIREMENT` |
| RPO | Defined at architecture sign-off | `REQUIREMENT` |
| RTO | Defined at architecture sign-off | `REQUIREMENT` |
| Disaster recovery plan | Documented runbook for major infrastructure loss | `REQUIREMENT` |
| Backup monitoring | Success/failure monitored and alerted; silent failure = production incident | `REQUIREMENT` |

*(Master document reference: Part 27.)*

---

## Section 15 — Monitoring

| Condition | Why it matters |
|---|---|
| Failed logins (repeated) | Credential-stuffing/brute-force signal |
| Repeated unlock attempts | Direct signal against the highest-risk feature |
| Suspicious commands | Possible compromised account or insider misuse |
| Command replay attempts | Direct signal of an attempted replay attack |
| MQTT authentication failures | Possible credential compromise or rogue device |
| Unusual API traffic | Possible scraping/enumeration/attack reconnaissance |
| Unusual robot activity inconsistent with any issued command | Possible bypass of Sakar's own command path |
| Privilege changes | Always alerted, never just logged |
| New admin users | Always alerted |
| Credential changes | Especially for privileged accounts |
| Unexpected agent versions | Unrecognized agent build connecting |
| Unexpected network traffic | Inconsistent with the architecture in Section 1 |

**Recommended tooling:** Prometheus + Grafana for metrics; centralized aggregation for the six SRELS categories plus infrastructure logs; a dedicated security-alerting channel distinct from operational alerting.

*(Master document reference: Part 28.)*

---

## Section 16 — Security Testing

**None of the following is executed by this document; no destructive testing is ever performed against the physical C40.**

Authentication · Authorization/RBAC · Tenant isolation · IDOR · BOLA · Privilege escalation · JWT · Refresh tokens · Replay attacks · Command forgery · MQTT · WebSocket · SQL injection · XSS · CSRF · SSRF · Path traversal · Rate limiting · Android security · Robot agent (software-level only) · Lock/unlock (authorization-chain testing in software; physical effect testing governed exclusively by Section 17/Part 38, not this program).

**CI/CD security gate:** dependency vulnerability scanning and static analysis for the categories above run in the build pipeline before any release candidate is promoted toward production.

*(Master document reference: Part 29.)*

---

## Section 17 — Security Acceptance Gates

| Gate | Covers | Conditions | Status |
|---|---|---|---|
| G1 — Authentication Hardening | Section 4.1 | Password policy, brute-force protection, MFA, token rotation/revocation implemented and tested | `REQUIREMENT` |
| G2 — Authorization & Tenant Isolation | Section 4.2 | RBAC enforced server-side everywhere; IDOR/BOLA/cross-tenant tests pass with zero findings | `REQUIREMENT` |
| G3 — Command Security | Section 5 | Replay, expiration, duplicate-protection, identity-validation implemented and tested | `REQUIREMENT` |
| G4 — Transport & Broker Security | Sections 9-11 | Network segmentation live; MQTT ACLs enforced; WebSocket tenant-scoping enforced | `REQUIREMENT` |
| G5 — Secrets & Data Protection | Sections 12-13 | No secret in source/Git/images; encryption verified; classification applied | `REQUIREMENT` |
| G6 — Backup & DR | Section 14 | At least one successful restore drill against a realistic dataset | `REQUIREMENT` |
| G7 — Security Testing Sign-off | Section 16 | Full testing program executed; no open critical/high findings | `REQUIREMENT` |
| G8 — Lock/Unlock Physical Certification | Section 6, Part 38 | All ten physical conditions `CONFIRMED`; stock-app bypass disproven or mitigated | `REQUIRES PHYSICAL C40 TEST` |
| G9 — Keenon Network Disclosure | Section 9.1 | Physical packet-capture test executed and findings accepted before any "data stays on Sakar" claim | `REQUIRES PHYSICAL NETWORK TEST` |

**Gates are cumulative and may not be waived silently** — a decision to ship ahead of a gate must be documented with an accountable owner in the risk register, never implied by omission.

*(Master document reference: Part 35.)*

---

## Section 18 — Physical C40 Validation Plan (Security-Relevant Subset)

Full plan (foundational SDK tests, the ten lock/unlock conditions, and the Keenon network capture design) is in the master document's Part 38. Reproduced summary of the ten lock/unlock conditions, since they are this document's central concern:

1. Lock prevents physical movement.
2. Navigation cannot bypass the lock.
3. Manual control cannot bypass the lock.
4. Lock survives agent restart.
5. Lock survives robot reboot.
6. Lock survives network loss.
7. Unauthorized local user cannot bypass the lock.
8. Stock Keenon application cannot bypass the lock (or OS-level enforcement demonstrably prevents it).
9. Authorized Sakar user can unlock.
10. Power-cycle behavior is understood and documented.

**No test in this plan has been executed. This document does not claim any of these ten conditions are met.**

*(Master document reference: Part 38.)*

---

## Closing Statement

This document specifies requirements only. Every control above is a target for a future implementation phase, gated by the acceptance criteria in Section 17. No software was written, modified, or deployed to produce this specification; `SakarC40Agent`, the Peanut SDK, `peanut-sdk-release.aar`, and every existing backend/frontend/mobile/database/CI artifact were read-only inputs, never edited. Physical C40 hardware and physical network capture equipment were not connected to or used during this review.
