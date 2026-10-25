# C40 Android Replica — Coverage & Gap Report

**Date:** 2026-10-25
**Scope:** Coverage matrix tying the C40 reverse-engineering evidence to the **existing** Sakar Android replica implementation, with honest gaps.
**Confidence legend:** `CONFIRMED_STATIC` · `CONFIRMED_RUNTIME` · `INFERRED` · `UNKNOWN` · `NOT_APPLICABLE`.

> **Location note:** The master prompt asked for `REVERSE_ENGINEERING_RESULTS/COVERAGE_AND_GAP_REPORT.md`. The original REA workspace (`D:\Sakar Robotics Projects\C40 S-LS-M014C00-RW-F00-V246\REVERSE_ENGINEERING_RESULTS`) is **preserved unchanged** per standing instructions; this report lives in the project's own RE area instead. Original APKs and reports were not modified.

## 0. Executive finding

The C40 Android replica is **already substantially implemented** as two deliberately Sakar-branded, safety-gated Jetpack-Compose apps, governed by a mature reference-screenshot workflow — it is not a greenfield build:

- **`robot/SakarC40Agent`** — the cleaning HMI replica (14 Gradle modules). `operator-ui` screens cover Home, Start Cleaning, Manual Drive (cleaning-mode/intensity selector — the five C40 modes), Schedule, Teach Route, Maps (cleaning-zone sketch), Installation, Diagnostics, Settings (Charging/Workstation, Consumables, Display/Sound/Lock, General/Resource, Network, Robot Info), and Super User (Network, Resource, General, Robot Debugging + full hardware Debug dashboard, System Settings). Safety-gated (`OperatingMode.DIAGNOSTIC_ONLY`), real Peanut-SDK bridge, MQTT link to the Sakar backend.
- **`robot/SakarRobotInstallationAssistant`** — the mapping/installation replica. UI covers home, wizard, mapping (`MappingScreen`, `OccupancyGridView`), floors, checklist, diagnostics, report (PDF export), settings.
- **Governance already in place:** `docs/ui/REFERENCE_UI_MAP.md` (real-robot appearance source-of-truth, built from reference screenshots), `FEATURE_REGISTRY.{json,md}`, `ROUTE_REGISTRY.md`, `COMPONENT_REGISTRY.md`, `UI_CHANGE_LEDGER.md`, `CLAUDE_UI_RULES.md`, `references/` (screenshots + measurements); deep RE in `docs/engineering/` + `docs/architecture/`; and the `research/c40-reverse-engineering/` archive (UI/ROS/action inventories, assets).

**Two hard constraints shape every "gap" below:**
1. **Reference-first rule (project policy + master-prompt policy):** UI must not be built from guessed layouts. New replica screens require an *original-robot reference screenshot*. We have Sakar **replica** screenshots (`robot/.mcp-artifacts/`, `docs/ui/references/`) and **minified** Keenon Vue assets — not clean original-app screenshots for unbuilt screens. So "build more screens accurately" is **blocked on reference evidence** for anything not already CONFIRMED.
2. **Branding:** deliverables use Sakar branding and must not surface the vendor product name in replica UI (per the master prompt) — so pixel-identity to the original is explicitly *not* the goal; functional + layout fidelity against references is.

## 1. Deliverables mapping (the master prompt's 14 docs)

Most requested reports already exist in equal-or-better form; this avoids duplicating/forking the team's maintained docs.

| Requested report | Existing authoritative artifact | Action |
|---|---|---|
| COVERAGE_AND_GAP_REPORT.md | — (this file) | **Created** |
| COMPLETE_ANDROID_UI_INVENTORY.md | `docs/ui/FEATURE_REGISTRY.{json,md}`, `ROUTE_REGISTRY.md` | Reference (maintained) |
| ANDROID_SCREEN_SPECIFICATIONS.md | `docs/ui/REFERENCE_UI_MAP.md` + `references/measurements/` | Reference |
| COMPLETE_UI_NAVIGATION_GRAPH.md | `operator-ui/.../nav/SakarNavGraph.kt`, `ROUTE_REGISTRY.md`; `research/.../ui/UI_NAVIGATION_FLOW.md` | Reference |
| MAPPING_UI_REVERSE_ENGINEERING.md | `docs/engineering/` (installation-assistant audits), `research/.../workers/installation_assistant_app.md` | Reference |
| MAP_RENDERING_AND_COORDINATE_SYSTEM.md | `MappingModels.kt` (`resolutionMetersPerPixel`), `OccupancyGridView`; see §4 here | **Gap documented (§4)** |
| COMPLETE_ROS_TOPIC_NODE_INVENTORY.md | `research/.../ROS_TOPICS_INVENTORY.md`, `ROS_STATIC_REFERENCES.csv` (1,115 refs) | Reference |
| ROS_SERVICES_AND_ACTIONS_INVENTORY.md | `research/.../ROS_STATIC_REFERENCES.csv`, `ROS_COMMUNICATION_ARCHITECTURE.md` | Reference |
| ANDROID_TO_ROBOT_ACTION_MAPPING.md | `research/.../ui/UI_ACTION_METHOD_MAPPING.md` | Reference |
| ANDROID_REPLICA_ARCHITECTURE.md | `SakarC40Agent/README.md`, `docs/engineering/05_KEENON_TO_SAKAR_ARCHITECTURE.md` | Reference |
| IMPLEMENTATION_STATUS.md | `docs/ui/FEATURE_REGISTRY.json` (per-feature status) + §2–§3 here | Reference + §2 |
| RUNTIME_VERIFICATION_CHECKLIST.md | — | **Created** (sibling file) |
| SCREENSHOT_VISUAL_COMPARISON_REPORT.md | `docs/ui/UI_CHANGE_LEDGER.md`, `references/`, `.mcp-artifacts/` | Reference (ongoing) |
| REMAINING_UNKNOWN_ITEMS.md | — | **Created** (sibling file) |

