# Phase 4 — Web Application Implementation Report

**Date:** 2026-08-30 · **Scope:** build the Sakar Web Application (`web/`) against the real, existing Sakar Cloud Backend. No backend redesign, no Peanut SDK/AAR change, no physical CleanBot connection, no Web/Mobile task execution fabrication. **Not committed, not pushed** — working tree left for review.

---

## 1. Implementation gap list (produced before writing any code)

`docs/api/SAKAR_ROBOT_PLATFORM_API_SPEC.md` is explicitly marked "nothing below is implemented" — it is a target contract, not the current one. The actual implemented REST surface, read directly from the controllers:

| Controller | Endpoints that actually exist |
|---|---|
| `AuthController` | `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout` |
| `OrganizationController` | `POST /organizations`, `GET /organizations/{id}`, `GET /organizations/{id}/children` — **no list-all endpoint** |
| `SiteController` | `POST /sites`, `GET /sites?organizationId=` — **no list-all endpoint, organizationId required** |
| `RobotController` | `GET /robots`, `GET /robots/{id}`, `POST /robots`, `POST /robots/{id}/activate`, `POST /robots/{id}/deactivate`, `GET /robots/{id}/status`, `POST`/`DELETE /robots/{id}/mqtt-credentials` |
| `AuditController` | `GET /audit-logs` |

No controller exists for: telemetry, events, errors, alerts, tasks, robot models, users, roles/permissions, or application logs — despite `RobotTelemetry`, `RobotAlert`, `RobotTask`, `TaskEvent`, `robot_events`, `robot_errors`, `application_logs`, `User`, `Role`, `Permission` all existing as persisted JPA entities/tables. This gap list, not the API spec document, drove every "real vs. simulated" decision below.

Additional gaps discovered while building:
- `RobotResponse` (the real list/detail shape) carries no `online`/`battery`/`lastHeartbeat`/`agentVersion` fields — those require a separate, per-robot `GET /robots/{id}/status` call through a live adapter.
- `POST /robots` requires a `robotModelId` UUID with no lookup endpoint to discover one.
- The real `PermissionCode` enum (`iam/PermissionCode.java`) differs from the API spec's aspirational names (no `ROBOT_DELETE`, `TASK_CONTROL`, `MAP_MANAGE`).
- The JWT's actual claim keys are `org` and `perms` (not `org_id`/`permissions` as might be assumed) — confirmed by decoding a real login response, not by reading `JwtService.java` alone (see §9, "Errors and fixes").

## 2. Technology

React 19 + TypeScript, Vite 8, React Router 7, `axios`, `@stomp/stompjs`. No UI/state-management/charting library added — none of the current screens justify one. `web/package.json` did not exist before this phase (`web/` contained only a placeholder `README.md`); the project was scaffolded fresh with `npm create vite@latest -- --template react-ts`.

## 3. Architecture

See `docs/architecture/SAKAR_WEB_APPLICATION_ARCHITECTURE.md` for the full structure, data-flow, auth, RBAC, WebSocket, and lock/unlock write-ups. Summary: `src/api/*` (real backend calls) is strictly separated from `src/mocks/simulated.ts` (the only source of non-real data); every simulated page renders `<SimulatedDataBanner>`/`<UnavailableFeature>` naming the exact missing endpoint.

## 4. What is real vs. simulated

