# Sakar Robotics Web Platform — Keenon Cloud UI/UX Reference Audit

**Status: AUDIT ONLY. No code was written or modified during this task. Do not begin implementation until the user explicitly says "IMPLEMENT IT."**

**Method:** Live, read-only inspection of the Keenon Cloud production web app (`https://www.robotkeenon.com`) via an authenticated browser session (existing session cookies, no credential entry), cross-referenced with a full source-code inventory of the Sakar frontend (`web/src`) and backend (`backend/src`), plus Sakar's own prior `KEENON_C40_CLOUD_API_AUDIT.md`/`.json` (live-tested Keenon Open Platform API results) and the "Preparation Training On Keenon Cloud" reference deck.

**Account scope note:** The Keenon account used is Sakar Robotics' own distributor account for its 5 owned physical devices (3× C40 S, 1× S100, 1× W3) plus 61 unbound C55/C40 S units in un-allocated stock. No section was inaccessible — every one of the 11 top-level sidebar modules was reachable and inspected. Nothing is marked "NOT ACCESSIBLE."

---

## A. Keenon UI Inventory (full, live-verified)

Keenon Cloud's IA is **11 top-level sidebar modules**, most implemented as independent micro-frontend "sub-apps" (own URL prefix, own nested sidebar):

| # | Module | Sub-items | Real data seen |
|---|---|---|---|
| 1 | **Dashboard** | (single page) | Announcement carousel, "Recently Used" quick-links, 7-day overview, Task Data Details (Task Distribution + Task/Mileage tabs) |
| 2 | **Account Permission Platform** | Role list, Internal User List, External User List | 2 roles (Engineer, Testing Engineer), 3 internal users, 0 external users |
| 3 | **Robot management** | Robot Management (→ Robot list, Inventory control, Sub-Agent Inventory, Running record), Running Statistics (→ Running record / Cleaning Task Record) | 4 bound robots, **61 unbound stock robots** (models C55, C40 S) |
| 4 | **New Resource Configuration** | New Version Resource Configuration (Scene list/Send record/Historical record/Resources), Marketing materials, Language configuration management | Empty — this module targets meal-delivery/guidance robot Apps 1.7+, not the cleaning App; N/A to Sakar's C40 S fleet |
| 5 | **Store Management** | Store list | 1 store: "Sakar robotics office", ID `C00715655`, area India |
| 6 | **OTA Management** | System Version Management, Update record | Empty (no packages pushed to this account) |
| 7 | **Operation And Maintenance Platform** | Mission Log (Cleaning/Catering/Hotel tabs), Customer Repair Requests, Device Management, Remote Operations and Maintenance (→ Remote Deployment, Remote log) | Device Management lists **all 5 physical Sakar devices** across all 3 models with per-device "remote desktop / Monitor / Playback" actions; Remote Deployment shows 3 completed real deployment jobs |
| 8 | **IoT Platform** | Elevator Module (Elevator management/configuration/Cloud ladder control), Phone Module (Device management) | Empty — no elevator/phone peripherals deployed |
| 9 | **Open Platform** | Customer Registration, Application management, File download | Self-service developer-portal signup for Keenon's own Open API (the same flow Sakar itself used) — no live OAuth app/secret is exposed in this UI |
| 10 | **Operational Dashboard** | Home page, Operation Ranking, Store Real-Time Data Statistics, Use Retention Analytics, Hotel Task Record | Shared KPI+ranking template: "Number of tasks/calls/mileage today", store & robot leaderboards, live task-mode donut, time-series tabs |
| 11 | **Learning Center Platform** | Learning Center | Vendor marketing/training content (solution showcase + course library) — not a workflow screen, no Sakar equivalent needed |

### Robot Detail — high-priority deep dive (8 tabs, real "Demo Piece" C40 S robot)

