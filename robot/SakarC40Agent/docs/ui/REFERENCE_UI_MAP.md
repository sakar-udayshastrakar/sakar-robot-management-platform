# Reference UI Map

**This file is the source of truth for what the real robot's UI actually looks like and how it is
actually navigated.** Before building or changing a screen, check here for whether a reference
exists, and never guess a layout that could instead be confirmed against a real screenshot.

Status legend: `CONFIRMED` (built from an actual reference screenshot in this session),
`USER-STATED` (behavior described by the user/team as real-robot fact, no screenshot seen),
`UNCONFIRMED` (pre-existing in this codebase, no reference screenshot or statement obtained yet).

```
REAL ROBOT (as evidenced so far)

Home                                                              [CONFIRMED - reference screenshot]
 ├── Start Cleaning                                                [CONFIRMED]
 ├── Scheduled Cleaning                                            [CONFIRMED]
 ├── Teaching Mode                                                 [CONFIRMED]
 └── Manual Drive (cleaning-mode/intensity selector; vendor's       [CONFIRMED - "Hand Cleaning" reference]
     own reference screen has no movement controls here)

SERVICE ACCESS                                                     [USER-STATED: long-press SAKAR logo
Long press SAKAR ROBOTICS logo                                      opens the real Super User PIN dialog]
 └── Super User
      ├── Network                                                  [CONFIRMED - reference screenshot]
      ├── Resource Management                                      [CONFIRMED]
      ├── General Settings                                         [CONFIRMED]
      ├── Robot Debugging                                          [CONFIRMED - sidebar entry]
      │    └── Debug (full hardware dashboard: Emergency Stop/Hub  [CONFIRMED - 8 reference screenshots]
      │        Motor, IMU, Battery, brush/tank/cover status, Hub
      │        Speed, pressure sensors, water volumes, carpet
      │        detection, Component & Speed Test cards (Fan, Side
      │        Brush, Washing Brush, Clean Water Pump, Sweeping
      │        Brush), embedded joystick, Front/Rear Roller Brush
      │        sections, Workstation Settings, Light Strips,
      │        Anti-collision Bumper, Geomagnetic Detection)
      └── System Settings                                          [CONFIRMED]
           (App Guardian Service, Auto-start App, Hide System Menu
           Bar, Decrease System Volume, Data Center, Data Center
           Connection, Exit App)
```

## Reference conformance (2026-09-25)

The Super User sidebar was audited against the 5 reference screenshots and found to contain 5
extra, non-reference visible entries: Manual Drive Simulation, Logs, Robot Information, Robot
Installation, and Maps. **These visible sidebar entries have been removed** so the sidebar now
shows exactly the 5 reference-confirmed sections (Network, Resource Management, General Settings,
Robot Debugging, System Settings) - confirmed by on-device screenshot. Nothing underlying was
deleted:
- Manual Drive remains reachable from Home -> Manual Drive.
- Robot Information remains reachable from Home -> Settings -> Robot Information.
- Logs, Robot Installation, and Maps keep their routes/screens/ViewModels intact in source but are
  now ORPHANED (see `ROUTE_REGISTRY.md`/`FEATURE_REGISTRY.json`) - not navigable from anywhere in
  the UI.

See `UI_CHANGE_LEDGER.md` for the change row and `CURRENT_UI_DUPLICATION_AUDIT.md` for the
original audit that flagged this.

- The **"Super User →" text button on Home** (top-right), flagged as a non-reference-confirmed
  second access path in `CURRENT_UI_DUPLICATION_AUDIT.md` item 1, was **removed 2026-09-25**.
  Long-press-logo is now the sole entry point, matching the reference (which has no visible
  "Super User" control anywhere in its top status area).

## Reference screenshot inventory

