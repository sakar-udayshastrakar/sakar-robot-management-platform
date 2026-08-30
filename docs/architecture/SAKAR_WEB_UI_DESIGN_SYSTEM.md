# Sakar Web UI — Design System

Companion to `SAKAR_WEB_APPLICATION_ARCHITECTURE.md` (stack/data-flow). Describes what is actually implemented under `web/src/`, not a target spec.

**Status: strictly aligned to the Sakar Robotics Support Portal.** An earlier pass built an *inspired-by* dark enterprise theme; a subsequent, explicit design-system-alignment task replaced it with the Support Portal's actual tokens, logo, and layout structure, transcribed directly from that product's real frontend source (`support-portal/support-portal/frontend/src/`, a sibling project on this machine) so the two products are visually one family — not "inspired," but the same system.

---

## 1. Design direction

A dense, professional, light enterprise console — Sakar Robotics' own established look (white surfaces, light-slate background, orange brand accent, Roboto), applied to fleet-management content instead of the Support Portal's ticketing content. No dark mode: the Support Portal has none, so neither does this.

## 2. Brand — logo

The header uses the **exact logo asset** the Support Portal's own `AdminLayout.jsx`/`AdminLoginPage.jsx` render (`img src={logoFull}`), copied byte-for-byte into `web/src/assets/sakar-logo-full.png` from `support-portal/support-portal/frontend/src/assets/logo-full.png`. Confirmed identical: both files hash to the same Vite build filename (`logo-full-_DLw_PJn.png`) since Vite's asset hashing is content-based. No logo was redrawn, approximated, or replaced with a monogram.

## 3. Color system

All colors are CSS custom properties on `:root` in `web/src/index.css`, transcribed from the Support Portal's real Tailwind tokens and utility-class usage (`support-portal/frontend/src/index.css`'s `@theme` block, plus the exact classes in `Card.jsx`/`Badge.jsx`/`Button.jsx`/`Input.jsx`/`AdminLayout.jsx`/`AdminDashboardPage.jsx`).

| Token | Value | Tailwind source |
|---|---|---|
| `--sakar-bg` | `#f8fafc` | `bg-slate-50` (page background) |
| `--sakar-surface` | `#ffffff` | white (header, sidebar, cards) |
| `--sakar-border` | `#e2e8f0` | `border-slate-200` |
| `--sakar-border-strong` | `#cbd5e1` | `border-slate-300` (inputs) |
| `--sakar-text` | `#0f172a` | `text-slate-900` (headings) |
| `--sakar-text-muted` | `#475569` | `text-slate-600` (sidebar/body text) |
| `--sakar-text-faint` | `#64748b` | `text-slate-500` (subtitles, KPI labels) |
| `--sakar-text-subtle` | `#94a3b8` | `text-slate-400` (section titles, placeholders) |
| `--sakar-primary` (600) | `#ff914d` | `bg-brand-600` (primary buttons) |
| `--sakar-primary-hover` / `-strong` (700) | `#ff6100` | `bg-brand-700` / `text-brand-700` (hover, active nav, links) |
| `--sakar-primary-soft` (50) | `#fff6f0` | `bg-brand-50` (active sidebar row) |
| `--sakar-primary-soft-strong` (100) | `#ffe9db` | `bg-brand-100` (primary badge) |
| `--sakar-success` | `#059669` | `emerald-600` |
| `--sakar-warning` / `-text` | `#d97706` / `#92400e` | `amber-600` / `amber-800` (Badge.jsx uses 800 for text) |
| `--sakar-danger` | `#dc2626` | `red-600` |
| `--sakar-info` | `#2563eb` | `blue-600` |

Radius scale matches Tailwind exactly: `--sakar-radius-sm: 8px` (`rounded-lg` — sidebar links, icon buttons), `--sakar-radius-md: 12px` (`rounded-xl` — buttons, inputs, mobile cards), `--sakar-radius-lg: 16px` (`rounded-2xl` — desktop cards, KPI tiles, modals). Font stack is copied verbatim: `Roboto, system-ui, Avenir, Helvetica, Arial, sans-serif`.

