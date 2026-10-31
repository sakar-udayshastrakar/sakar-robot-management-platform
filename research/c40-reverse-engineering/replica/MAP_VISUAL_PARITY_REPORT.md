# C40 Replica — Map Visual Parity Report

**Date:** 2026-10-31
**Reference source:** `C:\Users\sakar\Downloads\keenonwebpic\keenonwebpic\` (user-supplied original-app screenshots)
**Baseline commit:** `e359d61` (verified as current `HEAD` on `dev`; unchanged by this iteration)
**Scope of this iteration:** map and map-related screens only. Nothing committed/staged — for review.

> Legend: **[VERIFIED]** = confirmed on the emulator or by build/test output this iteration. **[ASSUMPTION]** = inferred from screenshots/code, not separately proven.

---

## 1. Reference screenshot inventory

63 PNG images across 20 folders (dimensions read via System.Drawing). Grouped by function:

| Group (folder) | Files | Screens | Map-bearing? |
|---|---|---|---|
| cleaning app | 24 | home, start cleaning, manual drive, manual push, teaching mode (+inside), scheduled clean (inside), edit task, settings + Settings-detail captures (982×537 / 663×516) | **1** (`start cleaning.png`) |
| robot installation | 12 | map-deployment card home (+lang dropdown), 2-pane Set-up (Basic/Volume/Network/Business/Advanced), **mapping/commissioning wizard** | **4** (wizard steps) |
| Super User | 5 | super-user panels | no |
| robot debug | 7 | hardware/debug panels | no |
| Business settings / Draw Elevator / Elevator List Config / Elevator robotid / Elevator settings / Scheduling path / Remote control robot | 7 (1 each) | elevator + scheduling config forms | no (forms) |
| Industrial Computer ROS / Return-to-charge stress / Water Pressure Test | 3 | debug/test panels | no |
| Remote Assistant / keenon services / krlog | 5 | system apps (no Sakar replica) | no |

**Observed original accent:** cobalt blue (~#3B6CF0). Semantic colors in maps: green robot marker, red paths, orange/yellow zone boundaries, magenta robot-radius arc. Per the standing decision, the replica keeps **Sakar orange** for brand/interactive elements and does **not** reproduce Keenon branding.

## 2. Screenshot → application screen mapping (map-related)

| Reference | App screen (code) | In scope | Notes |
|---|---|---|---|
| `cleaning app/start cleaning.png` | `operator-ui` → `cleaning/StartCleaningScreen.kt` (`CleaningMap`) | **YES** | The one in-scope occupancy map in the cleaning app. |
| `robot installation/20-11-58, 20-14-56, 20-15-19, 20-15-37` | **`SakarRobotInstallationAssistant`** mapping/commissioning wizard (separate app) | **NO** | Task explicitly forbids modifying the Installation Assistant app. Documented, not touched. |
| `cleaning app/home, manual drive, manual push, teaching (+inside), schedule (inside), edit task` | respective `operator-ui` screens | n/a | **[VERIFIED]** none contains a map render (the "route" / folded-map thumbnails are decorative glyphs). |
| — | `operator-ui/maps/MapsScreens.kt` → `MapDetailScreen` | optional | Honest local polygon zone **sketch-pad**; has no occupancy-style reference screenshot. Left as-is. |

**Conclusion:** within the editable operator-ui cleaning app, **Start Cleaning is the only real map screen**, so it is the single highest-impact target. The installation mapping wizard is a separate, out-of-scope app.

## 3. Start Cleaning — before / after (vs `start cleaning.png`)

| Aspect | Reference | Replica BEFORE | Replica AFTER (this iteration) |
|---|---|---|---|
| Canvas background | gray (unknown/unscanned) | light border tint | **gray unknown** (`#BCC2CB`) |
| Rooms | irregular SLAM geometry | clean rectangles | **irregular polygons** (point-in-polygon) |
| Walls | black speckled lidar scan | none | **seeded black speckle** along room edges |
| Free space | light-blue navigable fill | plain surface rects | light free-space fill (`#F6F7F9`) |
| Selectable zones | translucent **red** polygons | orange rectangles | translucent **orange** polygons (Sakar accent) |
| Coverage | pink hatch in cleaned area | none | subtle orange coverage hatch in robot's room |
| Robot marker | orange pin | orange donut | **orange map pin** (head + tip + white center) |
| Grid | occupancy cells | 18-step grid | finer 22-step faint cell grid |
| Layout (tabs/floor selector/Select All/times/Start Clean) | ✓ | ✓ already matched | unchanged (kept) |

