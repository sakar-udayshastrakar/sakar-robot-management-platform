# web/

**Status: PHASE 4 IMPLEMENTED (uncommitted — pending review).** React 19 + TypeScript (Vite) admin web application for the Sakar Robot Management Platform, built against the real Sakar Cloud Backend REST API.

See `docs/architecture/SAKAR_WEB_APPLICATION_ARCHITECTURE.md` for the full architecture and `docs/requirements/SAKAR_PHASE_4_WEB_IMPLEMENTATION_REPORT.md` for what is real vs. simulated, live verification evidence, and remaining gaps.

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

Login, Organizations, Sites, Robots (list/detail/register/activate/deactivate/status/MQTT credentials), and Audit Logs call the real backend. Telemetry, Events, Errors, Alerts, Tasks, Application Logs, Users, and Roles have no backend REST endpoint yet (the data is persisted, not exposed) and render clearly-labeled simulated data instead — see the sidebar's `SIM`/`SOON` tags and the implementation report's gap list for exactly which endpoint is missing per feature. Remote lock/unlock is a permanently disabled placeholder — it is not implemented anywhere in the backend and has not been physically validated.

**Core architecture rule this application follows:** it talks to Sakar Cloud Backend only. It never connects to a robot directly.