Alert-severity (Critical/High/Medium/Low) and event-severity (Info/Warning/Error/Critical) remain two distinct scales (unchanged from the prior redesign pass) — both now expressed with the tokens above instead of the earlier dark-theme palette.

## 4. Layout

```
┌───────────────────────────────────────────────────────────────┐
│ [logo] │ Robot Management        🔔2  user@email  ⏻ Logout    │  ← header, white, sticky
├──────────┬────────────────────────────────────────────────────┤
│ Dashboard│                                                    │
│ ── Fleet │                                                    │
│ Orgs     │                                                    │
│ ...      │                Main content                        │
│ ◂Collapse│                                                    │
└──────────┴────────────────────────────────────────────────────┘
```

This mirrors `AdminLayout.jsx`'s actual DOM structure exactly: a full-width sticky header on top (logo, a vertical separator, product name — no breadcrumb/search/status badge in this row), then a row below it containing a white sidebar and the content area. `AppShell.tsx` was restructured (previously sidebar+header were both inside one flex row) to match this two-level shape.

**Sidebar** (`Sidebar.tsx`) — white, `border-right`, starts directly with nav (no duplicate logo block, matching the Support Portal's sidebar having none either). Sections and order match the task's reference exactly: Overview (Dashboard) · Fleet (Organizations, Sites, Robots, Fleet Map) · Operations (Tasks, Cleaning, Telemetry, Alerts, Events, Errors) · Insights (Logs, Analytics) · Administration (Users, Roles, **Permissions**, Audit Logs, Settings). Active row: `bg-brand-50`/`text-brand-700`, exactly as `AdminLayout.jsx`'s `SidebarLink`. A manual collapse toggle (icon-only rail) is additive, kept from the prior pass for desktop power users — the Support Portal itself only collapses via a mobile drawer.

**Header** (`TopNav.tsx`) — logo + separator + "Robot Management" (left); a notifications bell with an unread-count badge, the session email, and an explicit "Logout" button (right) — matching `AdminLayout.jsx`'s header contents field-for-field. The prior pass's global search bar and backend-status badge were removed from this row (the reference header has neither); backend health remains real and visible on the Dashboard's System Health card, and robot search remains available on the Robots page's own filter bar.

## 5. Component system

Same component set as the prior redesign pass (`Card`, `Badge`, `StatusBadge`, `SeverityBadge`, `MetricCard`, `DataTable`, `SearchBar`, `FilterBar`, `PageHeader`, `Modal`/`Drawer`/`ConfirmDialog`, `Toast`, `States`, `Icon`), restyled to the tokens above. `States.tsx` gained a tinted icon box (matching `EmptyState.jsx`/`ErrorState.jsx`/`Loader.jsx`'s `rounded-2xl bg-slate-100`/`bg-red-100` icon treatment) instead of text-only states. `StatCard` was already retired in favor of `MetricCard` in the prior pass — kept retired here.

## 6. New: Permissions page

The reference sidebar structure explicitly separates **Permissions** from **Roles**. `PermissionsPage.tsx` was added — a real, static table of the backend's authoritative `PermissionCode` enum (`iam/PermissionCode.java`), the same real data source `RolesPage`/RBAC already use. No simulated data and no new backend call.

## 7. What stayed unchanged from the prior pass

Real-vs-simulated data behavior, RBAC/permission gating, robot detail tabs, WebSocket feature flag, accessibility conventions (focus rings, `aria-*`, semantic tables), and the responsive breakpoints — none of this is a "look," so none of it needed to change for a design-system-alignment task. See `docs/requirements/SAKAR_WEB_UI_UX_REQUIREMENTS.md` for that inventory, and `docs/requirements/SAKAR_PHASE_4_WEB_IMPLEMENTATION_REPORT.md` for the original real-vs-simulated gap list, both still accurate.