Structure now matches the reference; palette stays Sakar orange rather than cloning the original blue/red.

## 4. Files changed (this iteration)

| File | Change | Rationale |
|---|---|---|
| `robot/SakarC40Agent/operator-ui/.../cleaning/StartCleaningScreen.kt` | `SimZone` rectangles → **polygons** (+ ray-casting `contains`, centroid); added occupancy-map palette consts; **rewrote `CleaningMap`** (gray unknown bg, free-space polygons, seeded SLAM speckle, coverage hatch, orange pin); nudged 2F robot off the "Store" label | Match `start cleaning.png` occupancy structure while keeping branding + simulation honesty |

`StartCleaningUiTest.kt` also shows as modified vs `e359d61`, but that change is from the **previous** (already-reviewed) Start Cleaning iteration, not this map work; it still passes unchanged. No other files were touched. **No `git add` was run; nothing is staged.**

Final diff vs `e359d61`: `StartCleaningScreen.kt` ~455 lines changed (cumulative), `StartCleaningUiTest.kt` 49 (prior iteration). Pre-existing unrelated modified docs / untracked files were left untouched.

## 5. Map geometry & data integrity

- Zones are **normalized polygons (0..1)**; all drawing and hit-testing scale with the canvas, so labels/walls/robot stay consistent at any size or aspect ratio. **[VERIFIED]** by selecting an irregular room on-device.
- Tap selection uses **ray-casting point-in-polygon**, so hit areas follow the real polygon, not a bounding box. **[VERIFIED]**
- SLAM speckle is seeded by floor (`Random(label.hashCode())`), so the texture is **deterministic** across recompositions/scales. **[ASSUMPTION — logic-level]** (stable across the screenshots taken).
- Selection state is keyed by floor-namespaced zone IDs and cleared on floor switch — no cross-floor collision. **[VERIFIED]** (2F shows 0 selected after selecting on 1F).

## 6. Simulation honesty (unchanged, preserved)

- DEMO/SIMULATED banner retained: "demo floor plan — no real map is loaded and no cleaning command is sent to the robot." **[VERIFIED]** on-screen.
- The map is hardcoded `SIM_FLOORS` demo data — **not** presented as a live/real robot map.
- Start/Pause/Stop route only to `SimulatedCleaningRepository` (local `StateFlow` + `delay` loop; no gateway/SDK/MQTT/ROS). **[VERIFIED]** by code audit.

## 7. Build & test results (this iteration)

- `:app:assembleDebug` → **BUILD SUCCESSFUL** **[VERIFIED]**
- Unit tests (`operator-ui`) → **BUILD SUCCESSFUL**, no failures (module has no unit-test sources; `data` tests pass) **[VERIFIED]**
- Instrumented (`:operator-ui:connectedDebugAndroidTest`, Medium_Tablet emulator) → **22 tests, 0 skipped, 0 failed** (incl. the 6 `StartCleaningUiTest` cases recompiled against the polygon map) **[VERIFIED]**

## 8. Emulator verification (Medium_Tablet, 2560×1600)

- **1F**: occupancy map renders — gray unknown bg, irregular Lobby/Cafe/Corridor/Office polygons, speckled walls, faint grid, orange robot pin in Office. **[VERIFIED]**
- **Zone select**: tapping inside the irregular Lobby polygon → translucent-orange fill following the polygon, "1 area(s) selected", Start Clean enabled. **[VERIFIED]**
- **Floor switch 2F**: Room A/Room B/Hall/Store polygons render; selection cleared ("Please select a cleaning area first", Start Clean disabled); robot pin clear of the "Store" label after the nudge. **[VERIFIED]**

## 9. Remaining differences / not done

- **Robot-installation mapping wizard** (4 reference screens): out of scope — separate `SakarRobotInstallationAssistant` app, which the task forbids modifying. Not attempted.
- **Reference map toolbar (3 icons) and the "43s" countdown** on `start cleaning.png`: intentionally **omitted** — they imply live map-layer/route/zoom and auto-start behaviors that do not exist, which would break the data-honesty rule. Documented choice, not an oversight.
- Free-space / navigable fill uses a neutral light tone (not the original's blue) and zones use Sakar orange (not the original's red) — a deliberate branding choice, so an exact color match to the reference is not expected.
- The simulated map is demo geometry; the pink "already-cleaned" coverage is a light stylized hatch, not a real coverage grid.
- `androidTest` exercises zone selection via **Select All** (canvas-drawn room labels are not accessibility nodes, so individual polygons can't be targeted by text in a UI test). On-device tap selection of an individual polygon was verified manually instead.