## 2. Screen coverage matrix (cleaning HMI — `SakarC40Agent/operator-ui`)

Original screens from `research/.../ui/UI_SCREEN_INVENTORY.md` (names CONFIRMED_STATIC from `routeMap.json`; original pixel layout UNKNOWN — minified Vue). Reference status from `docs/ui/REFERENCE_UI_MAP.md`.

| Original screen (route) | Replica owner | Implemented | Reference status | Gap / next |
|---|---|---|---|---|
| Home (`/home`) | `home/HomeScreen.kt` | ✅ | CONFIRMED (ref screenshot) | — |
| Cleaning start/mode/area (`/work`,`/immediately`,`/select_area`) | `cleaning/StartCleaningScreen.kt`, `manualdrive/ManualDriveScreen.kt` (mode/intensity: 5 C40 modes) | ✅ | CONFIRMED ("Hand Cleaning" ref) | Area selection depends on backend areas (live) |
| Scheduled cleaning (`/timer`) | `schedule/ScheduleScreens.kt` | ✅ | CONFIRMED | — |
| Teach path (`/teach_path*`) | `teachroute/TeachRouteScreen.kt` | ✅ | USER-STATED | Actuation gated (HARDWARE_TEST) |
| Charging (`/charge`) | `settings/ChargingWorkstationScreens.kt` | ✅ (settings) | UNCONFIRMED | Dedicated charging status screen — needs ref |
| Consumables (`ConsumptiveStatistic`) | `settings/ConsumablesScreen.kt` | ✅ | UNCONFIRMED | — |
| Settings hub + subscreens | `settings/SettingsRootScreen.kt` (+ Display/Sound/Lock, General/Resource, Network, RobotInfo) | ✅ | Network/Resource/General CONFIRMED | Audio/Voice, Do-Not-Disturb, Water mgmt subscreens — needs ref |
| Maps / zones | `maps/MapsScreens.kt` (zone sketch, `drawPolygonZone`) | ✅ (simulated, labeled) | UNCONFIRMED | Real occupancy render from backend map PNG — see §4 |
| Diagnostics / hardware debug | `diagnostics/`, `superuser/RobotHardwareDebugScreen.kt` | ✅ | CONFIRMED (8 ref screenshots) | — |
| Super User (Network/Resource/General/Debug/System) | `superuser/*` | ✅ | CONFIRMED (5 ref screenshots) | — |
| Localization / relocation (`/location`,`/relocation*`) | — | ❌ | UNCONFIRMED | **Gap**: no dedicated relocation screen; needs ref + robot runtime |
| Manual movement controls | `manualdrive/ManualDriveScreen.kt` (added, gated) | ✅ (gated) | additive (ledgered) | — |
| Dialogs / loading / empty / error states | `common/CommonComponents.kt` + per-screen | ✅ (framework) | mixed | Per-original-dialog parity needs refs |
| PIN auth (`/password`) | `home/IdleLock.kt`, `superuser/SuperUserAuth.kt` | ✅ (placeholder: any-4-digit) | USER-STATED | Real PIN policy requires product decision |
| Voice/announcements | (web Voice feature added earlier; Android: Audio settings) | partial | UNKNOWN | Android voice UI — needs ref + decision |

## 3. Mapping app coverage (`SakarRobotInstallationAssistant`)

Original = Vue3/Element-Plus SPA (i18n sections: scan, revise/mapEdit, target, scene, danger, dispatch, gate, busyArea, vslam, mapSync, robotList, bindCharge, offline, lock — `research/.../workers/installation_assistant_app.md`).

