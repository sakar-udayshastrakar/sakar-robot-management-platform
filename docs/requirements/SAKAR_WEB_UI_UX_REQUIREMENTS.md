# Sakar Web UI — UX Requirements & Redesign Report

Companion to `docs/architecture/SAKAR_WEB_UI_DESIGN_SYSTEM.md` (visual/component system) and `docs/requirements/SAKAR_PHASE_4_WEB_IMPLEMENTATION_REPORT.md` (original Phase 4 real-vs-simulated gap list, still accurate). This document covers the **enterprise UI/UX redesign pass**: what changed, what every page now does, and what it deliberately still does not do.

**Scope discipline, restated:** UI/UX only. No backend business logic, database schema, MQTT implementation, `SakarC40Agent`, Peanut SDK, robot communication protocol, authentication logic, RBAC logic, or API changed. Confirmed by `git diff --stat`/`--name-only` at the end of this pass touching only `web/**` and documentation.

---

## 1. What changed vs. Phase 4

- Full visual redesign: new color system (Sakar's own brand orange), typography, spacing, and a formal component library (§ design system doc).
- Sidebar: collapsible, icon+label, grouped, active-state, "SIM"/"SOON" data-source tags on every nav item.
- Top bar: added global robot search, a real backend-connectivity indicator, a notifications preview, and a user menu (session-scoped info + logout).
- Dashboard: restructured into the 6 required KPI cards (Total/Online/Offline/Charging/Error/Low Battery), a "Robot Fleet Overview" table, "Recent Robot Events", "Active Alerts", and a new **System Health** panel (real backend/database check via `GET /actuator/health`; MQTT/WebSocket honestly reported as "not exposed" rather than fabricated).
- Robots list: rebuilt as a dense, filterable (search/status/model/site), sortable, paginated data table with per-row Actions (View/Activate/Deactivate — Deactivate now behind a `ConfirmDialog`).
- Robot Detail: new header (name/ID/model/site/live status), an Actions row (View/Diagnostics/Logs — jump to those tabs), and **11 tabs**: Overview, Telemetry, Events, Errors, Alerts, Tasks, Cleaning, Logs, Timeline, Diagnostics, Audit.
- New: **Diagnostics** tab (real robot capabilities + real raw adapter payload from an on-demand status probe) and **Audit** tab (real audit-log rows, client-filtered to this robot — no per-robot audit endpoint exists).
- Alerts page: rebuilt with the real Critical/High/Medium/Low severity scale (`SeverityBadge`) and the required column set (Severity/Robot/Alert/Source/Created/Status/Actions) plus search/filter.
- Organizations/Sites: added real Sites/Robots counts (resolved client-side from existing endpoints — see design doc), Sites gained an Organization-name column and a derived (not fabricated) Status.
- Users/Roles/Audit: added Organization + Last Login (Users), User count (Roles), and Request ID (Audit) columns.
- Logs: became a dense, monospace-for-technical-content viewer with level/source/date-range/search filters.
- Telemetry: added a time-range selector and a min/max/avg/current summary with a lightweight inline-SVG sparkline (no charting library added).
- New **Fleet Map** page: an honest "Live robot location unavailable" state — no map/positioning API exists on the backend, so nothing is fabricated.
- Toasts (`ToastProvider`) now give real success/error feedback for register/activate/deactivate/create actions, alongside existing inline validation errors.

## 2. Page-by-page data-source table (real vs. simulated)

| Page/Tab | Data source | Notes |
|---|---|---|
| Login, Dashboard KPI "Total Robots", Organizations, Sites, Robots list/detail (registry fields), Activate/Deactivate, MQTT credentials, Audit Logs | **Real** | Existing backend endpoints, unchanged |
| Dashboard "Online/Offline", Robot header/Overview connection badge | **Real, best-effort** | Live `GET /robots/{id}/status` probe; "Unavailable"/"Unknown" on failure, never fabricated as offline |
| Dashboard "Charging/Error/Low Battery" | Simulated | No backend field exists for any of the three |
| Robot Fleet Overview & Robots list "Battery"/"Agent Version" | **Always "Not available"** | No endpoint anywhere returns either field |
| Sites "Robots"/"Online" counts | **Real** | Derived client-side from the real robots list |
| Sites "Alerts" | Simulated (labeled) | No alerts endpoint |
| Organizations "Sites"/"Robots" counts | **Real** | Derived client-side (see design doc §"counts") |
| Telemetry, Events, Errors, Alerts, Tasks, robot-scoped Logs | Simulated | No backend endpoint; every table/tab bannered |
| Robot Detail → Diagnostics (capabilities list) | **Real** | `RobotResponse.capabilities` |
| Robot Detail → Diagnostics (raw adapter payload) | **Real, on-demand** | `RobotStatusSnapshot.raw` via a manual probe button |
| Robot Detail → Audit | **Real, client-filtered** | Real `GET /audit-logs`, filtered to this robot's id from the most recent 100 records |
| Robot Detail → Cleaning | **No data shown at all** | Honest "planned" state referencing the robot's real `CLEANING` capability flag where present; never a fake cleaning history |
| Users, Roles (assignment/user-count) | Simulated (labeled) | No `/users`/`/roles` endpoint; role/permission **names** are the real backend enum values |
| Application Logs (fleet-wide) | Simulated | No `application_logs` read endpoint |
| Fleet Map | **Unavailable state only** | No map/positioning API exists |
| System Health — Backend/Database | **Real** | `GET /actuator/health`, including `components.db` when the backend reports it |
| System Health — MQTT/WebSocket | **Honestly "not exposed"** | No MQTT health indicator exists in the codebase; WebSocket state reflects the feature flag only, never a live probe result |

## 3. Lock/Unlock

Permanently disabled. Both buttons render the exact required text — `Available after physical robot validation` — as their label suffix, `title` tooltip, and `aria-disabled`. No click handler exists; no command is ever sent. This is unchanged in substance from Phase 4, restyled and re-worded to match this pass's exact spec.

## 4. WebSocket

Unchanged in substance: `web/src/websocket/stompClient.ts` implements the confirmed protocol, gated by `VITE_ENABLE_WEBSOCKET` (default `false`). The Dashboard's System Health panel now surfaces this state explicitly rather than implying anything is connected. No page treats a missing WebSocket message as "robot offline" — every live-status surface remains REST-probe-based (on-demand, not polled aggressively).

## 5. Performance

- No page polls continuously. `SystemStatusIndicator` polls `GET /actuator/health` once per 60s (not "aggressive"); every other "live" read (robot status probes) is on-demand (button click) or runs once per data-load, capped at 12 robots (`useRobotStatusProbe`).
- Tables use pagination (robots, audit log) or are naturally small (organizations' children, one site's list) rather than rendering unbounded row counts.
- No new heavy dependency was added for charts/icons — a hand-rolled `Sparkline` and a hand-authored `Icon` set keep the bundle lightweight (production JS bundle: ~378KB / ~115KB gzipped, unminified baseline was ~342KB/106KB before this pass).
- `RobotsListPage`'s robot-array memoization (`useMemo` around `data?.content ?? []`) was specifically added to prevent an infinite-render loop that an earlier draft of this pass introduced — see the Final Report's "Errors and fixes" for detail. Re-render behavior was spot-checked; no other page recomputes an unstable array/object identity as a hook dependency.

## 6. Accessibility checklist (verified)

- [x] Semantic HTML: real `<table>` markup, `<button type="button">` for actions, `<label>`/`aria-label` on every form/filter control.
- [x] Keyboard: Modal/Drawer close on Escape; dropdown menus (notifications, user menu) close on outside click *and* Escape; focus moves into a Modal/Drawer on open.
- [x] Visible focus states: global `:focus-visible` ring, not suppressed anywhere.
- [x] ARIA: `role="dialog"`/`aria-modal` on Modal/Drawer, `aria-live="polite"` on the toast viewport, `aria-haspopup`/`aria-expanded` on the two top-bar dropdown triggers, `aria-disabled` + `title` on the Lock/Unlock buttons.
- [x] Contrast: status/severity badge colors were chosen from a Tailwind-slate-adjacent palette with soft-tint backgrounds and solid-tone text, verified visually in both light and dark themes.
- [x] `prefers-reduced-motion: reduce` collapses all animation/transition durations.

## 7. Responsive checklist (verified live in the Browser pane)

- [x] Desktop (primary): full sidebar, all table columns, top-bar search visible.
- [x] Tablet (≥768px): top-bar search hidden past 1080px; sidebar and tables remain fully usable.
- [x] Mobile (375×812, screenshotted live): KPI cards reflow to 2 columns, sidebar becomes a horizontally-scrollable icon row, wide tables scroll horizontally within their own container without breaking page layout.

## 8. Testing

- **Web:** 21/21 automated tests passing (`npm run test`), `npm run build` succeeds. Two new `RobotsListPage` tests were added (search-filters-the-fleet, filtered-empty-state) because the column set and empty-state copy legitimately changed; no existing assertion was weakened to force a pass.
- **Backend:** re-run unchanged, 71/71 PASS.
- **Agent:** re-run unchanged (`:api` module), 18/18 PASS.
- Live-verified in a real browser against a real (locally-hosted, disposable) instance of the actual backend: login, dashboard KPIs/fleet table/system health, robots list with real filters, robot detail across Overview/Diagnostics/Audit tabs (all real data where the design doc claims "real"), and mobile-viewport rendering.
