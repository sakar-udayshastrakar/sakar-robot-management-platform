# Sakar Robot Management Platform — Security Risk Register

**Version:** 1.0 · **Date:** 2026-08-26 · **Status:** Requirements/design document — nothing implemented · **Companion to:** `SAKAR_ROBOT_PLATFORM_MASTER_REQUIREMENTS.md` (Part 18) and `SAKAR_SECURITY_REQUIREMENTS.md` (Section 3).

**Purpose:** the authoritative, standalone security risk register for the platform. Every risk here maps to one or more threats in the threat model (`SAKAR_SECURITY_REQUIREMENTS.md` Section 2) and one or more controls in the security requirements. This register must be reviewed at every security acceptance gate and updated with the outcome of each physical/network test as it completes. **A risk leaves this register only by being re-graded with evidence attached — never by deletion.**

**Status vocabulary:** `PLANNED`, `REQUIREMENT`, `DESIGN COMPLETE`, `IMPLEMENTATION PENDING`, `REQUIRES PHYSICAL C40 TEST`, `REQUIRES NETWORK TEST`, `REQUIRES VENDOR SUPPORT`, `UNKNOWN`. `IMPLEMENTED` is never used in this document.

**Severity scale:** `Critical` (could enable unauthorized physical robot control, mass data breach across tenants, or total loss of the primary data store), `High` (could enable single-tenant compromise, credential/token theft, or a significant safety-relevant bypass), `Medium` (could enable a contained information disclosure or a degraded-but-recoverable security posture), `Low` (hardening/defense-in-depth gaps with no direct exploit path identified).