| Screen | Confirmed via | Notes |
|---|---|---|
| Home | Session reference screenshots | 4 quick actions: Start Cleaning, Scheduled Cleaning, Teaching Mode, Manual Drive |
| Manual Drive (mode/intensity selector) | "Hand Cleaning" vendor reference | No movement controls in the vendor's own reference screen at this checkpoint - movement controls were added separately as an explained, additive UI change (see `UI_CHANGE_LEDGER.md`) |
| Super User Network | Reference screenshot | Wi-Fi toggle (real state, not togglable - Android 10+ restriction), network list with lock icon / "Connection successful" |
| Super User Resource Management | Reference screenshot | "Online Scenes" (SR Buttler/SR Pallet/SR Cleaning) + "Imported" list with checkmark |
| Super User General Settings | Reference screenshot | Language, Standard User Password, Fault Do-Not-Disturb Mode, Show Map Creation Shortcut, Rotate Screen 180°, Elevator Communication Method, Re-sweep Switch for ROS Area, ROS Dynamic Path Toggle |
| Super User Robot Debugging (sidebar entry) | Reference screenshot | 4 rows: Robot Debugging, Return to charge stress test, Wash Pressure Test, Industrial computer/ROS connection test |
| Debug (full dashboard) | 8 reference screenshots | See `RobotHardwareDebugScreen.kt` - CleanComponent-domain rows shown disabled, not faked |
| Super User System Settings | Reference screenshot | App Guardian Service, Auto-start App, Hide System Menu Bar, Decrease System Volume, Data Center, Data Center Connection, Exit App |

## Reference Screen Schema

Every reference screen tracked in `references/` (see `references/README.md`) should eventually get
a structured entry using this schema - either here or in a per-screen file this map points to. The
schema exists now so that future screens are documented consistently from the start; **existing
screens are not being retrofitted into this schema yet, and no new screen measurements are being
invented to fill it in** - it is a template for the next reference screen that gets added, not a
migration of the tree/table above.

| Field | Meaning |
|---|---|
| `screen_id` | Stable identifier for the reference screen, ideally matching a `feature_id`/`owner_screen` in `FEATURE_REGISTRY.json` where one exists. |
| `reference_image` | Path to the one authoritative screenshot for this screen under `references/screenshots/` (see "one reference screen has one authoritative reference image" in `references/README.md`). |
| `route` | The route constant (from `ROUTE_REGISTRY.md`) that reaches this screen, or `n/a` if the screen isn't reached via the Compose `NavHost`. |
| `owner` | The owner composable/screen file that implements this reference screen (matches `FEATURE_REGISTRY.md`'s `owner_screen`). |
| `allowed_elements` | The elements confirmed present in the reference image - what is allowed to exist on this screen. |
| `forbidden_extras` | Elements known to have been added beyond the reference (engineering/test additions, prior duplication) that must not appear as visible, reference-presented UI on this screen. |
| `measurement_file` | Path to the derived measurement file for this screen under `references/measurements/` (bounds, fractions, spacing, colors - see `references/README.md`). |
| `branding_exceptions` | Any reference element intentionally replaced for Sakar branding (rule 8 in `CLAUDE_UI_RULES.md`), and why. `NONE` if there are no exceptions. |
| `dynamic_runtime_elements` | Elements on the screen that show live/real runtime data rather than static reference content (e.g. connection status, clock, battery) - documented so they are never confused with a reference-vs-implementation mismatch during comparison. |

### Template

```json
{
  "screen_id": "",
  "reference_image": "references/screenshots/<file>",
  "route": "",
  "owner": "",
  "allowed_elements": [],
  "forbidden_extras": [],
  "measurement_file": "references/measurements/<file>",
  "branding_exceptions": [],
  "dynamic_runtime_elements": []
}
```

## Maintenance rule

When a new reference screenshot is obtained, add a row here **before** writing any UI code
against it, and update the tree diagram above. If a screen is built without a reference (an
engineering/test addition), say so explicitly in `FEATURE_REGISTRY.json`'s `reference_source`
field rather than leaving it blank.
