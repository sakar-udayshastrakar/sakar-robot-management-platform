# Sakar Robot Management Platform — Development Roadmap

Companion to the other four platform documents. **Planning document only — no phase has been started, no code has been written.**

Each phase lists: objective, dependencies, deliverables, acceptance criteria, and risks. Phases are ordered so that no phase assumes an unverified capability from a later phase — in particular, **Phase 0 exists precisely because every other phase's scope depends on resolving the `REQUIRES PHYSICAL C40 TEST` items identified across the source studies.**

---

## Phase 0 — Physical C40 SDK Validation

**Objective:** resolve every open, hardware-dependent question identified in `PEANUT_SDK_C40_TECHNICAL_STUDY.md` before any dependent feature is designed further or built. This is the single highest-leverage phase in the roadmap — every later phase's scope narrows or changes based on what this phase finds.

**Dependencies:** physical access to a Keenon C40/C40 S, in a controlled area, with the robot's physical emergency-stop within immediate reach at all times; a second operator present for any test involving potential motion (per the safety plan already defined in `PEANUT_SDK_C40_TECHNICAL_STUDY.md` §11).

**Deliverables:**
- Confirmed `LinkType` (COM/COM_COAP/COAP/HTTP) the real C40 actually requires.
- Confirmed real-robot values for: battery, motor status, health, emergency state, position, navigation status (execute test plan items 1-10 from the SDK study).
- **The motor lock/unlock seven-condition validation** (test plan items 11-15): physical stop-of-movement, navigation-under-lock behavior, restart/reboot/network-disconnect persistence, and stock-Keenon-app interaction.
- An updated version of `PEANUT_SDK_C40_TECHNICAL_STUDY.md`/`PEANUT_SDK_C40_API_MATRIX.md` with every `REQUIRES PHYSICAL C40 TEST` row resolved to `CONFIRMED` or `CONFIRMED FALSE` (i.e., disproven), never left ambiguous after this phase.

**Acceptance criteria:**
> Given the test plan in `PEANUT_SDK_C40_TECHNICAL_STUDY.md` §11, when each test is executed under its stated safety precautions, then its result (pass/fail/inconclusive) is recorded, and any test involving the motor lock explicitly states whether physical immobility was observed — no test is marked complete based on an SDK callback alone.

**Risks:** motor lock may not physically prevent movement at all (would require an alternate safety mechanism, e.g., a hardware interlock, before "remote lock" can be marketed as a feature); the stock Keenon app may be difficult to fully remove/disable, forcing an earlier-than-planned investment in OS-level device management (pull forward part of Phase 8).

---

## Phase 1 — Sakar Robot Agent Telemetry

**Objective:** extend the existing `SakarC40Agent` (not replace it) to forward already-read telemetry to a Sakar Backend endpoint, using Phase 0's confirmed `LinkType` and data values.

**Dependencies:** Phase 0 complete (or at least the `LinkType` and telemetry-value questions resolved — the lock-specific tests can trail slightly behind if scheduling requires, since Phase 1 does not touch lock/control).

**Deliverables:**
- Agent-side implementation of the Register/Heartbeat/Telemetry/Events messages from `SAKAR_ROBOT_PLATFORM_API_SPEC.md` §2.1-2.4, built on top of the existing `PeanutSdkBridge` read calls — no new SDK capability required, per the API matrix.
- Local buffering for offline operation per `SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md` §4.7.
- Confirmed `native_lib_abi` reporting (32-bit constraint monitoring, §API_SPEC §2.10).

**Acceptance criteria:**
> Given a running C40 with the extended agent installed, when telemetry is read locally via `PeanutSdkBridge`, then it is forwarded to a (stubbed, for this phase) backend endpoint within the configured interval, and continues to buffer correctly through a simulated network interruption.

**Risks:** telemetry frequency requirements are `UNKNOWN` until real link latency is measured in Phase 0; agent battery/resource impact of continuous forwarding on the tablet hardware is untested.

---

## Phase 2 — Sakar Backend (core services)