| Feature | Status | Backing |
|---|---|---|
| Login / logout / token refresh / protected routes | **Real** | `AuthController` |
| Dashboard — total/active/registered/deactivated counts | **Real** | `GET /robots` |
| Dashboard — online/offline | **Real, best-effort** | Live `GET /robots/{id}/status` probe (bounded to 12 robots); shown as "unavailable" rather than 0 when the probe fails everywhere |
| Dashboard — charging/low-battery, recent events/errors/alerts | Simulated | No backing endpoint |
| Organizations (view/create) | **Real** | `OrganizationController`; list-all worked around via robots' `organizationId`s + manual ID entry (see gap list) |
| Sites (view/create) | **Real** | `SiteController`, scoped to one organization at a time |
| Robots (list/detail/register/activate/deactivate) | **Real** | `RobotController` |
| Robot live status probe | **Real** (on-demand, not polled) | `GET /robots/{id}/status` |
| MQTT credential provision/revoke | **Real** | `POST`/`DELETE /robots/{id}/mqtt-credentials` |
| Audit Logs | **Real** | `AuditController` |
| Telemetry, Events, Errors, Alerts, Tasks, Application Logs | Simulated | No backend endpoint; each names the missing endpoint |
| Users, Roles | Simulated preview (real enum values, simulated assignment) | No `/users`/`/roles` endpoint |
| Cleaning, Analytics | Placeholder, **no simulated preview** | No control/aggregation endpoint — a fake preview here would misrepresent a capability, per the task's own instruction not to fabricate execution |
| Remote lock/unlock | **Disabled placeholder, sends nothing** | Not implemented anywhere in the backend, not physically validated |
| WebSocket live status | **Implemented, feature-flagged off by default** | Protocol confirmed live in Phase 3 for CONNECT only; SUBSCRIBE inconclusive — see architecture doc §7 |

## 5. Live verification against the real backend (this session)

No Docker/production infra available, so — consistent with the Phase 5 live-validation approach — a disposable H2-in-Postgres-mode database plus a jedis-mock Redis server were used to run the **real, unmodified** backend locally (Flyway disabled for this run only; schema created via `ddl-auto=create-drop`; MQTT disabled — not needed for this phase). RBAC and one bootstrap `SUPER_ADMIN` account were seeded to mirror `V9`'s real role/permission matrix exactly; the `Sakar Robotics` root organization and `C40 S` / `Sakar CleanBot 5000 Plus` model + capabilities were seeded to mirror `V10__seed_reference_data.sql` exactly. All of this lived in the scratchpad, outside the repository, and is not part of any commit.

Using the running dev server (`npm run dev`, real backend on `http://localhost:8080`), the following were exercised in a real browser and confirmed via `get_page_text`/network inspection — not asserted from reading the code alone:

- Login with the seeded `SUPER_ADMIN` account — succeeded, JWT decoded correctly, sidebar shows every nav item (all 13 permissions).
- A site (`Bengaluru HQ`) and two robots (`CleanBot Alpha`, `CleanBot Beta`) were registered through the real `POST /sites` / `POST /robots` endpoints.
- Dashboard: **Total Robots: 2, Registered: 2** — matched the two real robots exactly; "Live connectivity" correctly showed the unavailable banner (both robots' `GET /robots/{id}/status` calls returned no usable adapter result, as expected with a `KEENON_CLOUD` adapter and no live Keenon connection) instead of fabricating an online/offline count.
- Robots list: both real robots rendered with real serial numbers/lifecycle status.
- Robot detail (`CleanBot Alpha`): Overview, Telemetry (simulated, bannered), Events (simulated, bannered), Timeline (simulated, bannered), and Security tabs all rendered correctly.
- Security tab: clicked **Provision / rotate** — this called the real `POST /robots/{id}/mqtt-credentials` endpoint and displayed a real, freshly-issued username/password pair.
- Organizations: correctly reported "no list-all endpoint" and discovered the real organization via the robots list; opened it and displayed the real `path`, `orgType`, `status`.
- Sites page: listed the real `Bengaluru HQ` site under that organization.
- **Audit Logs page: showed 5 real rows** — 4 `LOGIN` entries and 1 `MQTT_CREDENTIAL_PROVISIONED` entry — an exact, live record of the actions just taken in the browser during this same verification pass, read back through the real `GET /audit-logs` endpoint.
- Mobile viewport (375×812): layout reflows correctly — sidebar becomes a horizontally-scrollable row, stat cards stack to two columns, page content remains readable.

This is real end-to-end verification against a real (locally-hosted) instance of the actual backend — not a claim based on reading source code, and not a claim of testing against the production/staging deployment.

## 6. Testing

**Web:** 19/19 automated tests passing (`npm run test`, Vitest + Testing Library) — JWT decode/expiry (including the real `org`/`perms` claim names), `useApi`'s loading/success/error transitions, `DataTable`'s empty/populated rendering, `ProtectedRoute`'s unauthenticated/forbidden/authorized redirects, `LoginPage`'s invalid-credential error message and successful-login navigation, and `RobotsListPage`'s populated/empty/error rendering against a mocked `GET /robots`. `npm run build` succeeds (`tsc -b && vite build`).

**Backend (re-run, must stay green):** 71/71 PASS (`./mvnw clean test`), unchanged from Phase 3.

**Agent (re-run, must stay green):** confirmed 18/18 PASS — see the Final Report chat message for the exact re-run command and result.

## 7. Security

- Protected routes (`ProtectedRoute`) redirect unauthenticated users to `/login` and permission-missing users to `/forbidden` — verified both by live browser testing and by the `ProtectedRoute` unit tests.
- No secret, API key, or production URL is hardcoded — `.env.example` documents the three configuration variables (`VITE_API_BASE_URL`, `VITE_WS_URL`, `VITE_ENABLE_WEBSOCKET`); no `.env`/`.env.local` file is committed (see `.gitignore` — Vite's own template already ignores `.env*.local`, confirmed still in place).
- JWT decoding is display-only (see architecture doc §6) — every authorization decision remains backend-enforced; RBAC-based nav visibility is a UX convenience, not a security boundary, and is documented as such in code comments.
- Remote lock/unlock sends nothing — verified both by code inspection (`LockUnlockPanel` has no fetch/API import at all) and by clicking the (disabled) buttons live.
- No credential value is logged to the console anywhere in this codebase; the one place a secret is displayed (the MQTT credential provisioning result) is rendered once, in-memory only, with an explicit "copy now" warning, and is never written to `localStorage`/`sessionStorage`.

## 8. Files created

Everything under `web/` except the original placeholder `README.md` (which was moved aside during scaffolding and is restored below with updated, factual content) is new in this phase — see the Final Report chat message for the `git status`/`git diff --stat` output confirming the exact file list.

## 9. Errors and fixes encountered this session

- **Assumed JWT claim names from the API spec turned out wrong twice over**: first assumed `permissions`/`org_id` (plausible English names), then found the actual `JwtService.java` constants are `perms`/`org` — confirmed by decoding a real token from a real `POST /auth/login` call, not by re-reading the Java source a second time. Fixed in `jwt.ts` before any test was written against it.
- **CSS selector list mixed with a nested `@media` block** (`selector-a,\n@media {...}`) is invalid CSS — `lightningcss` failed the production build with "Invalid empty selector"; split into two separate rules.
- **`create-vite` refused to scaffold into a non-empty directory** (the existing placeholder `README.md`) — moved the file aside, scaffolded, will restore its content (updated) afterward.
- **H2 single-file-lock contention** running a seed script against an already-running backend — same root cause documented in the Phase 3 live-validation report; fixed identically, with `AUTO_SERVER=TRUE` added to the JDBC URL so a second local process can connect concurrently.
- **`preview_start` failed to spawn `npm --prefix "<path with spaces>"`** — Windows argument-splitting broke on the space-containing path passed as a separate array element; fixed by wrapping the dev-server launch in a single `.cmd` file (`devweb.cmd`) referenced as one `runtimeExecutable` string instead.

## 10. Remaining gaps (unchanged backend, tracked here rather than worked around further)

1. No list-all endpoint for organizations or sites — a real gap for any multi-tenant admin UI, not just this one.
2. No REST read path for telemetry/events/errors/alerts/tasks/logs/users/roles, despite the data being persisted.
3. No robot-model lookup endpoint — robot registration requires knowing a model UUID out-of-band.
4. WebSocket SUBSCRIBE behavior remains unvalidated live (Phase 3 finding, unchanged).
5. `GET /robots/{id}/status` depends on a live vendor adapter (Keenon Cloud for the current model) — it is not a general-purpose "is this robot online" check independent of that dependency.

None of these are addressed by adding new backend code in this phase — consistent with "do not redesign the backend unnecessarily."