| ID | Risk | Severity | Current Status | Mitigation | Validation |
|---|---|---|---|---|---|
| R01 | Cloud/backend compromise (attacker gains control of the Sakar Backend application) | Critical | `REQUIREMENT` | WAF, hardened Nginx/backend configuration, patch management, network segmentation (Security Architecture) | Penetration test covering the full external attack surface (Security Testing) |
| R02 | Database compromise (direct or exfiltrated access to PostgreSQL) | Critical | `REQUIREMENT` | Private-network-only database, least-privilege database roles, encryption at rest, no public exposure | Database security review; verify no route from the public internet reaches PostgreSQL directly |
| R03 | Credential theft (user password/session compromised) | High | `REQUIREMENT` | Salted adaptive password hashing, MFA, brute-force protection, account lockout | Authentication security test (credential-stuffing simulation, lockout verification) |
| R04 | Token theft (JWT access or refresh token compromised) | High | `REQUIREMENT` | Short-lived access tokens, refresh-token rotation with reuse detection, server-side revocation | Token security test (replay-after-rotation, revocation propagation) |
| R05 | Robot command forgery (a command is issued without a valid authorization chain) | Critical | `REQUIREMENT` | Command signing, full authorization chain (auth -> RBAC -> tenancy -> validation), agent-side local re-validation | Command security test: attempt to submit a command bypassing each stage of the chain individually |
| R06 | Command replay (a previously valid command, e.g. an old UNLOCK, is re-sent) | High | `REQUIREMENT` | Per-command nonce + expiration, agent-side nonce tracking | Replay attack test: capture and re-submit a valid command, confirm rejection |
| R07 | Cross-tenant access (a user reaches another organization's data or robots) | Critical | `REQUIREMENT` | Server-side authorization on every request, `Organization -> Site -> Robot` hierarchy enforced at the data-access layer | IDOR/BOLA test suite across every ID-bearing endpoint; explicit cross-org negative tests |
| R08 | MQTT compromise (broker credential theft or ACL bypass) | High | `REQUIREMENT` | TLS, unique per-robot credentials, per-client topic ACLs, connection limits | MQTT security test: attempt cross-robot topic access with a valid but differently-scoped credential |
| R09 | WebSocket compromise (unauthorized subscription to another tenant's event stream) | Medium | `REQUIREMENT` | Auth-on-connect, origin validation, per-message tenant-scoped authorization | WebSocket security test: attempt to subscribe to an unauthorized robot's channel |
| R10 | Android device compromise (rooted/tampered robot tablet) | High | `REQUIREMENT` | Android Keystore for credentials, secure local storage, minimal permissions, app-integrity checks | Android security review/static-dynamic analysis of the agent APK |
| R11 | SakarC40Agent compromise (the agent process itself is subverted) | High | `REQUIREMENT` | App integrity checks, minimal permissions, secure exported components/services/receivers, local `guard()` gate as defense-in-depth | Agent security review; attempt to invoke a gated SDK call bypassing the local authorization gate |
| R12 | Stock Keenon application bypasses the Sakar-issued lock | High | `REQUIRES PHYSICAL C40 TEST` | OS-level device management (kiosk/device-owner mode) once confirmed necessary; documented as unresolved until tested | Physical validation plan, test 8 (launch the stock app while Sakar's lock is engaged and attempt to unlock/drive) |
| R13 | Remote lock does not physically work as expected (motor lock has no real effect, or navigation/manual control can override it) | Critical | `REQUIRES PHYSICAL C40 TEST` | Full ten-condition physical validation plan must pass before any production claim | Physical validation plan, tests 1–3 and 10 |
| R14 | Unexpected OTA/update subsystem network communication | Medium | `REQUIRES NETWORK TEST` | Isolated-VLAN packet capture designed but not yet executed; architecture does not depend on this subsystem for core function | Physical Keenon network test (destination IP/domain/protocol/port/frequency capture) |
| R15 | Unknown Keenon network traffic from the stock app or Android OS-level services | Medium | `REQUIRES NETWORK TEST` | Same packet-capture test as R14, run for a period covering charge/idle/task cycles | Same as R14 |
| R16 | Insider threat (a Sakar staff member misuses legitimate elevated access) | Medium | `REQUIREMENT` | Least-privilege RBAC, mandatory audit logging of every sensitive action, step-up authentication specifically for unlock | Periodic audit-log review process; alerting on privilege changes and new admin users |
| R17 | Backup compromise (stolen or leaked backup exposes historical operational/customer data) | High | `REQUIREMENT` | Encrypted backups with separately-managed keys, storage location distinct from production | Backup security review; confirm backup access requires credentials distinct from production access |
| R18 | Dependency vulnerability (a third-party library used by backend/web/mobile/agent has a known CVE) | Medium | `REQUIREMENT` | Dependency vulnerability scanning integrated into the CI/CD pipeline, defined patch cadence | Automated scanning report reviewed before each release |

## Risk-to-Threat Cross-Reference

| Risk ID | Related threat(s) (`SAKAR_SECURITY_REQUIREMENTS.md` Section 2) |
|---|---|
| R01 | T1, T7 |
| R02 | T13 |
| R03 | T2, T3 |
| R04 | T2, T5 |
| R05 | T9 |
| R06 | T8 |
| R07 | T2 |
| R08 | T10 |
| R09 | T2, T10 |
| R10 | T4, T6 |
| R11 | T4 |
| R12 | T12 |
| R13 | T12 |
| R14 | T11 |
| R15 | T11 |
| R16 | T3 |
| R17 | T14 |
| R18 | T7 |

## Gate Dependency

| Risk ID | Blocked by / resolved through Security Acceptance Gate |
|---|---|
| R01, R05, R06, R07 | G1, G2, G3 |
| R08, R09 | G4 |
| R02, R17, R18 | G5, G6, G7 |
| R10, R11 | G4 (partially), Section 8/Part 21 implementation |
| R12, R13 | G8 |
| R14, R15 | G9 |
| R16 | G1, G2 (ongoing operational control, not a one-time gate) |

## Register Maintenance Rules

1. Every risk retains its ID permanently, even after resolution — a resolved risk is marked `DESIGN COMPLETE`/closed with evidence, not removed.
2. No risk may be re-graded to a lower severity or a more favorable status without a named reviewer and a dated evidence reference (a test report, an audit finding, a signed-off gate).
3. New risks identified during implementation are appended with the next sequential ID (`R19`, `R20`, ...) — IDs are never reused or renumbered.
4. This register is reviewed in full at every Security Acceptance Gate review (`SAKAR_SECURITY_REQUIREMENTS.md` Section 17) and at minimum quarterly once the platform is in production.
