# Documentation Index

**Status: DOCUMENTATION / ARCHITECTURE PREPARATION.** Nothing in this repository implements a feature. Every document below is a **copy** of an already-approved source-of-truth document; the canonical originals remain at `D:\Sakar Robotics Projects\` and were **not modified or moved** by this workspace-organization pass.

## docs/requirements/

| File | Contents |
|---|---|
| `SAKAR_ROBOT_PLATFORM_MASTER_REQUIREMENTS.md` / `.pdf` | The master requirements & technical specification (40 parts) — the primary source of truth for the whole platform, including the security-first architecture and the latest live API validation evidence (Part 40). |
| `SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md` | Earlier detailed requirements companion (mobile/web/agent functional detail, offline-behavior matrix, screen inventories). |
| `SAKAR_ROBOT_PLATFORM_ROADMAP.md` | Development roadmap companion (phase-by-phase detail; the master document's Part 33 is the authoritative summary). |
| `SAKAR_C40_AGENT_VALIDATION_REPORT.md` | Phase 0 validation report — assesses the existing `SakarC40Agent` codebase (current Sakar Robot Agent implementation) against the approved requirements. |
| `SAKAR_KEENON_C40S_LIVE_API_TESTING_REFERENCE.pdf` | **New.** The supplied Postman/cURL live API testing reference against the real Keenon Open Platform (store `C00715655`, robot `94:BA:06:CA:99:F3`) — the source evidence for Master Requirements Part 40. |
| `SAKAR_LIVE_API_VALIDATION_MATRIX.md` | **New.** Endpoint-by-endpoint breakdown (purpose/input/output/live test result/Sakar mapping/dependency/status/notes) of every API exercised in the live testing reference above. |

## docs/security/

| File | Contents |
|---|---|
| `SAKAR_SECURITY_REQUIREMENTS.md` | Standalone, self-contained security requirements specification (authentication/RBAC, robot command security, Android/agent security, network/MQTT/WebSocket security, data protection, secrets management, backup/DR, monitoring, security testing, security acceptance gates). |
| `SAKAR_SECURITY_RISK_REGISTER.md` | The formal risk register (R01–R18) with severity, status, mitigation, and validation columns. |
| `SAKAR_C40_AGENT_SECURITY_AUDIT.md` | Phase 0 security audit of the existing `SakarC40Agent` codebase against `SAKAR_SECURITY_REQUIREMENTS.md`. |

## docs/architecture/

| File | Contents |
|---|---|
| `SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md` | Backend/system architecture companion (logical services, communication-protocol comparison, security-hardened deployment view). |
| `SAKAR_ROBOT_PLATFORM_DATABASE.md` | Full field-level database schema companion (every table, type, key, index, retention note). |
| `SAKAR_C40_AGENT_ARCHITECTURE.md` | Phase 0 architecture snapshot of the existing `SakarC40Agent` codebase (current state only). |
| `SAKAR_ROBOT_PLATFORM_NAMING_AND_MODEL_STRATEGY.md` | Product/platform/agent naming strategy and the multi-robot-model roadmap (Sakar CleanBot 5000 Plus, Sakar Robot Agent, Robot Adapter concept). |

## docs/api/

| File | Contents |
|---|---|
| `SAKAR_ROBOT_PLATFORM_API_SPEC.md` | REST API requirements companion (endpoint-by-endpoint request/response/error specification) and the Robot Agent API (Sakar Cloud ↔ Sakar Robot Agent message contract). |

## Specifications not yet extracted into standalone files

The following items are named in this project's documentation-first plan but **currently exist only as specific Parts inside `SAKAR_ROBOT_PLATFORM_MASTER_REQUIREMENTS.md`**, not as separate documents. Per the instruction to use the approved master requirements as the source of truth and not invent additional requirements, no new standalone file was fabricated for these during this workspace-organization pass — extracting them is deliberately left as explicit, separately-scoped future documentation work:

| Named specification | Currently found at |
|---|---|
| Robot Communication Specification | Master Requirements, Part 15 ("Robot Communication"), cross-referenced by Part 23 (MQTT) and Part 24 (WebSocket) |
| Robot Security Specification | Master Requirements, Part 21 ("Android / Sakar Robot Agent Security") and Part 20 ("Robot Command Security") |
| SRELS Specification | Master Requirements, Part 12 ("Sakar Robot Event & Log System") |
| Physical C40 Validation Plan | Master Requirements, Part 38 ("Physical C40 Validation Plan") |

## Live API Testing Status (v2.1)

**CURRENT:** Keenon C40 S live API integration tested against the Keenon Open Platform (`https://cloud.robotkeenon.com`) — see `SAKAR_ROBOT_PLATFORM_MASTER_REQUIREMENTS.md` Part 40 and `SAKAR_LIVE_API_VALIDATION_MATRIX.md`. This path is `KEENON-CLOUD DEPENDENT`.

**TARGET:** a Sakar-owned, multi-robot platform (`SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md` §7/§8 — Robot Adapter Layer) that is not vendor-specific and does not require Keenon Cloud for its core monitoring/control loop.

**NOT YET PROVEN:** a fully local Sakar Robot Agent → Sakar Cloud → Robot path operating without Keenon Cloud in the loop.

## Change control

Any edit to the *content* of a requirement belongs in the canonical original at the project-collection root, not in these copies — these copies exist so the new project workspace is self-contained for engineers working under `sakar robotics web/`. If a copy and its original ever diverge, the most recently approved original at `D:\Sakar Robotics Projects\` governs until an explicit re-sync is performed.