| Tab | Content |
|---|---|
| Robot information | 3 sectioned cards: Real-time status (with clickable location pin → opens full interactive map), Software info, Business info |
| Map management | Floor table → "Details" → rich multi-layer floor-plan viewer (Base Map / No-go Line / Target Point / Area / Restricted area / Teaching Path / Speed Limit / Gate / Elevator layer toggles, floor selector) |
| Task management | Recurring **cleaning schedules** (2 real: "demo", "Task7") — Task Name, Start/End time, Timed Task Type, Frequency, Cleaning Times, Cleaning mode, floor, Sweeping areas, Estimated time, Cleaning area, enable/disable Switch, Details |
| Task record | Cleaning **run history** — 3 KPI cards (Accumulated time 2080 min, Cumulative area 13985 m², Cumulative times 504) + filterable table (Task Type, Cleaning mode, Task Name, Area, Covered Area, Work Hours, Duration, Efficiency, Coverage %, Power/Water Consumption) |
| Statistics | Bar charts: Robot cleaning area, Task record(times), + Water & Cleaning Agent Statistics panel; Recent 7/30 days + custom range toggle |
| Trial Run Record | Filterable table (Cleaning Floor, Reasons for Failure) — empty for this robot |
| Configuration management | Per-robot push-notification account list (Account type, Language, Receive push) — empty |
| Cleaning Daily Report | Branded, exportable-to-PDF daily report (robot/store/SN header, Total/Scheduling/Temporary/Hand-push task counts) |