**Objective:** stand up the modular-monolith backend (`SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md` §2) with Authentication, User, Organization, Robot Registry, and Telemetry services — enough to receive Phase 1's agent traffic and serve read APIs.

**Dependencies:** Phase 1's message contract finalized (even if Phase 1's field agent isn't fully deployed yet, the contract must be stable).

**Deliverables:** API Gateway with auth middleware; Authentication/User/Organization/Robot Registry/Telemetry services per §ARCHITECTURE §2.1; the public REST endpoints in `SAKAR_ROBOT_PLATFORM_API_SPEC.md` §1.1-1.4.

**Acceptance criteria:**
> Given a registered test robot and a valid agent credential, when the agent sends a heartbeat/telemetry message, then the backend persists it and it is retrievable via `GET /robots/{id}/status` and `GET /robots/{id}/telemetry` by an authorized user.

**Risks:** none specific beyond standard backend build risk; this phase is largely `CONFIRMED` buildable with no open SDK questions.

---

## Phase 3 — Database

**Objective:** implement the schema in `SAKAR_ROBOT_PLATFORM_DATABASE.md` as an actual, migrated database, sized and indexed for the initial fleet scale.

**Dependencies:** Phase 2's service boundaries settled (schema ownership per service, even within a single deployable).

**Deliverables:** migrations for all 25 tables; baseline indexes; a documented backup/restore procedure (satisfying `SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md` §10's Backup NFR from day one, not deferred).

**Acceptance criteria:**
> Given the migrated schema, when Phase 2's services write through it under a representative test load, then all documented indexes are used by the corresponding query patterns (verified via query-plan review), and a restore-from-backup drill succeeds.

**Risks:** telemetry/event table volume projections are estimates until Phase 0/1 reveal real reporting frequency — partitioning strategy may need revisiting post-launch.

---

## Phase 4 — Web Dashboard

**Objective:** build the Web Application's core modules — Dashboard, Fleet Management, Robot Management, Live Monitoring, Alerts, Audit Logs — against the Phase 2/3 backend.

**Dependencies:** Phase 2 (APIs) and Phase 3 (persistence) complete for the relevant read paths.

**Deliverables:** the web screens listed in `SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md` §8 items 1-7 and 15-16 (Login through Robot Details, plus Users/Roles/Audit Logs/Settings); Maps/Tasks/Cleaning/Analytics screens can follow once Map/Task/Cleaning services (Phase 6/9) exist — sequence within this phase by data availability, not by a fixed UI-completeness target.

**Acceptance criteria:**
> Given the Phase 2 APIs and a seeded test fleet, when an authorized user logs into the web app, then they see a dashboard reflecting real ingested telemetry, and cannot see data outside their authorized organizations (verified with a negative test per `SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md` §11's multi-tenancy acceptance criterion).

**Risks:** UX for the three-state command status (`sent`/`acked`/`confirmed`) requires careful design so it reads as informative rather than alarming to non-technical users — allocate design review time, not just engineering time.

---

## Phase 5 — Mobile App

**Objective:** build the Mobile Application's monitoring + notification surface against the same backend APIs, reusing the Phase 4 API contracts.

**Dependencies:** Phase 2 APIs stable; push notification infrastructure (APNs/FCM) provisioned.

**Deliverables:** the mobile screens in `SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md` §7 (Login through Profile); push notification wiring for the event types in §2.8.

**Acceptance criteria:**
> Given a robot transitioning to a low-battery or offline state, when the corresponding alert is generated by the Alert Service (Phase 6), then an authorized mobile user with notifications enabled receives a push notification within the configured latency target.

**Risks:** mobile push delivery reliability is platform-dependent (OS-level battery optimization can delay delivery) — set expectations accordingly in the NFR sign-off, do not over-promise real-time guarantees on mobile push specifically.

---

## Phase 6 — Remote Commands (non-lock)

**Objective:** implement the Robot Command Service and the Command message contract (`SAKAR_ROBOT_PLATFORM_API_SPEC.md` §2.5-2.7) for **non-lock** commands first: navigate, pause, resume, stop, return-to-charge, cleaning actions — deliberately sequenced before lock/unlock so the command-security machinery (nonce, expiry, signature, idempotency) is proven on lower-stakes commands first.

**Dependencies:** Phase 0's relevant physical confirmations for each command type being enabled (do not enable a command in production the physical effect of which Phase 0 didn't test, even if it's "just navigation" — apply the same rigor the task demanded for lock to every physical-action command).

**Deliverables:** Robot Command Service; realtime channel selection and implementation (`SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md` §4 — MQTT recommended, HTTPS-polling acceptable as an interim fallback); Task Service integration for task-originated commands.

**Acceptance criteria:**
> Given an authorized operator issuing a `pause` command, when the agent receives, acks, and executes it, then the backend records `sent → acked → completed` states distinctly, and a duplicate delivery of the same command ID is a no-op.

**Risks:** choice of realtime transport (MQTT vs. WebSocket vs. hybrid) has infrastructure/ops cost implications — confirm team's operational familiarity with the chosen broker technology before committing.

---

## Phase 7 — Remote Lock/Unlock

**Objective:** implement the Lock Management module end-to-end (`SAKAR_ROBOT_PLATFORM_API_SPEC.md` §1.5, §DATABASE §16) — but **do not mark it production-ready** until this phase's own gate (below) is satisfied.

**Dependencies:** **Phase 0's motor-lock physical validation (test plan items 11-15) must show a positive result** (robot actually stops/prevents movement, lock persists appropriately, and either the stock app cannot bypass it or an OS-level enforcement mechanism is in place) before this phase's output can be flagged production-ready. Phase 6's command infrastructure (nonce/expiry/signature/idempotency) reused directly.

**Deliverables:** `/robots/{id}/lock` and `/unlock` endpoints with the mandatory `physically_confirmed` response field (`SAKAR_ROBOT_PLATFORM_API_SPEC.md` §1.5.1); the dedicated Lock Management web module; lock history UI on mobile and web (`SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md` §2.7/§3.2).

**Gate before production sign-off (explicit, non-negotiable per the source requirements):**
> The final physical-lock acceptance criterion (`SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md` §11 "Lock") cannot be marked PASS, and this feature cannot be marketed to customers as a safety guarantee, until Phase 0's seven-condition physical test has been completed and all seven conditions are satisfied or an equivalent compensating control (e.g., OS-level enforcement per Phase 8) is in place for whichever conditions the SDK alone cannot satisfy.

**Risks:** if Phase 0 finds the motor lock does *not* physically prevent movement, this phase's scope changes fundamentally (would need a hardware-level safety mechanism instead, which is outside this platform's software scope and would need to be raised as a product/hardware decision, not solved in software).

---

## Phase 8 — Security Hardening

**Objective:** close the gaps identified in `SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md` §3.6 — OS-level device management (kiosk/device-owner mode), command signing at production-grade (beyond the MVP HMAC baseline if warranted), MFA enforcement rollout, and a security review of the full command/lock path.

**Dependencies:** Phase 7's finding on whether OS-level enforcement is *required* (vs. merely recommended) to satisfy the lock gate — if Phase 0 showed the stock app can trivially override the lock, this phase's device-owner-mode work becomes a hard prerequisite for Phase 7's production sign-off, not a parallel-track nice-to-have, and phase ordering should be revisited accordingly at that time.

**Deliverables:** device-owner/kiosk-mode rollout plan and pilot; command-signing implementation review; penetration-test or equivalent security review of the authentication/authorization/command-security stack.

**Acceptance criteria:**
> Given a tablet under device-owner/kiosk-mode enforcement, when an attempt is made to launch any app other than the authorized agent, then the attempt is blocked by the OS, independent of anything the agent application itself does.

**Risks:** device-owner mode typically requires enrollment at factory-reset/provisioning time — retrofitting it onto already-deployed tablets may require a physical re-provisioning pass, which has fleet-wide logistics cost.

---

## Phase 9 — Fleet Management

**Objective:** complete the multi-robot, multi-site, multi-organization management surface — robot groups, cross-robot analytics, map management, cleaning schedule management — building on the now-stable core.

**Dependencies:** Phases 2-8 provide the data and command substrate; this phase is primarily UI/aggregation work plus the Map/Cleaning-schedule write APIs (`SAKAR_ROBOT_PLATFORM_API_SPEC.md` §1.7/§1.8).

**Deliverables:** robot groups and bulk operations; the Analytics Service (`SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md` §2.1) with utilization/downtime/error-frequency reporting; Map management write path; Cleaning schedule create/edit.

**Acceptance criteria:**
> Given a fleet of multiple robots across multiple sites, when an authorized user views fleet-wide analytics, then figures reflect aggregated data across exactly the robots that user is authorized to see — no more, no less.

**Risks:** analytics correctness depends on telemetry completeness from Phase 1 — gaps in early data collection (before this phase) will show as reporting gaps, not bugs; communicate this to stakeholders rather than treating early data gaps as defects to retroactively fix.

---

## Phase 10 — Production Deployment

**Objective:** harden, load-test, and roll out the platform to its first real production fleet.

**Dependencies:** all prior phases; explicit sign-off that every P0 requirement from `SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md` §9 is met, and that Phase 7's lock-feature gate is either fully satisfied or the feature is shipped explicitly flagged as best-effort (never silently shipped as if fully validated).

**Deliverables:** production infrastructure (per the Availability/DR NFRs in `SAKAR_ROBOT_PLATFORM_REQUIREMENTS.md` §10); monitoring/alerting on the platform's own health (distinct from robot alerting); a documented incident-response process for both platform outages and robot-safety incidents.

**Acceptance criteria:**
> Given the production environment, when the defined RPO/RTO targets are tested via a simulated failover/restore drill, then actual recovery time and data loss fall within the targets set during architecture sign-off (`SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md` §NFR).

**Risks:** first real customer deployment will surface integration issues (network environment variance, tablet hardware variance) that a controlled test environment cannot fully anticipate — plan a staged/pilot rollout rather than a single full-fleet cutover.

---

## Roadmap-Level Risk Register (cross-phase)

| Risk | Phases most affected | Notes |
|---|---|---|
| C40 SDK compatibility (`LinkType` unknown) | 0, 1 | Blocks agent telemetry design until resolved |
| Motor lock physical behavior unconfirmed | 0, 7 | Could invalidate the entire "remote lock" feature as currently scoped if disproven |
| Stock Keenon app interference | 0, 7, 8 | May force Phase 8 forward as a Phase 7 hard dependency |
| OTA/cloud communication unverified | 0 (packet capture), ongoing | Standing caveat on all "data ownership" product claims, never fully closed by this roadmap alone — requires ongoing vigilance, not a one-time task |
| 32-bit native library constraint | 1, 8, 10 | Constrains tablet hardware/OS choices for the life of the fleet using this SDK version |
| SDK vendor dependency | all | Mitigated architecturally by the single-chokepoint `PeanutSdkBridge` pattern (already in place) |
| Robot firmware changes (OTA) | 0, 7 (re-validation) | Any firmware update to the motion/STM32 board should trigger a re-run of the relevant Phase 0 tests before trusting prior results |
| Network failure / duplicate commands | 6, 7 | Addressed by the idempotency/ack design in `SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md` §4, must be tested under real network conditions, not just unit-tested |
| Unauthorized access / lock bypass | 7, 8 | The two phases most directly answering this |
| Data loss | 2, 3, 10 | Addressed by backend durability + backup/restore drills |

---

## How to Read This Roadmap
Phases are presented in a recommended sequence, but **Phase 0 is the only truly hard prerequisite for everything downstream that touches physical robot behavior.** Phases 2-5 (backend/database/web/mobile core) can, in practice, proceed in parallel with a delayed or partially-complete Phase 0, since they depend only on the *shape* of telemetry data (largely `CONFIRMED` already) rather than its exact real-world values. Phases 6 onward (commands, lock, security hardening) should not proceed to a *production* sign-off without Phase 0's relevant physical results in hand, even if their software is built and tested against simulated/mocked agent responses in the meantime.
