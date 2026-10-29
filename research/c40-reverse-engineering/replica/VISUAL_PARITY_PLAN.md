# C40 Replica — Visual Parity Plan (from original reference screenshots)

**Date:** 2026-10-25
**Reference source:** `C:\Users\sakar\Downloads\keenonwebpic\keenonwebpic\` (user-supplied original-app screenshots) — the reference evidence previously documented as missing (`REMAINING_UNKNOWN_ITEMS.md`). This unblocks reference-driven visual parity.

**Reference inventory:** cleaning app (39), robot installation (19), Remote Assistant (2), keenon services (1), krlog (2). The Sakar replica scope is the **cleaning app** (→ `SakarC40Agent/operator-ui`) and **robot installation** (→ `SakarRobotInstallationAssistant`); the other three are system apps with no current Sakar replica.

## Cleaning app — reference vs replica (assessed from screenshots)

| Screen | Original (reference) | Replica today | Delta / work | Effort |
|---|---|---|---|---|
| Home | logo; Manual Push + Recharge + mute/light/settings; robot render; "I'm lost!" state pill; cards: Start Cleaning (pin icon), Scheduled Cleaning (clock), Teaching Mode, Manual Drive | **ALIGNED ✅** — pin + clock icons now match (Sakar orange); same 3-col geometry/header/hero. Status badge intentionally honest ("Not connected" vs "I'm lost!" — no robot). Verified on-emulator 2026-10-25. | (optional) status cluster could add signal/wifi glyphs | Done (decision: Sakar-branded) |
| Manual Drive | 5 mode cards (Sweep&Mop, Water Suction✓, Sweep&Vacuum, Sweep&Push, Sweep) + Gentle/Standard/Powerful + Start Cleaning | **near-exact match already** | accent color only | Minimal |
| Start Cleaning | **map-based area selection**: "Start Cleaning Now / Task List" tabs, countdown, floor list (1F), occupancy map w/ selectable zones + robot pos + map toolbar, Select All, "N Times", "Start Clean" | simple mode/intensity/cycles form (whole-floor only) | **major rebuild** to map-centric area selection (simulated map where no real map) | High |
| Settings | **two-pane master-detail**: left sidebar (Consumables Statistics, Cleaning Data, Workstation, Charging, Screen Lock, General, Screen Emoji…); right = section detail (e.g. consumable wear cards) | **ALIGNED ✅** — rebuilt as two-pane (sidebar of 12 sections + inline detail via `SettingsPane(embedded)`; each detail screen reused unchanged). Sakar-branded (orange highlight/accent). Verified on-emulator 2026-10-25 (Consumables + Sound). | — | Done |
| Scheduled Cleaning | `in side schdule clean.png` (not yet compared in detail) | `ScheduleScreens.kt` exists | TBD | TBD |
| Teaching Mode | `teaching mode.png`, `in side teaching mode.png` | `TeachRouteScreen.kt` exists | TBD | TBD |
| Edit Task | `edit task.png` | part of schedule/task config | TBD | TBD |
| Manual Push | `manual push.png` | Home button (placeholder) | TBD | TBD |
| Super User (5) + Robot Debugging (6 + ROS/charge/water tests) | `Super User/…` | SuperUser screens exist (reference-confirmed earlier) | detail compare | Medium |

## Robot installation — reference vs replica
19 references incl. Business settings (Elevator settings → Draw Elevator / List Config / robot id; Remote control robot; Scheduling path) + mapping/other. Replica = `SakarRobotInstallationAssistant` (home, wizard, mapping, floors, checklist, report). **Not yet compared screen-by-screen** — to be assessed next.

## Method (per project reference-first rule, `docs/ui/REFERENCE_UI_MAP.md`)
For each screen, before writing UI: (1) add a reference row to `REFERENCE_UI_MAP.md` pointing at the authoritative screenshot, (2) align the replica, (3) build + capture a replica screenshot, (4) compare and iterate. No layout is guessed; where the original shows real robot/map data the replica uses clearly-labeled simulated data (never presented as live).

## Open decision (blocks color/branding work — see chat)
"Exactly the same UI" conflicts with the standing rule *"use Sakar branding; do not expose the Keenon product name/logo."* Structure/component parity proceeds regardless; the **accent color** (Sakar orange vs the originals' blue) and whether to keep Sakar's logo/name is the user's call. The Keenon wordmark/logo itself will **not** be reproduced as the app identity (impersonation / standing-rule conflict); Sakar's logo/name stays.

## Suggested order (highest visible impact first)
1. Home detail alignment (Low) · 2. Settings two-pane (Medium) · 3. Start Cleaning map-based area selection (High) · 4. Teaching/Schedule/Edit-task compare + align · 5. Super User/Debug detail · 6. Installation app pass.
