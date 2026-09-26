# Documentation Index

A complete index of technical documentation relevant to this project, gathered from `docs/`
(recursive) inside `SakarC40Agent`, plus the project's own root, plus the directly-related
root-level documents at `D:\Sakar Robotics Projects\` that this consolidation's source material
depends on. **No document listed here was deleted, replaced, or edited as part of this
consolidation** — this index only organizes and cross-references what already exists.

Authoritative = the document this project should treat as the current source of truth for its
topic. A non-authoritative document is not wrong, just superseded, narrower in scope, or a design
proposal rather than a statement of current fact — each row says which.

## SDK

| File | Purpose | Status | Authoritative? | Related |
|---|---|---|---|---|
| `D:\Sakar Robotics Projects\PEANUT_SDK_C40_API_MATRIX.md` | Full API/data matrix for `peanut-sdk-release.aar`, decompiled with `javap` | Current, static-analysis findings | **Yes**, for SDK capability questions | `PEANUT_SDK_C40_TECHNICAL_STUDY.md`, `COMPATIBILITY_REPORT.md` |
| `D:\Sakar Robotics Projects\PEANUT_SDK_C40_TECHNICAL_STUDY.md` | Deeper narrative study of the same AAR: network/cloud findings, lock/security analysis, physical test plan | Current, static-analysis findings | **Yes**, for SDK network/security questions | Same as above |
| `SakarC40Agent\COMPATIBILITY_REPORT.md` | C40-specific compatibility classification of every SDK feature (`LIKELY`/`UNKNOWN`/`NOT SUPPORTED`, nothing `CONFIRMED`) | Current | **Yes**, for "is this API C40-tested" questions | Same as above |

## APK Reverse Engineering

| File | Purpose | Status | Authoritative? | Related |
|---|---|---|---|---|
| `docs/architecture/KEENON_APPLICATION_SEPARATION_MAP.md` | Full ownership map of Keenon's 5 separate APKs, decompiled evidence per app | Current (Phase 3) | **Yes** | `SAKAR_APPLICATION_BOUNDARIES.md`, `01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md` §6 |
| `docs/architecture/SAKAR_APPLICATION_BOUNDARIES.md` | Proposed Sakar application boundaries mirroring the above | Current (Phase 3), proposal only | **Yes**, for the proposal itself (not an implementation record) | Same as above |

## Mapping

| File | Purpose | Status | Authoritative? | Related |
|---|---|---|---|---|
| `docs/architecture/KEENON_MAPPING_DEEP_REVERSE_ENGINEERING.md` | End-to-end trace of Keenon's mapping feature: JS bundle, WebSocket bridge, `Mapping.db`, rosbridge | Current (Phase 4) | **Yes** | `SAKAR_MAPPING_TECHNICAL_FEASIBILITY.md`, `01` §10/§11 |
| `docs/architecture/SAKAR_MAPPING_TECHNICAL_FEASIBILITY.md` | Feature-by-feature feasibility matrix + final conclusion for a Sakar equivalent | Current (Phase 4) | **Yes** | Same as above |

## SLAM

| File | Purpose | Status | Authoritative? | Related |
|---|---|---|---|---|
| `docs/architecture/SAKAR_ROS_SLAM_ACCESS_AUDIT.md` | Live network-reachability test of the robot's expected SLAM address/ports from this dev environment | Current (Phase 5) | **Yes**, for "is the robot reachable from here" | `docs/engineering/04_SLAM_INVESTIGATION_STATUS.md` |
| `docs/engineering/04_SLAM_INVESTIGATION_STATUS.md` | Focused Q&A status document consolidating the above with Phase 4 | Current (this consolidation) | **Yes** | All Phase 4/5 docs |

## Hardware

| File | Purpose | Status | Authoritative? | Related |
|---|---|---|---|---|
| `docs/engineering/03_HARDWARE_ARCHITECTURE.md` | RK3288 UIB board markings, separate SLAM computer, separate motor-controller board, all connector/detail gaps marked `UNKNOWN` | Current (this consolidation) | **Yes**, and currently the **only** hardware document in this repository | `01` §2/§3/§4/§26 |

## Architecture

| File | Purpose | Status | Authoritative? | Related |
|---|---|---|---|---|
| `docs/engineering/01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md` | The master consolidation document (this document set's own top-level file) | Current | **Yes** — top-level entry point for this entire document set | All other files in `docs/engineering/` |
| `docs/engineering/05_KEENON_TO_SAKAR_ARCHITECTURE.md` | 3-column Keenon/Sakar-current/Sakar-target comparison, no ranking | Current | **Yes** | `01` §6/§7/§9 |
| `SakarC40Agent\README.md` | Current, authoritative description of `SakarC40Agent`'s own module layout, Capability model, and safety gating | Current, actively maintained by the project itself | **Yes**, for "what does SakarC40Agent actually do today" | `01` §23/§24 |
| `D:\Sakar Robotics Projects\SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md` | Design-only architecture for a much larger, separate "Sakar Robot Management Platform" (multi-tenant cloud backend + fleet management) | Design document only, nothing implemented (per its own header) | Not authoritative for `SakarC40Agent` itself — a related but distinct, broader-scope project | `SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md`, `_API_SPEC.md`, `_DATABASE.md`, `_ROADMAP.md`, `_MASTER_REQUIREMENTS.md` |

## UI

| File | Purpose | Status | Authoritative? | Related |
|---|---|---|---|---|
| `docs/ui/CLAUDE_UI_RULES.md` | Permanent UI governance rules for any session touching UI | Current | **Yes** | All `docs/ui/*` |
| `docs/ui/FEATURE_REGISTRY.md` (+ `.json`) | Machine-readable feature ownership registry | Current, actively maintained | **Yes** | `ROUTE_REGISTRY.md`, `COMPONENT_REGISTRY.md` |
| `docs/ui/ROUTE_REGISTRY.md` | Every NavHost route, LIVE vs. ORPHANED status | Current, actively maintained | **Yes** | Same |
| `docs/ui/COMPONENT_REGISTRY.md` | Shared UI component inventory, known near-duplicates | Current | **Yes** | Same |
| `docs/ui/REFERENCE_UI_MAP.md` | Tree of the real robot's confirmed UI hierarchy vs. reference screenshots | Current, actively maintained | **Yes** | `docs/ui/references/*` |
| `docs/ui/UI_CHANGE_LEDGER.md` | Dated log of every UI change made in this project | Current, actively maintained | **Yes** | — |
| `docs/ui/CURRENT_UI_DUPLICATION_AUDIT.md` | Point-in-time, read-only 16-item duplication audit | Frozen snapshot (explicitly read-only per its own governance rule) | **Yes**, as a historical record — not to be treated as reflecting current state without cross-checking `UI_CHANGE_LEDGER.md` | `FEATURE_REGISTRY.md` |
| `docs/ui/references/README.md` | Rules for the reference-screenshot evidence directory | Current | **Yes** | — |
| `docs/ui/references/REFERENCE_SCREEN_INVENTORY.md` | Frozen inventory of 63 reference screenshots, grouped by screen family | Current, frozen per its own governance | **Yes** | `docs/ui/references/measurements/*` |
| `docs/ui/references/measurements/README.md` (+ 17 per-screen `.json` files) | Pixel-measurement schema + per-screen measurement data | Current (Batch 1 + Batch 2 only; more screens not yet measured) | **Yes**, for the screens it covers | `REFERENCE_SCREEN_INVENTORY.md` |

## Cleaning

Covered within architecture/SDK documents rather than a dedicated file: `01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md` §14 (cleaning system), `PEANUT_SDK_C40_API_MATRIX.md`/`COMPATIBILITY_REPORT.md` (confirms `CleanComponent` absence). No standalone "cleaning" document exists in this repository — this index entry exists to record that gap explicitly rather than leave it undiscoverable.

## Cloud

| File | Purpose | Status | Authoritative? | Related |
|---|---|---|---|---|
| `D:\Sakar Robotics Projects\KEENON_C40_CLOUD_API_AUDIT.md` | Live test of Keenon's actual Cloud/Open Platform API (`https://cloud.robotkeenon.com`) using a real Sakar Robotics account/store | **This is a live/runtime test against a real Keenon cloud endpoint** (dated 2026-08-25), not a static-analysis document — a materially different evidence category from most of this project's other findings, and should be re-read in full before being cited elsewhere, since this consolidation pass did not read it beyond its header | **Yes**, for Keenon Cloud API questions specifically — but flagged here as **not yet cross-referenced into `01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md`**, since doing so properly requires reading it in full rather than summarizing from its header alone (see "Known gaps" below) | `PEANUT_SDK_C40_TECHNICAL_STUDY.md` §7 (which separately confirms the *SDK itself* has no cloud calls — this document is about Keenon's *separate*, official cloud API product, not the SDK) |
| `D:\Sakar Robotics Projects\SAKAR_ROBOT_PLATFORM_API_SPEC.md` | Specification-only API surface for the separate, larger Sakar Robot Management Platform | Design document only | Not authoritative for `SakarC40Agent` | `SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md` |

## Security

| File | Purpose | Status | Authoritative? | Related |
|---|---|---|---|---|
| `D:\Sakar Robotics Projects\SAKAR_SECURITY_REQUIREMENTS.md` | Full security requirements/threat model for the separate Sakar Robot Management Platform | Design document only, nothing implemented (per its own header) | Not authoritative for `SakarC40Agent` itself — scoped to the larger platform project | `SAKAR_SECURITY_RISK_REGISTER.md` |
| `D:\Sakar Robotics Projects\SAKAR_SECURITY_RISK_REGISTER.md` | Risk register (R01–R18) for the same platform | Design document only | Same scope note as above | Same |
| `PEANUT_SDK_C40_TECHNICAL_STUDY.md` §7/§8/§12 | The actual SDK/APK-level security findings relevant to `SakarC40Agent` itself (motor-lock authorization, TLS trust-all) | Current, static-analysis findings | **Yes**, for `SakarC40Agent`/SDK-specific security questions | `01` §22 |

**Conflict/scope note (per this document set's own rule to record disagreements explicitly,
not silently pick one):** `SAKAR_SECURITY_REQUIREMENTS.md`/`SAKAR_SECURITY_RISK_REGISTER.md` are
about a **different, larger, not-yet-built** platform (multi-tenant cloud, MQTT broker, mobile/web
apps) than `SakarC40Agent`'s own current scope. They are not in conflict with this consolidation's
findings — they simply describe a different layer of a longer-term plan — but a reader should not
assume `SakarC40Agent` itself currently implements any control described in those two documents.

## Testing

| File | Purpose | Status | Authoritative? | Related |
|---|---|---|---|---|
| `SakarC40Agent/api/src/test/.../mqtt/*` (10 test files) | Real, existing test suite for the `:api` module's MQTT client/command-dispatch/executor code | Current, part of the actual codebase | **Yes**, for confirming `:api` module behavior | `01` §24 |
| `SakarC40Agent/virtual-agent/src/test/.../*` (4 test files) | Real, existing test suite for the `:virtual-agent` simulation module | Current | **Yes** | `01` §24 |
| `SakarC40Agent/robot/src/test/.../C40RobotControllerTest.java` | Real, existing test for `C40RobotController`'s `OperatingMode` gating | Current | **Yes** | `01` §16/§23 |
| `SakarC40Agent/sdk/src/test/.../PeanutSdkBridgeDestinationParsingTest.java` | Real, existing test for SDK-bridge destination parsing | Current | **Yes** | `01` §8 |

## Deployment

No dedicated deployment document exists for `SakarC40Agent` in this repository at the time of this
consolidation. `SAKAR_ROBOT_PLATFORM_ROADMAP.md` (design-only, larger platform scope) is the only
document touching deployment phasing, and it is explicitly not authoritative for `SakarC40Agent`
itself.

## Implementation

| File | Purpose | Status | Authoritative? | Related |
|---|---|---|---|---|
| `D:\Sakar Robotics Projects\ROBOT_AGENT_COMMAND_LOOP_INVESTIGATION_AND_DESIGN.md` | Investigation + design + implementation record of the backend↔agent MQTT command loop, including a real `RETURN_TO_DOCK` executor | Mixed — contains both investigation and a record of actual implementation work | **Yes**, for the `:api` module's command-loop history | `SakarC40Agent/api/*`, its test suite above |

## Simulation

Covered within architecture documents rather than a dedicated file: `01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md` §16 (Manual Drive simulation architecture) and §24 (`:virtual-agent` module). No standalone "simulation" document exists — recorded here as a gap, not silently left undiscoverable.

---

## Known gaps in this index (recorded explicitly, per this document set's own rules)

1. `KEENON_C40_CLOUD_API_AUDIT.md` (605 lines) was identified and its header/scope read, but **not
   read in full** during this consolidation pass — its detailed findings are therefore **not yet
   reflected** in `01_MASTER_ENGINEERING_KNOWLEDGE_BASE.md` §8/§22. This is a real, live cloud-API
   test (a materially higher-value evidence category than most static-analysis findings in this
   project) and should be fully incorporated in a future pass rather than left only in this index.
2. The 5 `SAKAR_ROBOT_PLATFORM_*` documents and `SAKAR_SECURITY_*` documents describe a
   substantially larger, separate platform project. They were categorized and scope-noted here but
   not deeply cross-referenced into the `SakarC40Agent`-specific master knowledge base, since they
   are, by their own explicit statement, unimplemented design documents for a different project
   layer.
3. No document in this repository currently investigates ROCK 4D hardware directly — its complete
   absence is recorded in `02_EVIDENCE_REGISTER.md` E-032, not silently omitted from this index.
