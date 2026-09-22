# web/

**Status note (documentation sync, 2026-09-22): this file was not kept current through later phases — the root `README.md`'s "Current Project Status" section is the authoritative, current source, not this banner.** React 19 + TypeScript (Vite) admin web application for the Sakar Robot Management Platform, built against the real Sakar Cloud Backend REST API. As of the Fifteenth pass (commit `08b20ac`, pushed to `dev`): TypeScript 0 errors, Vitest 104/104 passing across 17 files, `npm run build` succeeds.

See `docs/architecture/SAKAR_WEB_APPLICATION_ARCHITECTURE.md` for the original architecture and `docs/requirements/SAKAR_PHASE_4_WEB_IMPLEMENTATION_REPORT.md` for the Phase 4 implementation report (historical — see its own status note); both predate most of what "What is real vs. simulated" now describes.

## Quick start

```bash
npm install
cp .env.example .env.local   # point VITE_API_BASE_URL at a running Sakar Cloud Backend
npm run dev
```

## Scripts

| Command | What it does |
|---|---|
| `npm run dev` | Vite dev server |
| `npm run build` | Type-check (`tsc -b`) + production build |
| `npm run test` | Vitest unit/component test suite |
| `npm run lint` | oxlint |

## What is real vs. simulated

**Current (2026-09-22):** Login, Organizations, Sites, Robots (list/detail/register/activate/deactivate/status/MQTT credentials), Audit Logs, Telemetry, Events, Errors, Alerts, Tasks (including CLEANING mode/area/repeat-count configuration and real `sakarAreaId`-keyed area selection), Users, Roles, Permissions, Cleaning history, non-lock robot Commands (with lifecycle history), and the robot map image/areas all call the real backend. Robot Detail's Task Management/Task Record/Statistics/Trial Run Record/Cleaning Daily Report tabs, Fleet Map, and Analytics remain `NotConnectedTab`/empty-state placeholders (no backend endpoint exists for them). Dashboard "Recent Robot Events" and the Robot Detail Timeline tab remain clearly-labeled simulated/random data (`src/mocks/simulated.ts`) — see the `SimulatedDataBanner`/`SIM` markers in those two spots specifically, not across the app generally. Remote lock/unlock is a permanently disabled placeholder — it is not implemented anywhere in the backend and has not been physically validated. `SPOT_CLEAN` has been **removed** from the Tasks panel's task-type dropdown — it was never a documented Keenon Open Platform V2.4.0 feature (Keenon's real temporary/one-off cleaning task, `POST /api/open/custom/clean/robot/strategy/temporary/task`, is a different, already-integrated thing) and had no backend support.

**Core architecture rule this application follows:** it talks to Sakar Cloud Backend only. It never connects to a robot directly.
