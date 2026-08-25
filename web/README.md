# web/

**Status: NOT YET IMPLEMENTED.** Reserved for the Sakar web application (React + TypeScript, per `docs/architecture/SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md`) — the primary administrative surface (fleet/organization/site/robot management, live monitoring, maps, tasks, cleaning, the dedicated Robot Lock Management module, alerts, analytics, users/roles, audit logs).

**Core architecture rule this application must follow:** the web application talks to Sakar Backend only. It never connects to a robot directly (master requirements document, Part 16 — Security Architecture).

Specification sources:
- `docs/requirements/SAKAR_ROBOT_PLATFORM_MASTER_REQUIREMENTS.md` — Part 7.A (Applications — Web), Part 31 (UI Requirements)
- `docs/api/SAKAR_ROBOT_PLATFORM_API_SPEC.md` — the only interface this application is authorized to call

**Current phase: DOCUMENTATION / ARCHITECTURE PREPARATION.** No code exists here yet.
