# Sakar Robot Management Platform

**This is the single project workspace for the Sakar Robotics Robot Management Platform. All new source code, infrastructure, tests, and documentation for this platform must live under this root (`D:\Sakar Robotics Projects\sakar robotics web`).**

## CURRENT PHASE

**DOCUMENTATION / ARCHITECTURE PREPARATION.**

No backend, web, mobile, robot-control, MQTT, REST API, WebSocket, or remote-lock feature has been implemented. No software has been built or deployed from this workspace. This repository currently contains: an organized directory structure, copies of the approved requirements/security/architecture/API documentation, and a copy of the existing `SakarC40Agent` Android project (source only, unmodified). Nothing here should be read as implying production readiness of any feature.

## Project Purpose

Sakar Robotics is building a platform so that Sakar — not the robot vendor (Keenon) — is the primary system of record for robot data, telemetry, history, authentication, authorization, commands, lock/unlock, users, configuration, analytics, logs, alerts, and fleet management, starting with the Keenon C40 / C40 S. The full rationale, architecture, and security model are specified in `docs/requirements/SAKAR_ROBOT_PLATFORM_MASTER_REQUIREMENTS.md`.

## Project Structure

```
sakar robotics web/
├── docs/                     Approved documentation (copies; see docs/README.md for the full index)
│   ├── requirements/         Master requirements, companion requirements/roadmap docs
│   ├── security/             Standalone security requirements + risk register
│   ├── architecture/         System architecture + database specification
│   └── api/                  REST API specification
├── backend/                  Sakar Cloud backend (Java + Spring Boot) — not yet implemented
├── web/                      Sakar web application (React + TypeScript) — not yet implemented
├── mobile/                   Sakar mobile application (Flutter) — not yet implemented
├── robot/
│   └── SakarC40Agent/        Robot-resident Android agent — existing project, copied here unmodified
├── database/                 PostgreSQL schema/migrations — not yet implemented
├── infrastructure/           Docker/Nginx/deployment config — not yet implemented
├── security/                 Security tooling/policy-as-code (distinct from docs/security/) — not yet implemented
├── tests/                    Automated test suites — not yet implemented
└── README.md                 This file
```

## Applications

| Application | Location | Status |
|---|---|---|
| Sakar Cloud (backend) | `backend/` | Not yet implemented |
| Web application | `web/` | Not yet implemented |
| Mobile application | `mobile/` | Not yet implemented |
| Robot Android tablet agent (`SakarC40Agent`) | `robot/SakarC40Agent/` | Existing project, copied in as-is; not yet extended with the new telemetry-forwarding/command-reception capability described in the master requirements |

**Core architecture rule:** the web and mobile applications communicate with Sakar Cloud only. Neither ever connects to a robot directly — enforced by network segmentation and application-layer authorization once the backend is built (master requirements, Part 16).

## Documentation

All approved specifications live under `docs/` — see `docs/README.md` for the full index and for an explicit note on which named specifications (Robot Communication, Robot Security, SRELS, Physical C40 Validation Plan) currently exist only as Parts within the master requirements document rather than as separate files. The canonical originals of every document in `docs/` remain at `D:\Sakar Robotics Projects\` and were not modified by this workspace-organization pass.

## Security

Security is specified, not yet implemented. `docs/security/SAKAR_SECURITY_REQUIREMENTS.md` defines every control (authentication, RBAC, robot command security, Android/agent security, network/MQTT/WebSocket security, data protection, secrets management, backup/DR, monitoring, security testing) and `docs/security/SAKAR_SECURITY_RISK_REGISTER.md` tracks 18 formally registered risks. The platform's highest-risk feature — remote motor lock/unlock — is explicitly documented as **not production-ready** until ten physical validation conditions are met on real hardware (master requirements, Part 11/38); nothing in this workspace changes that status.

## External Peanut SDK

The Keenon **Peanut SDK** (`peanut-sdk-v1.3.0`, including the vendor's sample app and the decompiled technical study material) is an **external, read-only vendor dependency** that lives at `D:\Sakar Robotics Projects\peanut-sdk-v1.3.0`, outside this project workspace. It has not been moved, copied, or modified. `robot/SakarC40Agent/sdk/libs/peanut-sdk-release.aar` is the pre-existing vendored binary that `SakarC40Agent` already depends on (carried over as part of that project's copy, unmodified) — this is a distinct file from the standalone reference directory and is excluded from Git tracking (see `.gitignore`).

## Development Status

| Item | Status |
|---|---|
| Backend implementation | Not started |
| Web implementation | Not started |
| Mobile implementation | Not started |
| Robot agent extension (telemetry forwarding, command reception) | Not started |
| MQTT / WebSocket / REST implementation | Not started |
| Remote lock/unlock | Not started; physically unverified regardless (master requirements Part 11/38) |
| Database schema deployment | Not started |
| Physical C40 testing | Not performed |
| Physical Keenon network traffic capture | Not performed |

## Legacy documents at the project-collection level

`D:\Sakar Robotics Projects\` (one level above this workspace) also contains `Sakar_Robotics_SRS.docx`/`.pdf`, a pre-existing document that predates this workspace and this reorganization. It was left in place, untouched, and is not part of this workspace's `docs/` index — if it needs to be reconciled with or superseded by the current master requirements, that is a separate, explicit decision for a future task.

---

**Do not begin backend, web, mobile, robot-control, MQTT, REST API, WebSocket, or remote-lock implementation from this README.** The next authorized step, per the approved documentation, is Phase 0 (physical C40 validation) as described in `docs/requirements/SAKAR_ROBOT_PLATFORM_MASTER_REQUIREMENTS.md` Part 33 — and that itself requires a separate, explicit go-ahead.