| Original section | Replica | Implemented | Gap |
|---|---|---|---|
| Mapping home / wizard | `ui/home`, `ui/wizard` | ✅ | — |
| Scan / mapping progress | `ui/mapping/MappingScreen.kt` (+ `OccupancyGridView`, simulated) | ✅ (labeled simulated) | Real scan needs rosbridge + robot |
| Map edit / rooms / zones / virtual walls | `ui/mapping` (+ floors) | partial | Room split/merge/label, virtual-wall editor parity — needs refs |
| Floors / multi-floor / elevator | `ui/floors/FloorsScreen.kt` | ✅ (structure) | Elevator/gate/dispatch config — needs refs + runtime |
| Checklist / diagnostics / report | `ui/checklist`, `ui/diagnostics`, `ui/report` (PDF) | ✅ | — |
| QR/mobile relay, map sync | — | ❌ | **Gap**: needs rosbridge `:8888` relay + robot |

## 4. Map rendering & coordinate system (genuine documentation gap)

- **Existing:** `OccupancyGridView` renders an occupancy grid on a Compose `Canvas`; `MappingModels.kt` carries `resolutionMetersPerPixel`; `operator-ui/maps` draws polygon zones in normalized canvas coords (clearly labeled "not the robot's map").
- **Original renderer:** the Keenon HMI uses **OpenLayers** over a locally-served tile/image map (`ol.js` in the peanut-clean bundle) — `CONFIRMED_STATIC` from the asset bundle. The robot stack is **ROS/cartographer** → standard `nav_msgs/OccupancyGrid` + `map.yaml` (`resolution`, `origin`, `width`, `height`). The exact OpenLayers projection/transform parameters used by the HMI are **UNKNOWN** (minified JS; not deobfuscated).
- **Safe, standard transform (ROS convention — `INFERRED` for this robot, standard for cartographer):**
  `world_x = origin_x + px * resolution`; `world_y = origin_y + (height - py) * resolution` (image y is top-down, map y is bottom-up). Pixel ← world inverts these. This is the correct basis for a backend-PNG-backed renderer and matches `resolutionMetersPerPixel`.
- **Gap/next:** a production occupancy renderer should consume the backend's real map metadata (`GET /robots/{id}/map` → resolution/origin/size) + `GET /robots/{id}/map/image` (PNG) and apply the transform above. Implementing it requires a synced real map (backend integration) to verify visually; **do not** hardcode the OpenLayers internals (UNKNOWN). Tracked in `REMAINING_UNKNOWN_ITEMS.md`.

## 5. ROS / action coverage

- **ROS inventory** (topics/services/nodes): fully documented statically in `research/.../ROS_TOPICS_INVENTORY.md` + `ROS_STATIC_REFERENCES.csv` (1,115 refs) + `ROS_COMMUNICATION_ARCHITECTURE.md`. **All `CONFIRMED_STATIC`; none `CONFIRMED_RUNTIME`** (no robot contacted). Live pub/sub direction, frequency, and advertisement remain `UNKNOWN` — see runtime checklist.
- **Android→robot action mapping:** `research/.../ui/UI_ACTION_METHOD_MAPPING.md` (UI→SDK→CoAP paths, CONFIRMED_STATIC). The Android replica reaches the robot only via the **Peanut SDK bridge** (`:sdk`) gated by `OperatingMode`, and via **MQTT** to the backend — never raw CoAP from UI. Real actuation is `HARDWARE_TEST`-gated and unverified without a robot.

## 6. Build / test status

See the execution report and `RUNTIME_VERIFICATION_CHECKLIST.md`. The apps are maintained with unit tests (operator-ui, data, robot, api, virtual-agent, installation-assistant core/ui). Actual build/test results for this pass are recorded in the final execution report.

## 7. Genuine remaining gaps (prioritized)

| # | Gap | Blocker | Priority |
|---|---|---|---|
| G1 | Localization/relocation screen | reference screenshot + robot runtime | Medium |
| G2 | Backend-PNG occupancy renderer with ROS transform (§4) | synced real map to verify | Medium |
| G3 | Mapping: room split/merge/label + virtual-wall editor parity | original-app references | Medium |
| G4 | Elevator/gate/dispatch + QR relay + map sync | rosbridge `:8888` + robot | Low–Med |
| G5 | Audio/Voice + Do-Not-Disturb + Water-management subscreens | reference screenshots | Low–Med |
| G6 | Real PIN policy (replace any-4-digit placeholder) | product/security decision | Low |
| G7 | Runtime ROS verification (upgrade CONFIRMED_STATIC → CONFIRMED_RUNTIME) | physical robot | Blocked |
| G8 | Real robot actuation end-to-end | physical robot + HARDWARE_TEST authorization | Blocked (safety) |

**None of G1–G8 can be completed accurately without either an original-robot reference screenshot (G1,G3,G5), a synced real map (G2), rosbridge/robot runtime (G4,G7,G8), or a product decision (G6)** — consistent with the master prompt's own "document missing evidence, do not invent" rule and the project's reference-first policy.
