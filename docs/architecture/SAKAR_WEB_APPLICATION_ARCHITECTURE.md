# Sakar Web Application — Architecture

Companion to `SAKAR_ROBOT_PLATFORM_ARCHITECTURE.md` and `SAKAR_ROBOT_MQTT_ARCHITECTURE.md`. Describes what actually exists under `web/` as of Phase 4 — not a specification of a future build. See `docs/requirements/SAKAR_PHASE_4_WEB_IMPLEMENTATION_REPORT.md` for the full status/gap breakdown.

## 1. Stack

React 19 + TypeScript (~6.0, `erasableSyntaxOnly` — no runtime enums, unions with `as const` instead), Vite 8, React Router 7. Dependencies deliberately kept minimal: `axios` (API client), `@stomp/stompjs` (WebSocket client), no UI component library, no state-management library (React context + a small `useApi` hook cover every current need), no charting library (none of the current data justifies one yet).

## 2. Directory layout

```
web/src/
  app/            — App.tsx (route table)
  api/            — one file per real backend resource (auth, organizations, sites, robots, audit) + client.ts (axios instance, interceptors)
  types/          — api.ts (ApiResponse/Page envelopes), domain.ts (real + simulated types, each annotated), permissions.ts (verbatim PermissionCode/RoleName)
  features/
    auth/         — AuthContext, session (localStorage), jwt (decode-only), LoginPage, ProtectedRoute
    dashboard/, organizations/, sites/, robots/  — real-API-backed features
    telemetry/, events/, errors/, alerts/, tasks/, logs/, users/, roles/  — simulated-data features (see §4)
    timeline/, placeholder/, misc/, settings/, audit/, shared/
  components/
    layout/       — AppShell, Sidebar (navConfig-driven), TopNav, Breadcrumbs
    ui/           — Card, Badge, StatCard, DataTable, Pagination, States (Loading/Error/Empty), SimulatedDataBanner/UnavailableFeature
  hooks/          — useApi (shared loading/error/data contract), usePermissions
  mocks/          — simulated.ts (every mock data generator, one file, clearly bannered)
  websocket/      — stompClient.ts
  test/           — vitest setup
```

## 3. Data flow — real features

```
React component
  -> src/api/<resource>.ts   (typed function, e.g. listRobots(page, size))
  -> src/api/client.ts       (axios instance: Authorization header, 401-refresh-and-retry, ApiRequestError)
  -> Sakar Cloud Backend REST API (unchanged, Phase 3's own controllers)
  -> PostgreSQL / H2 (unchanged)
```

Every real feature (auth, organizations, sites, robots, audit logs) calls an endpoint that exists today in `RobotController` / `OrganizationController` / `SiteController` / `AuditController` / `AuthController` — verified by reading those controllers directly, not by trusting `SAKAR_ROBOT_PLATFORM_API_SPEC.md` (which is itself marked "nothing below is implemented").

## 4. Data flow — simulated features

Telemetry, Events, Errors, Alerts, Tasks, Application Logs, Users, and Roles have **no backend REST controller** despite their entities/tables existing (`RobotTelemetry`, `RobotAlert`, `RobotTask`, `TaskEvent`, `robot_events`/`robot_errors`, `application_logs`, `User`, `Role`, `Permission`). Every one of these pages:

1. Renders `<SimulatedDataBanner>` (or `<UnavailableFeature>` for the two pages — Cleaning, Analytics — where even a preview would misrepresent a control capability) stating exactly which endpoint is missing.
2. Pulls from `src/mocks/simulated.ts` only — never a real API call.
3. Is tagged in the sidebar (`navConfig.ts`, `dataMode: 'simulated' | 'unavailable'`) so the distinction is visible before a viewer ever opens the page.

No page mixes real and simulated rows in the same table.

## 5. Authentication

`POST /auth/login` → `{accessToken, refreshToken, expiresIn}`. The access token (a real signed JWT, `JwtService.java`) is decoded client-side — **never verified client-side** — purely to drive UI: which nav items to show (`perms` claim), whose email to display, which organization to scope a query to (`org` claim). Every actual authorization decision still happens backend-side via `@PreAuthorize`; a decoded claim is a UI convenience, not a security boundary, and `ProtectedRoute` sending a viewer to `/forbidden` does not mean the backend would have allowed the call either way — it is a UX shortcut for what would fail on the wire regardless.

A 401 on any authenticated request triggers one silent `POST /auth/refresh` + retry (`client.ts`); a second failure clears the session and redirects to `/login`. Tokens live in `localStorage` (see the file-level comment in `session.ts` for why: the backend returns the refresh token in the response body, not an httpOnly cookie, so a browser client has no alternative).

## 6. RBAC in the UI

`usePermissions()` reads the decoded JWT's `perms` array. The permission codes used everywhere in this application are the **real** `PermissionCode` enum (`ROBOT_VIEW`, `ROBOT_CONTROL`, `ROBOT_TASK_CREATE`, `ROBOT_TASK_CANCEL`, `ROBOT_LOCK`, `ROBOT_UNLOCK`, `ROBOT_CONFIGURE`, `ROBOT_DIAGNOSTICS`, `ROBOT_LOG_VIEW`, `AUDIT_VIEW`, `USER_MANAGE`, `ROLE_MANAGE`, `SYSTEM_ADMIN`) — not the aspirational names in `SAKAR_ROBOT_PLATFORM_API_SPEC.md` (`ROBOT_DELETE`, `TASK_CONTROL`, `MAP_MANAGE`, ...), which do not exist in `PermissionCode.java` and are never referenced here.

## 7. WebSocket

`src/websocket/stompClient.ts` implements the confirmed protocol (`WebSocketConfig.java`: endpoint `/ws`, STOMP CONNECT with `Authorization: Bearer`, destinations `/topic/organizations/{orgId}/robots/{robotId}/{status|telemetry}`), gated behind `VITE_ENABLE_WEBSOCKET` (default `false`). Status: **IMPLEMENTED / REQUIRES LIVE VALIDATION** — the Phase 3 live validation confirmed CONNECT+auth works against a real backend but found the SUBSCRIBE phase inconclusive with a minimal hand-built STOMP client (`SAKAR_PHASE_3_LIVE_MQTT_VALIDATION_REPORT.md` §24). No page in this application treats a missing WebSocket message as "robot offline" — every live-status surface is REST-probe-based (`RobotStatusPanel`, on-demand, not polled) with the WebSocket client as an optional future push layer.

## 8. Remote lock/unlock

`LockUnlockPanel` renders two permanently-disabled buttons. There is no code path anywhere in `web/` that sends a lock or unlock command — `ROBOT_LOCK`/`ROBOT_UNLOCK` are checked only to decide whether to show an explanatory note, never to enable an action, because no backend command endpoint exists and no physical robot has validated the behavior.

## 9. What this document deliberately does not claim

- It does not claim the simulated-data pages are "coming soon on a schedule" — no such schedule exists; they are gaps, documented as gaps.
- It does not claim WebSocket live status is working — see §7.
- It does not claim any UI permission check is a security control — see §6.
- It does not claim the robot registry list surfaces battery/online/agent-version fields — `RobotResponse` does not carry them (see `SAKAR_ROBOT_PLATFORM_API_SPEC.md`'s aspirational shape vs. `RobotController.java`'s actual one).