**Known UI defect (Keenon's own bug, not ours):** clicking "Details" on a Map-management floor row or a Task-management schedule row renders a blank modal on first attempt; the map version was reachable via the Robot Information tab's location pin instead. Noted as an observation, not investigated further (read-only).

---

## B. Sakar UI Inventory (current, source-verified)

Single micro-frontend, 5 nav sections, 16 routed screens (`web/src/components/layout/navConfig.ts`):

| Section | Screens |
|---|---|
| Overview | Dashboard |
| Fleet | Organizations, Sites, Robots, **Fleet Map** (`dataMode: unavailable`) |
| Operations | Tasks, Cleaning, Telemetry, Alerts, Events, Errors |
| Insights | Logs, **Analytics** (`dataMode: unavailable`) |
| Administration | Users, Roles, Permissions, Audit Logs, Settings |

19 reusable UI components, `--sakar-*` design tokens (orange `#ff914d`/`#ff6100`, light-only theme), `useApi` + `States.tsx` (Loading/Error/Empty) pattern, `SimulatedDataBanner`/`UnavailableFeature` disclosure components already used for the two `unavailable` screens above. Single 900px responsive breakpoint, no mobile hamburger.

---

## C. Screen-by-Screen Gap Analysis

| Sakar screen | Keenon equivalent | Classification |
|---|---|---|
| Dashboard | Dashboard | **NEEDS VISUAL REDESIGN** — Sakar's is generic; Keenon's uses an announcement carousel, quick-link tiles, and a real Task Distribution/Statistics block worth adopting |
| Organizations | (no direct equivalent — Keenon has no multi-tenant org tree) | Sakar-specific, no redesign needed |
| Sites | Store Management → Store list | **PARTIALLY IMPLEMENTED** — Sakar's Site entity already maps field-for-field to Keenon's Store (name/address/contact); table layout could mirror Keenon's |
| Robots (list) | Robot management → Robot list | **NEEDS VISUAL REDESIGN** — Keenon's has a "Display field" column-toggle modal, richer filter bar (production code, use type, area, agent) worth adopting |
| Robot detail | Robot Detail (8 tabs) | **MISSING** most tabs — see Section F |
| Fleet Map | Map management tab (per-robot) | **MISSING / BACKEND BLOCKED** → now **downgraded to just MISSING**, see Section G (map API is confirmed real) |
| Tasks | Task management (recurring schedules) + Task record (run history) | **MISSING** — Sakar's Task entity is ad-hoc/one-shot bookkeeping only, no recurring-schedule concept, no dispatch to a robot (see Section H) |
| Cleaning | Task record / Cleaning Daily Report | **PARTIALLY IMPLEMENTED** — Sakar has a `CleaningSession` entity and screen but it's not wired to the same rich KPI-card + report-export pattern |
| Telemetry | (folded into Robot Detail statistics) | Sakar-specific, keep as-is |
| Alerts | (no direct equivalent; closest is Customer Repair Requests) | Sakar-specific, keep as-is |
| Events / Errors / Logs | Mission Log (Main Task Records / Subtask Records) | **PARTIALLY IMPLEMENTED** — Sakar splits these three ways; Keenon unifies into one filterable Mission Log with a Business Line toggle |
| Analytics (unavailable) | Operational Dashboard (5 sub-screens) | **MISSING** — real, valuable reference pattern, see Section E |
| Users / Roles / Permissions | Account Permission Platform (Role list / Internal / External User List) | **EXACTLY ALIGNED** in concept — Sakar's IAM model is actually more capable (materialized-path tenant scoping); only the External-User-facing self-service concept has no Sakar equivalent (not needed — Sakar's clients don't get portal logins yet) |
| Audit Logs | (no direct equivalent) | Sakar-specific, keep as-is |
| Settings | (no direct equivalent) | Sakar-specific, keep as-is |
| — no Sakar screen — | Store Real-Time Data Statistics, Operation Ranking | **MISSING** — valuable "Reports" module reference, see Section E |
| — no Sakar screen — | Inventory control / Sub-Agent Inventory | **MISSING** — this is the previously-requested "Inventory" module; Keenon's version is literally the un-bound-stock ↔ bound-to-site workflow, see Section M |
| — no Sakar screen — | OTA Management, Device Management (remote desktop/monitor), IoT Platform, New Resource Configuration, Open Platform, Learning Center | **OUT OF SCOPE** — vendor/hardware-ops tooling, not part of the web-platform gap list the user prioritized |

---

## D. Navigation Comparison

- **Keenon:** icon-only collapsed rail by default, hover/click reveals a flyout or expands to icon+label; each top module can host a completely separate micro-frontend with its own secondary sidebar (breadcrumb shows `Module / Sub-page`).
- **Sakar:** single flat sidebar, 5 grouped sections, always expanded, no collapse/rail mode, no breadcrumbs.
- **Recommendation:** keep Sakar's single-app architecture (no reason to fragment into micro-frontends), but adopt (a) a collapsible icon-rail mode for small screens instead of the current single 900px reflow breakpoint, and (b) a breadcrumb component for any screen that gains its own detail/sub-tabs (Robot Detail, Site Detail).

## E. Dashboard Comparison

Keenon's Dashboard and its separate "Operational Dashboard" module together suggest a pattern Sakar's single flat Dashboard doesn't yet have:
1. A **KPI card row** (tasks today, mileage today, calls today, active machines) — Sakar has robot counts but no fleet-wide today's-activity cards.
2. A **time-series chart with tabbed metrics** (Number of tasks / calls / mode proportion) over Recent 7/30 days.
3. A **leaderboard** (Store ranking / Robot ranking by cumulative tasks or mileage).

These map directly onto Sakar's already-real `CleaningSession`, `RobotTask`, and `RobotTelemetryEntry` data — no backend gap, just an unbuilt aggregation endpoint + a new UI. This is the strongest candidate to retire the `Analytics: unavailable` flag.

## F. Robot Detail Comparison (high priority)

Sakar currently has **no dedicated Robot Detail route** — robot info is shown inline on the Robots list/`RobotStatusPanel`. Keenon's 8-tab pattern maps onto Sakar's existing (real) data as follows:

| Keenon tab | Sakar backend data already available | Gap |
|---|---|---|
| Robot information | `Robot`, `RobotStatusSnapshot`, `BatteryInfo` (real, GET_STATUS/GET_BATTERY wired) | UI only — no detail page exists |
| Map management | `RobotAdapter.getMap()`/`getAreas()` implemented in `KeenonRobotAdapter`, confirmed live-tested in `KEENON_C40_CLOUD_API_AUDIT.md` (`GET /api/open/custom/robot/map`, `/map/position`) | **No REST endpoint** — same "implemented in adapter, never wired to a controller" pattern already fixed once for battery |
| Task management (schedules) | No backend concept of a *recurring* schedule exists — `RobotTask` is one-shot | Real backend gap — needs a new entity/table, not just a new endpoint |
| Task record | `CleaningSession` entity (real) | Needs a per-robot history endpoint + KPI aggregation |
| Statistics | `RobotTelemetryEntry` (real) | Needs an aggregation endpoint (sum/avg over date range) |
| Trial Run Record | No backend concept | Not a priority — Keenon's own data was empty too |
| Configuration management | No backend concept (per-robot push-notification subscribers) | Not a priority — low value, empty on Keenon's own account |
| Cleaning Daily Report | `CleaningSession` + `RobotTask` (real) | Needs a report-generation endpoint (can defer PDF export) |

## G. Map/Fleet Comparison

This audit **upgrades confidence** versus the prior code-only audit: Sakar's own `KEENON_C40_CLOUD_API_AUDIT.md` documents `GET /api/open/custom/robot/map` and `/map/position` as live-tested, working Keenon endpoints, and this session's live browser inspection independently confirmed it by rendering a real, rich, multi-layer floor-plan for an actual Sakar robot (layers: Base Map, No-go Line, Target Point, Area, Restricted area, Teaching Path, Speed Limit, Gate, Elevator).

Sakar's `Fleet Map` nav item is currently `dataMode: 'unavailable'`. Given the adapter method already exists (`RobotAdapter.getMap()`), the correct classification is **MISSING (backend endpoint + frontend), not BACKEND BLOCKED** — the same "adapter real, controller missing" gap already closed once for battery is the template to reuse here.

## H. Cleaning/Task Comparison

This is the single largest conceptual gap. Keenon cleanly separates:
- **Task management** = a *recurring schedule definition* (cron-like: start/end time, frequency, cleaning mode, target areas) that the robot itself executes autonomously once configured.
- **Task record** = the *resulting history* of executed runs (whether from a schedule, an ad-hoc dispatch, or a hand-push).

Sakar's `RobotTask`/`TaskStatus` model is a single flat one-shot record with no schedule concept, and — per this conversation's earlier code-read of `RobotTaskService` — **never actually dispatches anything to a robot** (pure Sakar-side bookkeeping). Closing this gap needs:
1. A decision on whether Sakar's Task model should gain a `Schedule` concept (new entity) or stay one-shot-only for now.
2. Wiring `RobotTaskService` (or a new command path) to the already-real `RobotAdapter.startTask()`/`stopTask()`/`pauseTask()`/`resumeTask()` methods — currently unreachable dead code from `RobotAdapterRegistry`, same class of gap as the pre-battery-fix status/battery endpoints.

## I. Administration Comparison

Sakar's Users/Roles/Permissions is **already more sophisticated** than Keenon's (real tenant-scoped materialized-path hierarchy vs. Keenon's flat Internal/External split) — no redesign needed here, this is a strength to preserve, not a gap to close.

---

## J. Component Reuse Plan

No new design system needed. Existing Sakar primitives cover every Keenon pattern seen:

| Keenon pattern | Sakar component to reuse |
|---|---|
| Filter bar + Search/Reset | Existing filter-bar pattern already used on Robots/Tasks/Alerts pages |
| KPI card row | New small component, same visual language as existing `Card` |
| Sectioned-card detail tabs | `Card` + new `Tabs` primitive (does not yet exist — first new component needed) |
| Loading/Error/Empty states | `States.tsx` (already exists, reuse as-is) |
| "No data" / unavailable feature banner | `SimulatedDataBanner`/`UnavailableFeature` (already exists, reuse as-is) |
| Multi-layer map viewer with layer toggles | New component — no Sakar equivalent exists yet; largest net-new UI investment in this whole audit |

## K. Backend Capability Matrix

| Capability | Adapter method | REST endpoint | Frontend |
|---|---|---|---|
| GET_STATUS | ✅ real | ✅ `/robots/{id}/status` | ✅ wired |
| GET_BATTERY | ✅ real | ✅ `/robots/{id}/battery` | ✅ wired |
| GET_TELEMETRY / GET_EVENTS / errors / logs | ✅ real | ✅ (Phase 9 web-platform work) | ✅ wired |
| GET_MAP / GET_AREAS | ✅ real (`KeenonRobotAdapter`) | ❌ no controller method | ❌ none |
| START_TASK / STOP_TASK / PAUSE_TASK / RESUME_TASK / RETURN_TO_DOCK | ✅ real | ❌ `RobotAdapterRegistry` referenced only by `RobotController`, no method exposes these | ❌ none |
| Recurring schedule (Keenon "Task management") | ❌ no backend concept at all | ❌ | ❌ |
| Fleet-wide KPI aggregation (Dashboard/Reports) | Data exists (`CleaningSession`, `RobotTask`, `RobotTelemetryEntry`) but no aggregation query/endpoint | ❌ | ❌ |
| Inventory (unbound stock ↔ site binding) | No backend concept — Sakar's `Robot` entity has no "unbound stock" lifecycle state distinct from `REGISTERED` | ❌ | ❌ |

## L. Mock Data Audit

No new mock data was introduced by this audit (read-only). Confirmed still-simulated on the Sakar side (from the existing `types/domain.ts` annotations): only the Timeline tab's merged view (`RobotEventRecord`/`TimelineEntry`) remains simulated — telemetry/events/errors/logs themselves are already real per the prior session's work. This audit found no case where Keenon's UI implied Sakar should *add* new mock data; every gap identified above is a genuine missing-API or missing-UI gap, not a call to fabricate data.

## M. Missing Features — by UI / API / DB / Integration

| Feature | UI | API | DB | Integration |
|---|---|---|---|---|
| Robot Detail page (tabs shell) | Missing | n/a | n/a | n/a |
| Map viewer | Missing | Missing (`GET_MAP` endpoint) | n/a (no persistence needed, live query) | Keenon adapter call ready |
| Recurring cleaning schedules | Missing | Missing | Missing (new table) | Needs new adapter method (Keenon has `clean/strategy` family per prior API audit) |
| Command dispatch (start/stop/pause/resume/dock) wired to a real UI control | Missing | Missing (controller method) | n/a (`RobotCommand` table already exists) | Adapter methods already real |
| Fleet-wide KPI dashboard / Reports | Missing | Missing (aggregation endpoint) | n/a (source tables exist) | n/a |
| Inventory (unbound-stock lifecycle) | Missing | Missing | Missing (new `Robot` lifecycle state or new table) | n/a |
| Task-record KPI cards per robot | Missing | Missing (aggregation over `CleaningSession`) | n/a (exists) | n/a |

## N. Phased Implementation Roadmap (proposed, not started)

1. **Robot Detail shell** — new route `/robots/:id`, tabs primitive, "Robot information" tab only (reuses 100% real, already-wired data: status/battery/capabilities). Lowest risk, highest immediate payoff.
2. **Map endpoint + viewer** — new `GET /robots/{id}/map` controller method (same pattern as the battery fix), basic single-layer viewer first, layer toggles later.
3. **Task record KPI + history tab** — new aggregation endpoint over `CleaningSession`, reuses existing entity.
4. **Command dispatch wiring** — expose start/stop/pause/resume/return-to-dock through the registry the same way battery was exposed; add matching UI controls with confirmation dialogs (these are real, physical-effecting commands — needs its own careful safety review before any UI ships a button for them).
5. **Fleet dashboard / Reports module** — new nav item, aggregation endpoints, KPI cards + leaderboard.
6. **Recurring schedules** — largest lift: new DB entity, new adapter method, new UI (Task management tab) — sequence last since it requires the most new backend design.
7. **Inventory module** — new `Robot` lifecycle state (unbound stock) + bind-to-site workflow UI, modeled on Keenon's Inventory control screen.

## O. First Implementation Slice (proposed — awaiting approval)

**Objective:** Add a Robot Detail page showing real, already-available data — the smallest slice that both closes a real UI gap and requires zero new backend capability.

- **Screens:** New route `/robots/:id`, single "Overview" tab (Robot information equivalent): name, serial, model, status, capabilities, live battery/status probe (reuse `RobotStatusPanel`).
- **Files (new):** `web/src/features/robots/RobotDetailPage.tsx`, `web/src/features/robots/RobotDetailPage.test.tsx`; route registration in `web/src/App.tsx`.
- **Files (modified):** `web/src/features/robots/RobotsListPage.tsx` (link robot rows to the new detail route), `web/src/components/layout/navConfig.ts` (no change needed — Detail is not a nav item, it's a drill-down).
- **Components:** reuse `Card`, `States.tsx`, existing `RobotStatusPanel`; no new primitive required for this slice (Tabs primitive deferred to the next slice, when a 2nd tab is added).
- **Backend dependencies:** none — uses only already-real `GET /robots/{id}`, `/status`, `/battery`.
- **Tests:** new frontend test for `RobotDetailPage` (loading/error/success states, matching the existing test patterns for other list/detail pages); no backend tests needed (no backend change).

---

## STOP — Awaiting Approval

This concludes the read-only audit. No implementation has begun. Per your instructions, I will not implement anything — including the first slice proposed in Section O — until you explicitly reply **"IMPLEMENT IT."**
